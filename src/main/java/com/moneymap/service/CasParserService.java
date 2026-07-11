package com.moneymap.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * IMPORTANT: the CAS text layout is not officially specified by AMFI/CAMS/KFintech. This parser
 * targets the layout confirmed against a real CAMS+KFintech consolidated statement: each scheme's
 * block runs from a "Folio No: ..." line through a final "NAV on &lt;date&gt;: INR &lt;nav&gt;
 * Market Value on &lt;date&gt;: INR &lt;value&gt;" line, then one or more lines of scheme-level
 * disclaimer/footnote text (exit-load rules, stamp-duty notices, "scheme name has been changed"
 * notices — these can contain stray decimal numbers and must NOT be parsed as holding data), and
 * finally a "Closing Unit Balance: &lt;units&gt; Total Cost Value: &lt;cost&gt;" line. Those two
 * labelled summary lines are authoritative and are used directly instead of trying to infer
 * units/NAV/value from transaction rows or from whatever numbers happen to appear in between —
 * that fragile "last N numbers seen" approach was what let a disclaimer footnote get parsed as a
 * holding in earlier versions of this parser. Real statements vary by RTA and by version, so
 * extraction accuracy is NOT guaranteed; this is why the import flow always shows an editable
 * preview and never auto-saves a parsed row without the user confirming it.
 */
@Service
public class CasParserService {

    private static final Logger log = LoggerFactory.getLogger(CasParserService.class);

    public record ParsedHolding(String folioNumber, String schemeName, BigDecimal units,
                                 BigDecimal nav, LocalDate asOfDate, BigDecimal value) {}

