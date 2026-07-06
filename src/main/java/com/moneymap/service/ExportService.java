package com.moneymap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneymap.model.HouseholdMember;
import com.moneymap.model.User;
import com.moneymap.model.asset.FinancialGoal;
import com.moneymap.model.asset.OwnedRecord;
import com.moneymap.module.Buckets.Bucket;
import com.moneymap.module.FieldSpec;
import com.moneymap.module.ModuleDef;
import com.moneymap.module.ModuleRegistry;
import com.moneymap.module.Valuation;
import com.moneymap.repository.Db;
import com.moneymap.repository.HouseholdMemberRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Export (Section 13): JSON is the full-fidelity round-trip format; Excel and PDF are
 * one-way presentation formats. All summary figures reuse PortfolioAggregationService.
 */
@Service
public class ExportService {

    public static final String EXPORT_VERSION = "1.0";

    private final ModuleRegistry registry;
    private final Db db;
    private final HouseholdMemberRepository householdMembers;
    private final PortfolioAggregationService aggregation;
    private final ObjectMapper mapper;

    public ExportService(ModuleRegistry registry, Db db, HouseholdMemberRepository householdMembers,
                         PortfolioAggregationService aggregation, ObjectMapper mapper) {
        this.registry = registry;
        this.db = db;
        this.householdMembers = householdMembers;
        this.aggregation = aggregation;
        this.mapper = mapper;
    }

    // ── JSON (round-trip) ────────────────────────────────────────────────────

    public byte[] exportJson(User owner) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportVersion", EXPORT_VERSION);
        root.put("exportedAt", Instant.now().toString());
        root.put("ownerId", owner.getId());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("fullName", owner.getFullName());
        profile.put("email", owner.getEmail());
        profile.put("username", owner.getUsername());
        root.put("profile", profile);

        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("currencyPreference", owner.getCurrencyPreference());
        preferences.put("dateFormat", owner.getDateFormat());
        preferences.put("numberFormat", owner.getNumberFormat());
        preferences.put("theme", owner.getTheme());
        preferences.put("goldRate24kPerGram", owner.getGoldRate24kPerGram());
        preferences.put("goldRate22kPerGram", owner.getGoldRate22kPerGram());
        preferences.put("usdInrExchangeRate", owner.getUsdInrExchangeRate());
        preferences.put("emergencyDenyWindowDays", owner.getEmergencyDenyWindowDays());
        root.put("preferences", preferences);

        Map<String, Object> assets = new LinkedHashMap<>();
        for (ModuleDef<?> def : registry.all()) {
            String key = camel(def.repo instanceof com.moneymap.repository.json.JsonEntityRepository<?> jr
                    ? jr.collection() : def.path);
            assets.put(key, def.repo.findWhere(r -> owner.getId().equals(((OwnedRecord) r).getOwnerId())));
        }
        // PF employer child records are their own collection
        assets.put("pfEmployerRecords", db.pfEmployerRecords.findWhere(r -> owner.getId().equals(r.getOwnerId())));
        root.put("assets", assets);

