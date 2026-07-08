package com.moneymap.web;

import com.moneymap.model.FundMaster;
import com.moneymap.model.User;
import com.moneymap.model.asset.OwnedRecord;
import com.moneymap.module.*;
import com.moneymap.repository.HouseholdMemberRepository;
import com.moneymap.service.FundMasterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Generic CRUD for every metadata-driven asset module (Sections 05–11).
 * Always operates on the CURRENT USER'S OWN data — cross-member viewing goes through
 * the family drill-down (read-only, permission-filtered) instead.
 */
@Controller
@RequestMapping("/assets/{modulePath}")
public class AssetController {

    private final ModuleRegistry registry;
    private final AssetService assetService;
    private final HouseholdMemberRepository householdMembers;
    private final FundMasterService fundMasterService;

    public AssetController(ModuleRegistry registry, AssetService assetService,
                           HouseholdMemberRepository householdMembers, FundMasterService fundMasterService) {
        this.registry = registry;
        this.assetService = assetService;
        this.householdMembers = householdMembers;
        this.fundMasterService = fundMasterService;
    }

    private User user(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    /** Form/save/delete routes require a generic-CRUD module (custom modules override with literal mappings). */
    private ModuleDef<?> module(String path) {
        ModuleDef<?> def = registry.byPath(path);
        if (def == null || !def.genericCrud) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND);
        return def;
    }

    /** The list view is generic for every module (custom modules reuse it, overriding only their forms). */
    private ModuleDef<?> moduleAny(String path) {
        ModuleDef<?> def = registry.byPath(path);
        if (def == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND);
        return def;
    }

    @GetMapping
    public String list(@PathVariable String modulePath,
                       @RequestParam(required = false) String tag,
                       HttpServletRequest request, Model model) {
        ModuleDef<?> def = moduleAny(modulePath);
        User user = user(request);
        model.addAttribute("module", def);
        model.addAttribute("rows", assetService.rows(def, user.getId(), tag, user));
        model.addAttribute("labels", def.listColumns.stream()
                .collect(java.util.stream.Collectors.toMap(c -> c, c -> assetService.labelFor(def, c),
                        (a, b) -> a, java.util.LinkedHashMap::new)));
        model.addAttribute("household", householdMembers.findByOwnerId(user.getId()));
        model.addAttribute("tag", tag);
        return "assets/list";
    }

    @GetMapping("/new")
    public String createForm(@PathVariable String modulePath, HttpServletRequest request, Model model) {
        ModuleDef<?> def = module(modulePath);
        model.addAttribute("module", def);
        model.addAttribute("values", Map.of());
        model.addAttribute("household", householdMembers.findByOwnerId(user(request).getId()));
        return "assets/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String modulePath, @PathVariable String id,
                           HttpServletRequest request, Model model) {
        ModuleDef<?> def = module(modulePath);
        User user = user(request);
        OwnedRecord record = (OwnedRecord) def.repo.findById(id)
                .filter(r -> user.getId().equals(((OwnedRecord) r).getOwnerId()))
                .orElse(null);
        if (record == null) return "redirect:/assets/" + modulePath;
        model.addAttribute("module", def);
        model.addAttribute("recordId", id);
        model.addAttribute("values", assetService.formValues(def, record));
        model.addAttribute("household", householdMembers.findByOwnerId(user.getId()));
        return "assets/form";
    }

    @PostMapping("/save")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String save(@PathVariable String modulePath,
                       @RequestParam(required = false) String id,
                       @RequestParam Map<String, String> params,
                       HttpServletRequest request, Model model, RedirectAttributes ra) {
        ModuleDef def = module(modulePath);
        User user = user(request);
        OwnedRecord record;
        if (id != null && !id.isBlank()) {
            record = (OwnedRecord) def.repo.findById(id)
                    .filter(r -> user.getId().equals(((OwnedRecord) r).getOwnerId()))
                    .orElse(null);
            if (record == null) return "redirect:/assets/" + modulePath;
        } else {
            try {
                record = (OwnedRecord) def.type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot instantiate " + def.type, e);
            }
            record.setOwnerId(user.getId());
        }
        Map<String, String> errors = RecordBinder.bind(record, def.fields, params);
        if (!errors.isEmpty()) {
            model.addAttribute("module", def);
            model.addAttribute("recordId", id);
            model.addAttribute("errors", errors);
            model.addAttribute("values", params);
            model.addAttribute("household", householdMembers.findByOwnerId(user.getId()));
            return "assets/form";
        }
        def.repo.save(record);
        ra.addFlashAttribute("success", def.displayName + " saved.");
        return "redirect:/assets/" + modulePath;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String modulePath, @PathVariable String id,
                         HttpServletRequest request, RedirectAttributes ra) {
        ModuleDef<?> def = module(modulePath);
        User user = user(request);
        def.repo.findById(id)
                .filter(r -> user.getId().equals(((OwnedRecord) r).getOwnerId()))
                .ifPresent(r -> def.repo.deleteById(id));
        ra.addFlashAttribute("success", "Record deleted.");
        return "redirect:/assets/" + modulePath;
    }

    // ── Fund picker (Mutual Funds only) — reads the cached FundMaster list ──────

    private void requireMutualFunds(String modulePath) {
        if (!"mutual-funds".equals(modulePath)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND);
    }

    /** Local-cache typeahead — no external call, so it's safe to hit on every keystroke. */
    @GetMapping("/fund-search")
    @ResponseBody
    public List<Map<String, Object>> fundSearch(@PathVariable String modulePath,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) String category) {
        requireMutualFunds(modulePath);
        return fundMasterService.search(q, category, 20).stream()
                .map(this::fundMasterToJson)
                .toList();
    }

    /** Live single-scheme lookup — fetches (and caches) latest NAV/category for the chosen fund. */
    @GetMapping("/fund-detail/{schemeCode}")
    @ResponseBody
    public Map<String, Object> fundDetail(@PathVariable String modulePath, @PathVariable int schemeCode) {
        requireMutualFunds(modulePath);
        try {
            return fundMasterService.fetchAndCacheDetail(schemeCode)
                    .map(this::fundMasterToJson)
                    .orElseGet(() -> Map.of("error", "Could not fetch this fund's details right now."));
        } catch (IllegalStateException e) {
            return Map.of("error", "Could not reach the fund data service right now — try again in a moment.");
        }
    }

    private Map<String, Object> fundMasterToJson(FundMaster fm) {
        Map<String, Object> json = new java.util.LinkedHashMap<>();
        json.put("schemeCode", fm.getSchemeCode());
        json.put("schemeName", fm.getSchemeName() == null ? "" : fm.getSchemeName());
        json.put("fundHouse", fm.getFundHouse() == null ? "" : fm.getFundHouse());
        json.put("categoryBucket", fm.getCategoryBucket() == null ? "" : fm.getCategoryBucket());
        json.put("latestNav", fm.getLatestNav() == null ? "" : fm.getLatestNav().toPlainString());
        json.put("navAsOfDate", fm.getNavAsOfDate() == null ? "" : fm.getNavAsOfDate().toString());
        return json;
    }
}
