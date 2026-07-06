package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.service.ExportService;
import com.moneymap.service.ImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;

/** Export & Import (Section 13). */
@Controller
public class ExportController {

    private final ExportService exportService;
    private final ImportService importService;
    private final com.moneymap.repository.UserRepository users;
    private final BCryptPasswordEncoder encoder;

    public ExportController(ExportService exportService, ImportService importService,
                            com.moneymap.repository.UserRepository users, BCryptPasswordEncoder encoder) {
        this.exportService = exportService;
        this.importService = importService;
        this.users = users;
        this.encoder = encoder;
    }

    private User user(HttpServletRequest r) { return (User) r.getAttribute("currentUser"); }

    @GetMapping("/export")
    public String page() {
        return "export";
    }

    @GetMapping("/export/json")
    public ResponseEntity<byte[]> json(HttpServletRequest request) throws IOException {
        byte[] body = exportService.exportJson(user(request));
        return download(body, "moneymap-export-" + LocalDate.now() + ".json", MediaType.APPLICATION_JSON);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> excel(HttpServletRequest request) throws IOException {
        byte[] body = exportService.exportExcel(user(request));
        return download(body, "moneymap-export-" + LocalDate.now() + ".xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> pdf(HttpServletRequest request) throws IOException {
        byte[] body = exportService.exportPdf(user(request));
        return download(body, "moneymap-summary-" + LocalDate.now() + ".pdf", MediaType.APPLICATION_PDF);
    }

    private ResponseEntity<byte[]> download(byte[] body, String filename, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(type)
                .body(body);
    }

    @PostMapping("/import/json")
    public String importJson(@RequestParam MultipartFile file,
                             @RequestParam String mode,
                             @RequestParam(required = false) String confirmUsername,
                             @RequestParam(required = false) String currentPassword,
                             HttpServletRequest request, Model model, RedirectAttributes ra) {
        User user = user(request);
        boolean replace = "REPLACE".equals(mode);
        if (replace) {
            // Replace deletes the entire existing dataset — type-to-confirm + password (§13)
            if (!user.getUsername().equalsIgnoreCase(confirmUsername == null ? "" : confirmUsername.trim())
                    || !encoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
                ra.addFlashAttribute("error", "Replace mode requires your username and password to confirm.");
                return "redirect:/export";
            }
        }
        try {
            var report = importService.importJson(user, file.getBytes(), replace);
            model.addAttribute("report", report);
            return "import-report";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());   // validation failure — zero records written
            return "redirect:/export";
        } catch (IOException e) {
            ra.addFlashAttribute("error", "Could not read the uploaded file.");
            return "redirect:/export";
        }
    }
}