        root.put("householdMembers", householdMembers.findByOwnerId(owner.getId()));
        root.put("netWorthSnapshots", db.netWorthSnapshots.findWhere(s -> owner.getId().equals(s.getOwnerId())));
        root.put("allocationTarget", db.allocationTargets.findWhere(t -> owner.getId().equals(t.getOwnerId()))
                .stream().findFirst().orElse(null));

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    }

    private String camel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : snake.toCharArray()) {
            if (c == '_' || c == '-') { up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }

    // ── Excel (one-way, Apache POI) ──────────────────────────────────────────

    public byte[] exportExcel(User owner) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Summary sheet first — same PortfolioAggregationService output as the dashboard
            var summary = aggregation.aggregate(owner);
            Sheet overview = wb.createSheet("Net Worth Overview");
            int r = 0;
            r = kv(overview, r, "Total Net Worth", summary.netWorth);
            r = kv(overview, r, "Total Assets", summary.totalAssets);
            r = kv(overview, r, "Total Liabilities", summary.totalLiabilities);
            r = kv(overview, r, "Cash Bucket", summary.bucket(Bucket.CASH));
            r = kv(overview, r, "Retirement Bucket", summary.bucket(Bucket.RETIREMENT));
            kv(overview, r, "Investments Bucket", summary.bucket(Bucket.INVESTMENTS));

            for (ModuleDef<?> def : registry.all()) {
                List<?> records = def.repo.findWhere(x -> owner.getId().equals(((OwnedRecord) x).getOwnerId()));
                if (records.isEmpty()) continue;   // sheet created only if the class has records (§13)
                String name = def.displayName.length() > 31 ? def.displayName.substring(0, 31) : def.displayName;
                Sheet sheet = wb.createSheet(name.replaceAll("[\\\\/*?\\[\\]:]", "-"));
                List<FieldSpec> fields = def.fields;
                Row header = sheet.createRow(0);
                int c = 0;
                for (FieldSpec f : fields) header.createCell(c++).setCellValue(f.label());
                header.createCell(c).setCellValue("Family Member");
                int rowIdx = 1;
                for (Object record : records) {
                    Row row = sheet.createRow(rowIdx++);
                    BeanWrapperImpl bw = new BeanWrapperImpl(record);
                    int col = 0;
                    for (FieldSpec f : fields) {
                        Object v = bw.getPropertyValue(f.name());
                        if (v instanceof BigDecimal bd) row.createCell(col).setCellValue(bd.doubleValue());
                        else row.createCell(col).setCellValue(v == null ? "" : String.valueOf(v));
                        col++;
                    }
                    row.createCell(col).setCellValue(String.valueOf(((OwnedRecord) record).getFamilyMemberTag()));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private int kv(Sheet sheet, int rowIdx, String key, BigDecimal value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value == null ? 0 : value.doubleValue());
        return rowIdx + 1;
    }

    // ── PDF summary (one-way, PDFBox) — fixed print style regardless of theme ──

    public byte[] exportPdf(User owner) throws IOException {
        var summary = aggregation.aggregate(owner);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            var regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 780;
                y = text(cs, bold, 22, 50, y, "MoneyMap — Net Worth Summary");
                y = text(cs, regular, 11, 50, y - 6, owner.getFullName() + " · " + java.time.LocalDate.now());
                y = text(cs, bold, 28, 50, y - 24, plain(summary.netWorth));
                y = text(cs, regular, 10, 50, y - 4, "Total Net Worth (Assets " + plain(summary.totalAssets)
                        + "  −  Liabilities " + plain(summary.totalLiabilities) + ")");

                y = text(cs, bold, 14, 50, y - 26, "Buckets");
                y = text(cs, regular, 11, 60, y - 6, "Cash: " + plain(summary.bucket(Bucket.CASH)));
                y = text(cs, regular, 11, 60, y - 2, "Retirement: " + plain(summary.bucket(Bucket.RETIREMENT)));
                y = text(cs, regular, 11, 60, y - 2, "Investments: " + plain(summary.bucket(Bucket.INVESTMENTS)));

                y = text(cs, bold, 14, 50, y - 20, "Modules");
                for (var ms : summary.moduleSummaries) {
                    if (ms.bucket() == Bucket.NONE || ms.total().signum() == 0) continue;
                    y = text(cs, regular, 10, 60, y - 2, ms.displayName() + ": " + plain(ms.total())
                            + "  (" + ms.count() + " records)");
                    if (y < 120) break;
                }

                List<FinancialGoal> goals = db.financialGoals.findWhere(g -> owner.getId().equals(g.getOwnerId()));
                if (!goals.isEmpty() && y > 160) {
                    y = text(cs, bold, 14, 50, y - 20, "Goals");
                    for (FinancialGoal g : goals) {
                        BigDecimal[] sips = Valuation.goalSips(g);
                        y = text(cs, regular, 10, 60, y - 2, g.getGoalName() + " — target " + plain(g.getTargetAmountToday())
                                + " by " + g.getTargetDate() + " · SIP " + plain(sips[0]) + " (nominal), "
                                + plain(sips[1]) + " (inflation-adjusted)");
                        if (y < 80) break;
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private float text(PDPageContentStream cs, org.apache.pdfbox.pdmodel.font.PDFont font, int size,
                       float x, float y, String content) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(content.replaceAll("[^\\x20-\\x7E]", ""));   // Helvetica-safe
        cs.endText();
        return y - size - 4;
    }

    private String plain(BigDecimal v) {
        return v == null ? "0" : "Rs " + v.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
