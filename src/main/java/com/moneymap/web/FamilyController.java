package com.moneymap.web;

import com.moneymap.model.*;
import com.moneymap.model.asset.OwnedRecord;
import com.moneymap.module.ModuleDef;
import com.moneymap.module.ModuleRegistry;
import com.moneymap.repository.Db;
import com.moneymap.repository.UserRepository;
import com.moneymap.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Family group, sharing matrix, consolidated dashboard, contacts directory, drill-down (Section 03/04). */
@Controller
@RequestMapping("/family")
public class FamilyController {

    private final Db db;
    private final UserRepository users;
    private final FamilyService familyService;
    private final PermissionService permissions;
    private final VaultService vault;
    private final PortfolioAggregationService aggregation;
    private final ModuleRegistry registry;
    private final com.moneymap.module.AssetService assetService;

    public FamilyController(Db db, UserRepository users, FamilyService familyService,
                            PermissionService permissions, VaultService vault,
                            PortfolioAggregationService aggregation, ModuleRegistry registry,
                            com.moneymap.module.AssetService assetService) {
        this.db = db;
        this.users = users;
        this.familyService = familyService;
        this.permissions = permissions;
        this.vault = vault;
        this.aggregation = aggregation;
        this.registry = registry;
        this.assetService = assetService;
    }

    private User user(HttpServletRequest r) { return (User) r.getAttribute("currentUser"); }

    private static final List<String> RELATIONSHIP_OPTIONS = List.of(
            "Self", "Spouse", "Father", "Mother", "Son", "Daughter", "Brother", "Sister", "Guardian", "Other");

    // ── Family home ──────────────────────────────────────────────────────────

