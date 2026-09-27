package com.sione.ztetype6;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Small XML helpers for the in-app editor.
 *
 * These functions deliberately preserve the user's original XML text instead of
 * re-serializing a DOM tree, because ZTE config files can be sensitive to layout
 * and encoding details. DOM parsing is used only for well-formedness validation.
 */
public final class XmlConfigTools {
    private XmlConfigTools() {}

    public static final class SearchResult {
        private final int start;
        private final int end;
        private final int ordinal;
        private final int total;

        public SearchResult(int start, int end, int ordinal, int total) {
            this.start = start;
            this.end = end;
            this.ordinal = ordinal;
            this.total = total;
        }

        public int start() { return start; }
        public int end() { return end; }
        public int ordinal() { return ordinal; }
        public int total() { return total; }
    }

    public static void validateWellFormed(String xml) throws Exception {
        if (xml == null || xml.trim().isEmpty()) {
            throw new IllegalArgumentException("XML kosong.");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try { factory.setXIncludeAware(false); } catch (Exception ignored) {}
        try { factory.setExpandEntityReferences(false); } catch (Exception ignored) {}
        setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureQuietly(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureQuietly(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureQuietly(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        } catch (IllegalArgumentException ignored) {
            // Some Android XML providers may not implement these JAXP attributes.
        }

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        if (doc.getDocumentElement() == null) {
            throw new IllegalArgumentException("XML tidak memiliki root element.");
        }
    }

    private static void setFeatureQuietly(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Android XML parser support varies by API level. Best-effort hardening.
        }
    }

    public static SearchResult find(String text, String needle, int from, boolean forward) {
        if (text == null) text = "";
        if (needle == null) needle = "";
        if (needle.isEmpty()) return new SearchResult(-1, -1, 0, 0);

        String hay = text.toLowerCase(Locale.ROOT);
        String ndl = needle.toLowerCase(Locale.ROOT);
        List<Integer> starts = new ArrayList<>();
        int p = 0;
        while (true) {
            int idx = hay.indexOf(ndl, p);
            if (idx < 0) break;
            starts.add(idx);
            p = Math.max(idx + ndl.length(), idx + 1);
        }
        if (starts.isEmpty()) return new SearchResult(-1, -1, 0, 0);

        int chosen = -1;
        if (forward) {
            for (int i = 0; i < starts.size(); i++) {
                if (starts.get(i) >= from) {
                    chosen = i;
                    break;
                }
            }
            if (chosen < 0) chosen = 0;
        } else {
            for (int i = starts.size() - 1; i >= 0; i--) {
                if (starts.get(i) < from) {
                    chosen = i;
                    break;
                }
            }
            if (chosen < 0) chosen = starts.size() - 1;
        }

        int start = starts.get(chosen);
        return new SearchResult(start, start + needle.length(), chosen + 1, starts.size());
    }

    public static int countIgnoreCase(String text, String needle) {
        if (text == null || needle == null || needle.isEmpty()) return 0;
        String hay = text.toLowerCase(Locale.ROOT);
        String ndl = needle.toLowerCase(Locale.ROOT);
        int count = 0;
        int pos = 0;
        while (true) {
            int idx = hay.indexOf(ndl, pos);
            if (idx < 0) return count;
            count++;
            pos = Math.max(idx + ndl.length(), idx + 1);
        }
    }

    public static String targetSummary(String xml) {
        int pppif = countIgnoreCase(xml, "PPPIF");
        int wancppp = countIgnoreCase(xml, "WANCPPP");
        int devAuth = countIgnoreCase(xml, "DevAuthInfo");
        int user = countIgnoreCase(xml, "name=\"User\"") + countIgnoreCase(xml, "name='User'");
        int pass = countIgnoreCase(xml, "name=\"Pass\"") + countIgnoreCase(xml, "name='Pass'")
                + countIgnoreCase(xml, "name=\"Password\"") + countIgnoreCase(xml, "name='Password'");
        return "PPPIF: " + pppif + " · WANCPPP: " + wancppp + " · DevAuthInfo: " + devAuth
                + " · User: " + user + " · Password/Pass: " + pass;
    }

    public static String extractPppoe(String xml) {
        List<String> blocks = new ArrayList<>();
        blocks.addAll(extractTables(xml, "PPPIF"));
        if (blocks.isEmpty()) blocks.addAll(extractTables(xml, "WANCPPP"));
        if (blocks.isEmpty()) {
            return "Tidak menemukan tabel PPPIF/WANCPPP pada XML ini.";
        }
        return joinBlocks("# PPPoE / PPPIF extraction", blocks);
    }

    public static String extractActiveDevAuthInfo(String xml) {
        List<String> tables = extractTables(xml, "DevAuthInfo");
        if (tables.isEmpty()) return "Tidak menemukan tabel DevAuthInfo pada XML ini.";

        List<String> activeRows = new ArrayList<>();
        Pattern rowPattern = Pattern.compile("(?is)<Row\\b[^>]*>.*?</Row\\s*>");
        Pattern enableOneDouble = Pattern.compile("(?is)<DM\\b[^>]*\\bname\\s*=\\s*\"Enable\"[^>]*\\bval\\s*=\\s*\"1\"[^>]*/?>");
        Pattern enableOneSingle = Pattern.compile("(?is)<DM\\b[^>]*\\bname\\s*=\\s*'Enable'[^>]*\\bval\\s*=\\s*'1'[^>]*/?>");

        for (String table : tables) {
            Matcher rows = rowPattern.matcher(table);
            boolean anyRow = false;
            boolean sawEnableField = false;
            List<String> fallbackRows = new ArrayList<>();
            while (rows.find()) {
                anyRow = true;
                String row = rows.group();
                fallbackRows.add(row.trim());
                boolean hasEnable = row.toLowerCase(Locale.ROOT).contains("name=\"enable\"")
                        || row.toLowerCase(Locale.ROOT).contains("name='enable'");
                sawEnableField |= hasEnable;
                if (enableOneDouble.matcher(row).find() || enableOneSingle.matcher(row).find()) {
                    activeRows.add(row.trim());
                }
            }
            // Some older ZTE configs do not store Enable in DevAuthInfo rows at all.
            // In that case, preserve all rows rather than returning an empty extraction.
            if (anyRow && !sawEnableField) activeRows.addAll(fallbackRows);
        }

        if (activeRows.isEmpty()) {
            return "DevAuthInfo ditemukan, tetapi tidak ada Row dengan Enable=1.";
        }
        return joinBlocks("# DevAuthInfo active rows", activeRows);
    }

    private static List<String> extractTables(String xml, String tableName) {
        List<String> out = new ArrayList<>();
        String quoted = Pattern.quote(tableName);
        Pattern pattern = Pattern.compile(
                "(?is)<Tbl\\b(?=[^>]*\\bname\\s*=\\s*([\"'])" + quoted + "\\1)[^>]*>.*?</Tbl\\s*>"
        );
        Matcher matcher = pattern.matcher(xml == null ? "" : xml);
        while (matcher.find()) out.add(matcher.group().trim());
        return out;
    }

    private static String joinBlocks(String title, List<String> blocks) {
        StringBuilder sb = new StringBuilder(title).append('\n');
        for (int i = 0; i < blocks.size(); i++) {
            sb.append("\n# --- block ").append(i + 1).append(" ---\n");
            sb.append(blocks.get(i)).append('\n');
        }
        return sb.toString();
    }
}
