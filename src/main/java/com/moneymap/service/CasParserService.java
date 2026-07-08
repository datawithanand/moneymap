package com.moneymap.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort parser for CAMS/KFintech Consolidated Account Statement (CAS) PDFs.
 *
 * IMPORTANT: the CAS text layout is not officially specified by AMFI/CAMS/KFintech — this
 * parser targets the commonly-observed layout (folio header lines, per-scheme name lines,
 * a "Closing ... Balance" line carrying units/NAV/value). Real statements vary by RTA and by
 * version, so extraction accuracy is NOT guaranteed. This is why the import flow always shows
 * an editable preview and never auto-saves a parsed row without the user confirming it.
 */
@Service
public class CasParserService {

    public record ParsedHolding(String folioNumber, String schemeName, BigDecimal units,
                                 BigDecimal nav, LocalDate asOfDate, BigDecimal value) {}

    private static final Pattern FOLIO_PATTERN =
            Pattern.compile("Folio\\s*(?:No\\.?|Number)?\\s*[:\\-]?\\s*([0-9A-Za-z/\\-]{4,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCHEME_HINT_PATTERN =
            Pattern.compile(".*(Fund).*(Growth|Dividend|IDCW|Plan|Direct|Regular).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOSING_BALANCE_HINT_PATTERN =
            Pattern.compile("Closing.*Balance|Total\\s+Units|Unit\\s+Balance", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECIMAL_NUMBER_PATTERN = Pattern.compile("-?[\\d,]+\\.\\d{2,4}");
    private static final Pattern CAS_DATE_PATTERN = Pattern.compile("(\\d{1,2}-[A-Za-z]{3}-\\d{4})");
    private static final DateTimeFormatter CAS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    /**
     * Decrypts (if needed) and extracts text from the given CAS PDF, then parses it into a
     * best-effort list of holdings. Never writes the PDF or password to disk.
     *
     * @throws InvalidPasswordException if the supplied password does not open the PDF
     * @throws IOException if the PDF cannot be read/parsed at all
     */
    public List<ParsedHolding> parse(byte[] pdfBytes, String password) throws IOException {
        String text;
        try (PDDocument doc = Loader.loadPDF(pdfBytes, password == null ? "" : password)) {
            PDFTextStripper stripper = new PDFTextStripper();
            text = stripper.getText(doc);
        }
        return parseText(text);
    }

    /** Exposed separately so the parsing logic can be exercised without a real PDF (testing). */
    public List<ParsedHolding> parseText(String text) {
        List<ParsedHolding> holdings = new ArrayList<>();
        String currentFolio = null;
        String currentScheme = null;

        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;

            Matcher folioMatcher = FOLIO_PATTERN.matcher(line);
            if (folioMatcher.find()) {
                currentFolio = folioMatcher.group(1);
                currentScheme = null;
                continue;
            }

            if (currentFolio == null) continue;   // ignore everything before the first folio header

            if (SCHEME_HINT_PATTERN.matcher(line).matches() && countDecimalNumbers(line) == 0) {
                currentScheme = line;
                continue;
            }

            boolean looksLikeClosingBalance = CLOSING_BALANCE_HINT_PATTERN.matcher(line).find();
            List<BigDecimal> numbers = extractDecimalNumbers(line);
            if (looksLikeClosingBalance && numbers.size() >= 3 && currentScheme != null) {
                int n = numbers.size();
                BigDecimal units = numbers.get(n - 3);
                BigDecimal nav = numbers.get(n - 2);
                BigDecimal value = numbers.get(n - 1);
                LocalDate asOfDate = extractCasDate(line);
                holdings.add(new ParsedHolding(currentFolio, currentScheme, units, nav, asOfDate, value));
                currentScheme = null;
            }
        }
        return holdings;
    }

    private static int countDecimalNumbers(String line) {
        Matcher m = DECIMAL_NUMBER_PATTERN.matcher(line);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private static List<BigDecimal> extractDecimalNumbers(String line) {
        List<BigDecimal> numbers = new ArrayList<>();
        Matcher m = DECIMAL_NUMBER_PATTERN.matcher(line);
        while (m.find()) {
            String cleaned = m.group().replace(",", "");
            try {
                numbers.add(new BigDecimal(cleaned));
            } catch (NumberFormatException ignored) {
                // skip malformed matches rather than failing the whole line
            }
        }
        return numbers;
    }

    private static LocalDate extractCasDate(String line) {
        Matcher m = CAS_DATE_PATTERN.matcher(line);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(m.group(1), CAS_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
