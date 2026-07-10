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
 * IMPORTANT: the CAS text layout is not officially specified by AMFI/CAMS/KFintech — this
 * parser targets the commonly-observed layout (folio header lines, per-scheme name lines,
 * a "Closing ... Balance" line/block carrying units/NAV/value). Real statements vary by RTA
 * and by version, so extraction accuracy is NOT guaranteed. This is why the import flow always
 * shows an editable preview and never auto-saves a parsed row without the user confirming it.
 *
 * Parsing strategy (block-based, not single-line): PDFBox's PDFTextStripper extracts text in
 * reading order, but table-formatted PDFs frequently split what looks like one logical row
 * (label, units, NAV, value) across several physical lines once column alignment doesn't match
 * reading order. Rather than requiring the label and all three numbers on one exact line, this
 * parser accumulates every decimal number seen since the current scheme name was recognized and,
 * once a "closing balance" cue line appears (or the scheme/folio block ends), takes the last
 * three numbers seen as units/NAV/value. This tolerates both single-line and multi-line-per-field
 * layouts without depending on exact line-break behavior.
 */
@Service
public class CasParserService {

    private static final Logger log = LoggerFactory.getLogger(CasParserService.class);

    public record ParsedHolding(String folioNumber, String schemeName, BigDecimal units,
                                 BigDecimal nav, LocalDate asOfDate, BigDecimal value) {}

    private static final Pattern FOLIO_PATTERN =
            Pattern.compile("Folio\\s*(?:No\\.?|Number)?\\s*[:\\-]?\\s*([0-9A-Za-z/\\-]{4,})", Pattern.CASE_INSENSITIVE);
    /** Recognizable scheme-name line: contains a fund/scheme keyword, or a plan/option keyword. */
    private static final Pattern SCHEME_HINT_PATTERN = Pattern.compile(
            "(Fund|Scheme)|((Growth|Dividend|IDCW|Direct|Regular)\\s+(Plan|Option))", Pattern.CASE_INSENSITIVE);
    /** Lines that look like a scheme name but are actually boilerplate/table headers — reject these. */
    private static final Pattern SCHEME_REJECT_PATTERN = Pattern.compile(
            "^(Date|Transaction|Description|Particulars|Opening|Closing|Total|Page|Statement|Summary|"
                    + "Registrar|ISIN|PAN|KYC|Nominee|Address|Mobile|Email|Value\\s+of\\s+Investment|"
                    + "Portfolio|Consolidated|Disclaimer|Note|Contact)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOSING_BALANCE_HINT_PATTERN =
            Pattern.compile("Closing.*Balance|Total\\s+Units|Unit\\s+Balance|Value\\s*[:\\-]", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECIMAL_NUMBER_PATTERN = Pattern.compile("-?[\\d,]+\\.\\d{2,4}");
    private static final Pattern CAS_DATE_PATTERN = Pattern.compile("(\\d{1,2}-[A-Za-z]{3}-\\d{4})");
    private static final DateTimeFormatter CAS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    /** Cap on lines scanned per scheme block before we give up on it — avoids runaway accumulation. */
    private static final int MAX_BLOCK_LINES = 60;

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
                            + "the statement's layout likely differs from the patterns this parser recognizes.",
                    text.length(), nonBlankLines, folioMatches);
        }
        return holdings;
    }

    /** Exposed separately so the parsing logic can be exercised without a real PDF (testing). */
    public List<ParsedHolding> parseText(String text) {
        List<ParsedHolding> holdings = new ArrayList<>();
        String currentFolio = null;
        String currentScheme = null;
        List<BigDecimal> blockNumbers = new ArrayList<>();
        LocalDate blockDate = null;
        int blockLines = 0;

        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;

            Matcher folioMatcher = FOLIO_PATTERN.matcher(line);
            if (folioMatcher.find()) {
                finalizeScheme(holdings, currentFolio, currentScheme, blockNumbers, blockDate);
                currentFolio = folioMatcher.group(1);
                currentScheme = null;
                blockNumbers = new ArrayList<>();
                blockDate = null;
                blockLines = 0;
                continue;
            }

            if (currentFolio == null) continue;   // ignore everything before the first folio header

            boolean isSchemeLine = SCHEME_HINT_PATTERN.matcher(line).find()
                    && !SCHEME_REJECT_PATTERN.matcher(line).find()
                    && countDecimalNumbers(line) <= 1;
            if (isSchemeLine) {
                // A new scheme name line ends the previous scheme's block.
                finalizeScheme(holdings, currentFolio, currentScheme, blockNumbers, blockDate);
                currentScheme = line;
                blockNumbers = new ArrayList<>();
                blockDate = null;
                blockLines = 0;
                continue;
            }

            if (currentScheme == null) continue;   // no scheme context yet — skip preamble lines

            if (++blockLines > MAX_BLOCK_LINES) {
                // Block ran on too long without a closing marker — abandon it rather than mis-attribute numbers.
                currentScheme = null;
                blockNumbers = new ArrayList<>();
                blockDate = null;
                continue;
            }

            blockNumbers.addAll(extractDecimalNumbers(line));
            LocalDate lineDate = extractCasDate(line);
            if (lineDate != null) blockDate = lineDate;

            boolean looksLikeClosingBalance = CLOSING_BALANCE_HINT_PATTERN.matcher(line).find();
            if (looksLikeClosingBalance && blockNumbers.size() >= 3) {
                finalizeScheme(holdings, currentFolio, currentScheme, blockNumbers, blockDate);
                currentScheme = null;
                blockNumbers = new ArrayList<>();
                blockDate = null;
                blockLines = 0;
            }
        }
        finalizeScheme(holdings, currentFolio, currentScheme, blockNumbers, blockDate);
        return holdings;
    }

    /** Finalizes the current scheme's block into a ParsedHolding if it has enough data, using the last 3 numbers seen. */
    private static void finalizeScheme(List<ParsedHolding> holdings, String folio, String scheme,
                                        List<BigDecimal> numbers, LocalDate asOfDate) {
        if (folio == null || scheme == null || numbers.size() < 3) return;
        int n = numbers.size();
        BigDecimal units = numbers.get(n - 3);
        BigDecimal nav = numbers.get(n - 2);
        BigDecimal value = numbers.get(n - 1);
        holdings.add(new ParsedHolding(folio, scheme, units, nav, asOfDate, value));
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