    private static final Pattern FOLIO_PATTERN =
            Pattern.compile("Folio\\s*(?:No\\.?|Number)?\\s*[:\\-]\\s*([0-9A-Za-z\\-]{4,})", Pattern.CASE_INSENSITIVE);
    /** Scheme header text always contains "<code>-<name...> - ISIN: <isin>" somewhere before the folio line. */
    private static final Pattern SCHEME_HEADER_PATTERN = Pattern.compile(
            "[A-Z0-9]{3,15}-(.+?)\\s*-\\s*ISIN\\s*:\\s*[A-Z0-9]{6,15}", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEMAT_TAG_PATTERN =
            Pattern.compile("\\(Non[- ]?Demat\\)$|\\(Demat\\)$", Pattern.CASE_INSENSITIVE);
    /** Authoritative end-of-block summary line: gives NAV, its as-of date, and market value directly. */
    private static final Pattern NAV_VALUE_PATTERN = Pattern.compile(
            "NAV\\s+on\\s+(\\d{1,2}-[A-Za-z]{3}-\\d{4})\\s*:\\s*INR\\s*([\\d,]+\\.?\\d*)\\s+Market\\s+Value\\s+on\\s+"
                    + "\\d{1,2}-[A-Za-z]{3}-\\d{4}\\s*:\\s*INR\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE);
    /** Authoritative closing-balance line: gives units directly. Appears after the footnote text, closing the block. */
    private static final Pattern CLOSING_BALANCE_PATTERN = Pattern.compile(
            "Closing\\s+Unit\\s+Balance\\s*:\\s*([\\d,]+\\.?\\d*)\\s+Total\\s+Cost\\s+Value", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter CAS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    /** Cap on lines scanned per scheme block before we give up on it — avoids runaway accumulation on malformed input. */
    private static final int MAX_BLOCK_LINES = 400;

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
        if (text == null || text.isBlank()) {
            log.warn("[CasParser] PDF text extraction produced no text — likely a scanned/image-based PDF, which this parser cannot read (no OCR).");
            return List.of();
        }
        List<ParsedHolding> holdings = parseText(text);
        if (holdings.isEmpty()) {
            // Structural diagnostics only — never log statement content (folio numbers, amounts, names).
            long nonBlankLines = text.lines().filter(l -> !l.isBlank()).count();
            long folioMatches = FOLIO_PATTERN.matcher(text).results().count();
            log.warn("[CasParser] No holdings detected. text_length={} non_blank_lines={} folio_header_matches={} — "
                            + "either every scheme in this statement has a zero closing balance, or the statement's "
                            + "layout differs from the patterns this parser recognizes.",
                    text.length(), nonBlankLines, folioMatches);
        }
        return holdings;
    }

    /** Exposed separately so the parsing logic can be exercised without a real PDF (testing). */
    public List<ParsedHolding> parseText(String text) {
        List<ParsedHolding> holdings = new ArrayList<>();

        StringBuilder headerBuffer = new StringBuilder();
        String currentFolio = null;
        String schemeName = null;
        LocalDate asOfDate = null;
        BigDecimal nav = null;
        BigDecimal marketValue = null;
        boolean sawNavLine = false;
        int blockLines = 0;

        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || isPageNoiseLine(line)) continue;

            Matcher folioMatcher = FOLIO_PATTERN.matcher(line);
            if (folioMatcher.find()) {
                currentFolio = folioMatcher.group(1);
                schemeName = extractSchemeName(headerBuffer.toString());
                headerBuffer.setLength(0);
                asOfDate = null;
                nav = null;
                marketValue = null;
                sawNavLine = false;
                blockLines = 0;
                continue;
            }

            if (currentFolio == null) {
                // Preamble, AMC name, PAN/KYC line, or the multi-line scheme-code/ISIN header —
                // all accumulated so extractSchemeName can find the header pattern once the
                // Folio line arrives, regardless of how PDFBox happened to break it across lines.
                headerBuffer.append(' ').append(line);
                continue;
            }

            if (++blockLines > MAX_BLOCK_LINES) {
                // Block ran on too long without a closing marker — abandon it rather than mis-attribute numbers.
                currentFolio = null;
                schemeName = null;
                continue;
            }

            if (!sawNavLine) {
                Matcher navMatcher = NAV_VALUE_PATTERN.matcher(line);
                if (navMatcher.find()) {
                    asOfDate = parseCasDate(navMatcher.group(1));
                    nav = parseCasNumber(navMatcher.group(2));
                    marketValue = parseCasNumber(navMatcher.group(3));
                    sawNavLine = true;
                }
                continue; // transaction rows before the NAV/value line aren't needed — units come from Closing Balance
            }

            // Between the NAV line and Closing Balance line sits scheme-level disclaimer/footnote
            // text (exit-load rules, stamp-duty notices, "scheme name has been changed" notices).
            // It is deliberately never scanned for numbers — that was the source of the earlier bug.
            Matcher closingMatcher = CLOSING_BALANCE_PATTERN.matcher(line);
            if (closingMatcher.find()) {
                BigDecimal units = parseCasNumber(closingMatcher.group(1));
                if (schemeName != null && units != null && units.signum() > 0 && nav != null && marketValue != null) {
                    holdings.add(new ParsedHolding(currentFolio, schemeName, units, nav, asOfDate, marketValue));
                }
                currentFolio = null;
                schemeName = null;
                headerBuffer.setLength(0);
            }
        }
        return holdings;
    }

    /**
     * Finds the last scheme-header match in the accumulated pre-folio text (the one nearest the
     * Folio line) and strips the trailing "(Non Demat)"/"(Demat)" tag, keeping everything else —
     * including "(formerly ...)" scheme-rename notes, which are genuinely part of the name.
     */
    private static String extractSchemeName(String headerText) {
        Matcher m = SCHEME_HEADER_PATTERN.matcher(headerText);
        String lastMatch = null;
        while (m.find()) {
            lastMatch = m.group(1).strip();
        }
        if (lastMatch == null) return null;
        return DEMAT_TAG_PATTERN.matcher(lastMatch).replaceAll("").strip();
    }

    private static BigDecimal parseCasNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseCasDate(String raw) {
        try {
            return LocalDate.parse(raw, CAS_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Per-page repeating boilerplate (vertical watermark, running headers) — never holding data. */
    private static boolean isPageNoiseLine(String line) {
        return line.equalsIgnoreCase("Consolidated Account Statement")
                || line.matches("(?i)Page \\d+ of \\d+")
                || line.matches("\\d{2}-[A-Za-z]{3}-\\d{4} To \\d{2}-[A-Za-z]{3}-\\d{4}")
                || line.matches("(?i)Date Amount ?Price ?Units ?Transaction.*Unit")
                || line.matches("(?i)\\(INR\\) ?\\(INR\\) ?Balance")
                || line.length() <= 3;
    }
}