    @GetMapping
    public String home(HttpServletRequest request, Model model) {
        User user = user(request);
        model.addAttribute("myInvitations", familyService.pendingInvitationsFor(user));
        model.addAttribute("relationshipOptions", RELATIONSHIP_OPTIONS);
        if (user.getFamilyGroupId() == null) {
            return "family/create";
        }
        String groupId = user.getFamilyGroupId();
        FamilyGroup group = db.familyGroups.findById(groupId).orElse(null);
        List<User> members = familyService.activeMembers(groupId);
        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("isFamilyAdmin", group != null && user.getId().equals(group.getAdminUserId()));
        model.addAttribute("pendingInvitations", familyService.pendingInvitationsOfGroup(groupId));
        // Vault panel: requests involving me
        model.addAttribute("requestsForMe", db.emergencyRequests.findWhere(r ->
                user.getId().equals(r.getOwnerId()) && r.getStatus() == EmergencyAccessRequest.Status.PENDING));
        model.addAttribute("myGrants", db.emergencyRequests.findWhere(r ->
                user.getId().equals(r.getOwnerId()) && r.isActiveGrant(Instant.now())));
        model.addAttribute("usersById", usersById(members));
        // Per-member vault context for the current viewer (§3.1/§3.2)
        Map<String, Map<String, Object>> vaultInfo = new HashMap<>();
        for (User member : members) {
            if (member.getId().equals(user.getId())) continue;
            Map<String, Object> info = new HashMap<>();
            info.put("designated", permissions.permissionRow(member.getId(), user.getId())
                    .map(FamilyPermission::isDesignatedEmergencyContact).orElse(false));
            vault.pendingBetween(member.getId(), user.getId())
                    .ifPresent(p -> info.put("pending", p));
            permissions.activeGrant(member.getId(), user.getId())
                    .ifPresent(g -> info.put("grant", g));
            vaultInfo.put(member.getId(), info);
        }
        model.addAttribute("vaultInfo", vaultInfo);

        // Family design handoff (Family.dc.html): consolidated net worth hero, member card grid
        // with real permission-respecting visible net worth, and a None/Summary/Detailed/Full
        // permission matrix. String[] familyColors mirrors the dashboard's family-snapshot palette.
        String[] avatarColors = {"#0e7490", "#7c4dea", "#1fa15c", "#d6900a", "#e0405e", "#3b6fe0"};
        BigDecimal consolidatedTotal = aggregation.aggregate(user).netWorth;
        List<Map<String, Object>> memberCards = new ArrayList<>();
        int idx = 0;
        for (User m : members) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", m.getId());
            String name = m.getFullName();
            card.put("name", name);
            card.put("initial", name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
            card.put("role", m.getId().equals(user.getId()) ? "You"
                    : (m.getFamilyRelationship() == null || m.getFamilyRelationship().isBlank() ? "Member" : m.getFamilyRelationship()));
            card.put("avatarBg", avatarColors[idx % avatarColors.length]);
            if (m.getId().equals(user.getId())) {
                card.put("netWorth", consolidatedTotal);
                card.put("permission", "You");
                card.put("permBg", "var(--badge-blue-bg)");
                card.put("permColor", "var(--badge-blue-fg)");
            } else {
                FamilyPermission.Level level = permissions.effectiveLevel(m.getId(), user.getId());
                boolean visible = level == FamilyPermission.Level.SUMMARY_ONLY || level == FamilyPermission.Level.FULL_ACCESS;
                card.put("netWorth", visible ? aggregation.aggregate(m).netWorth : null);
                card.put("permission", switch (level) {
                    case FULL_ACCESS -> "Full access";
                    case CONTACTS_ONLY -> "Detailed";
                    case SUMMARY_ONLY -> "Summary only";
                    case NO_ACCESS -> "Not shared";
                });
                boolean good = level == FamilyPermission.Level.FULL_ACCESS || level == FamilyPermission.Level.SUMMARY_ONLY;
                card.put("permBg", good ? "var(--badge-green-bg)" : "var(--warning-bg)");
                card.put("permColor", good ? "var(--badge-green-fg)" : "var(--accent)");
                if (visible) consolidatedTotal = consolidatedTotal.add((BigDecimal) card.get("netWorth"));
            }
            memberCards.add(card);
            idx++;
        }
        model.addAttribute("memberCards", memberCards);
        model.addAttribute("consolidatedTotal", consolidatedTotal);

        // Permission matrix: for each other member, what THEY have shared with ME, across the
        // 4 real access levels (None/Summary/Detailed/Full).
        List<Map<String, Object>> permMatrix = new ArrayList<>();
        for (User m : members) {
            if (m.getId().equals(user.getId())) continue;
            FamilyPermission.Level level = permissions.effectiveLevel(m.getId(), user.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", m.getFullName());
            row.put("none", level == FamilyPermission.Level.NO_ACCESS);
            row.put("summary", level == FamilyPermission.Level.SUMMARY_ONLY);
            row.put("detailed", level == FamilyPermission.Level.CONTACTS_ONLY);
            row.put("full", level == FamilyPermission.Level.FULL_ACCESS);
            permMatrix.add(row);
        }
        model.addAttribute("permMatrix", permMatrix);

        return "family/home";
    }

    private Map<String, User> usersById(List<User> members) {
        Map<String, User> map = new HashMap<>();
        users.findAll().forEach(u -> map.put(u.getId(), u));
        return map;
    }

    @PostMapping("/create")
    public String create(@RequestParam String groupName, HttpServletRequest request, RedirectAttributes ra) {
        String error = familyService.createGroup(user(request), groupName);
        if (error != null) ra.addFlashAttribute("error", error);
        return "redirect:/family";
    }

    @PostMapping("/invite")
    public String invite(@RequestParam String identifier, @RequestParam(required = false) String relationship,
                         HttpServletRequest request, RedirectAttributes ra) {
        var result = familyService.invite(user(request), identifier, relationship);
        if (result.error() != null) {
            ra.addFlashAttribute("error", result.error());
        } else if (!result.emailAttempted()) {
            ra.addFlashAttribute("success", "Invitation created (expires in 14 days). "
                    + "No email was sent — SMTP is not configured on this instance (Admin > Settings). "
                    + "If they already have a MoneyMap account, they'll see it waiting under Family when they log in.");
        } else if (result.emailSent()) {
            ra.addFlashAttribute("success", "Invitation sent by email. It expires in 14 days.");
        } else {
            ra.addFlashAttribute("success", "Invitation created (expires in 14 days), but the email could not be "
                    + "delivered — check the SMTP settings under Admin > Settings. "
                    + "If they already have a MoneyMap account, they'll see it waiting under Family when they log in.");
        }
        return "redirect:/family";
    }

    @PostMapping("/members/{id}/relationship")
    public String editRelationship(@PathVariable String id, @RequestParam String relationship,
                                   HttpServletRequest request, RedirectAttributes ra) {
        String error = familyService.editRelationship(user(request), id, relationship);
        if (error != null) ra.addFlashAttribute("error", error);
        return "redirect:/family";
    }

    @PostMapping("/invitations/{id}/accept")
    public String accept(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        String error = familyService.accept(user(request), id);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Welcome to the family group. Nothing is shared automatically — configure sharing when you're ready." : error);
        return "redirect:/family";
    }

    @PostMapping("/invitations/{id}/decline")
    public String decline(@PathVariable String id, HttpServletRequest request) {
        familyService.decline(user(request), id);
        return "redirect:/family";
    }

    @PostMapping("/invitations/{id}/cancel")
    public String cancel(@PathVariable String id, HttpServletRequest request) {
        familyService.cancelInvitation(user(request), id);
        return "redirect:/family";
    }

    @PostMapping("/leave")
    public String leave(HttpServletRequest request, RedirectAttributes ra) {
        String error = familyService.leave(user(request));
        if (error != null) ra.addFlashAttribute("error", error);
        return "redirect:/family";
    }

    @PostMapping("/members/{id}/remove")
    public String remove(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        String error = familyService.removeMember(user(request), id);
        if (error != null) ra.addFlashAttribute("error", error);
        return "redirect:/family";
    }

    @PostMapping("/dissolve")
    public String dissolve(@RequestParam String confirmName, HttpServletRequest request, RedirectAttributes ra) {
        String error = familyService.dissolve(user(request), confirmName);
        if (error != null) ra.addFlashAttribute("error", error);
        return "redirect:/family";
    }

    @PostMapping("/transfer-admin")
    public String transferAdmin(@RequestParam String newAdminUserId, HttpServletRequest request, RedirectAttributes ra) {
        String error = familyService.transferAdmin(user(request), newAdminUserId);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Family Admin role transferred." : error);
        return "redirect:/family";
    }

    // ── §2 Manage Sharing ────────────────────────────────────────────────────

    @GetMapping("/sharing")
    public String sharing(HttpServletRequest request, Model model) {
        User user = user(request);
        if (user.getFamilyGroupId() == null) return "redirect:/family";
        List<User> others = familyService.activeMembers(user.getFamilyGroupId()).stream()
                .filter(m -> !m.getId().equals(user.getId())).toList();
        Map<String, FamilyPermission> rows = new HashMap<>();
        for (User other : others) {
            permissions.permissionRow(user.getId(), other.getId()).ifPresent(p -> rows.put(other.getId(), p));
        }
        model.addAttribute("others", others);
        model.addAttribute("rows", rows);
        model.addAttribute("denyWindowDays", user.getEmergencyDenyWindowDays());
        return "family/sharing";
    }

    @PostMapping("/sharing/save")
    public String saveSharing(@RequestParam String viewerUserId,
                              @RequestParam FamilyPermission.Level standingLevel,
                              @RequestParam(required = false) String designated,
                              @RequestParam(required = false) FamilyPermission.Level emergencyLevel,
                              HttpServletRequest request, RedirectAttributes ra) {
        String error = familyService.updatePermission(user(request), viewerUserId, standingLevel,
                designated != null, emergencyLevel);
        ra.addFlashAttribute(error == null ? "success" : "error", error == null ? "Saved." : error);
        return "redirect:/family/sharing";
    }

    @PostMapping("/sharing/deny-window")
    public String denyWindow(@RequestParam int days, HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        if (days < 1 || days > 30) {
            ra.addFlashAttribute("error", "Deny window must be 1–30 days.");
        } else {
            user.setEmergencyDenyWindowDays(days);
            users.save(user);
            ra.addFlashAttribute("success", "Deny window updated — applies to requests filed from now on.");
        }
        return "redirect:/family/sharing";
    }

    // ── §4 Consolidated dashboard ────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        User viewer = user(request);
        if (viewer.getFamilyGroupId() == null) return "redirect:/family";
        List<User> others = familyService.activeMembers(viewer.getFamilyGroupId()).stream()
                .filter(m -> !m.getId().equals(viewer.getId())).toList();

        List<Map<String, Object>> cards = new ArrayList<>();
        List<Map<String, Object>> contactMembers = new ArrayList<>();
        BigDecimal familyTotal = BigDecimal.ZERO;

        for (User owner : others) {
            FamilyPermission.Level level = permissions.effectiveLevel(owner.getId(), viewer.getId());
            if (level == FamilyPermission.Level.NO_ACCESS) continue;   // simply absent (§2.2)
            if (level == FamilyPermission.Level.SUMMARY_ONLY || level == FamilyPermission.Level.FULL_ACCESS) {
                BigDecimal netWorth = aggregation.aggregate(owner).netWorth;
                familyTotal = familyTotal.add(netWorth);
                cards.add(Map.of("owner", owner, "netWorth", netWorth,
                        "fullAccess", level == FamilyPermission.Level.FULL_ACCESS));
            }
            if (level == FamilyPermission.Level.CONTACTS_ONLY || level == FamilyPermission.Level.FULL_ACCESS) {
                contactMembers.add(Map.of("owner", owner, "contacts", contactsDirectory(owner)));
            }
        }
        model.addAttribute("cards", cards);
        model.addAttribute("familyTotal", familyTotal);
        model.addAttribute("contactMembers", contactMembers);
        return "family/dashboard";
    }

