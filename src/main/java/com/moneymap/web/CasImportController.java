package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.model.asset.MutualFund;
import com.moneymap.repository.Db;
import com.moneymap.service.CasParserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * CAS (Consolidated Account Statement) PDF import for the Mutual Funds module. The uploaded
 * PDF and password are held only in memory for the duration of the parse call — never written
 * to disk. Parsed holdings are round-tripped through the preview form itself (editable inputs),
 * not stored server-side between preview and confirm, so there's no session state to manage or
 * leak.
 */
@Controller
@RequestMapping("/import/cas")
public class CasImportController {

    private final CasParserService casParserService;
    private final Db db;

    public CasImportController(CasParserService casParserService, Db db) {
        this.casParserService = casParserService;
        this.db = db;
    }

    private User user(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    @GetMapping
    public String uploadForm() {
        return "import/cas";
    }

    @PostMapping("/preview")
    public String preview(@RequestParam MultipartFile file,
                          @RequestParam(required = false) String password,
                          Model model, RedirectAttributes ra) {
        try {
            var rows = casParserService.parse(file.getBytes(), password);
            model.addAttribute("rows", rows);
            return "import/cas-preview";
        } catch (InvalidPasswordException e) {
            ra.addFlashAttribute("error", "That password did not open the PDF — please check it and try again.");
            return "redirect:/import/cas";
        } catch (IOException e) {
            ra.addFlashAttribute("error", "Could not read this PDF. It may be corrupted or in an unsupported format.");
            return "redirect:/import/cas";
        }
    }

    @PostMapping("/confirm")
    public String confirm(@RequestParam int rowCount, @RequestParam Map<String, String> params,
                          HttpServletRequest request, RedirectAttributes ra) {
        User owner = user(request);
        int saved = 0;
        for (int i = 0; i < rowCount; i++) {
            if (!"true".equals(params.get("include_" + i))) continue;
            String schemeName = trimToNull(params.get("schemeName_" + i));
            if (schemeName == null) continue;

            MutualFund mf = new MutualFund();
            mf.setOwnerId(owner.getId());
            mf.setFundName(schemeName);
            mf.setFolioNumber(trimToNull(params.get("folioNumber_" + i)));
            mf.setUnitsHeld(parseDecimal(params.get("units_" + i)));
            mf.setCurrentNavPerUnit(parseDecimal(params.get("nav_" + i)));
            mf.setNavAsOfDate(parseDate(params.get("asOfDate_" + i)));
            mf.setCurrency("INR");
            mf.setInvestmentType("LUMPSUM");
            db.mutualFunds.save(mf);
            saved++;
        }
        ra.addFlashAttribute("success", "Imported " + saved + " mutual fund holding(s) from your CAS statement. "
                + "Edit each one to fill in average NAV, category and AMC for accurate tracking.");
        return "redirect:/assets/mutual-funds";
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal parseDecimal(String s) {
        String t = trimToNull(s);
        if (t == null) return null;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String s) {
        String t = trimToNull(s);
        if (t == null) return null;
        try {
            return LocalDate.parse(t);
        } catch (Exception e) {
            return null;
        }
    }
}