    /**
     * Contacts Only view (§4.3/§6.2): per asset, grouped by module — nominee, customer care,
     * portal, branch address. Numeric fields are OMITTED entirely, never masked.
     */
    private List<Map<String, Object>> contactsDirectory(User owner) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModuleDef<?> def : registry.all()) {
            for (Object record : def.repo.findWhere(r -> owner.getId().equals(((OwnedRecord) r).getOwnerId()))) {
                OwnedRecord o = (OwnedRecord) record;
                if (isBlank(o.getNominee()) && isBlank(o.getCustomerCareNumber())
                        && isBlank(o.getCustomerCareEmail()) && isBlank(o.getPortalUrl())
                        && isBlank(o.getBranchAddress())) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("module", def.displayName);
                row.put("nominee", o.getNominee());
                row.put("customerCareNumber", o.getCustomerCareNumber());
                row.put("customerCareEmail", o.getCustomerCareEmail());
                row.put("portalUrl", o.getPortalUrl());
                row.put("branchAddress", o.getBranchAddress());
                out.add(row);
            }
        }
        return out;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    // ── §4.4 Drill-down (Full Access only, read-only) ───────────────────────

    @GetMapping("/view/{ownerId}")
    public String drillDown(@PathVariable String ownerId, HttpServletRequest request, Model model) {
        User viewer = user(request);
        User owner = users.findById(ownerId).orElse(null);
        if (owner == null) return "redirect:/family/dashboard";
        FamilyPermission.Level level = permissions.effectiveLevel(ownerId, viewer.getId());
        if (level != FamilyPermission.Level.FULL_ACCESS) {
            // The boundary between levels is hard — no partial drill-down (§4.4)
            return "redirect:/family/dashboard";
        }
        var summary = aggregation.aggregate(owner);
        List<Map<String, Object>> moduleTables = new ArrayList<>();
        for (ModuleDef<?> def : registry.all()) {
            var rows = assetService.rows(def, ownerId, null, owner);
            if (rows.isEmpty()) continue;
            Map<String, String> labels = new LinkedHashMap<>();
            for (String c : def.listColumns) labels.put(c, assetService.labelFor(def, c));
            moduleTables.add(Map.of("module", def, "rows", rows, "labels", labels));
        }
        model.addAttribute("owner", owner);
        model.addAttribute("summary", summary);
        model.addAttribute("moduleTables", moduleTables);
        return "family/drill-down";
    }

    // ── §3 Vault actions ─────────────────────────────────────────────────────

    @PostMapping("/vault/request")
    public String requestAccess(@RequestParam String ownerId, @RequestParam String reason,
                                HttpServletRequest request, RedirectAttributes ra) {
        String error = vault.request(user(request), ownerId, reason);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Emergency access request sent. The owner has been notified." : error);
        return "redirect:/family";
    }

    @PostMapping("/vault/{id}/approve")
    public String approve(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        String error = vault.approve(user(request), id);
        if (error != null) ra.addFlashAttribute("error", error);
        return "redirect:/family";
    }

    @PostMapping("/vault/{id}/deny")
    public String deny(@PathVariable String id, @RequestParam(required = false) String note,
                       HttpServletRequest request, RedirectAttributes ra) {
        String error = vault.deny(user(request), id, note);
        if (error != null) ra.addFlashAttribute("error", error);
        return "redirect:/family";
    }

    @PostMapping("/vault/{id}/revoke")
    public String revoke(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        String error = vault.revoke(user(request), id);
        if (error != null) ra.addFlashAttribute("error", error);
        return "redirect:/family";
    }
}
