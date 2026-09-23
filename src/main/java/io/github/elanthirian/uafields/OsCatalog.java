package io.github.elanthirian.uafields;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline end-of-life and outdated-release table for operating systems. */
final class OsCatalog {
    private final List<Row> rows;

    private OsCatalog(List<Row> rows) {
        this.rows = List.copyOf(rows);
    }

    static OsCatalog bundled() {
        try (var in = OsCatalog.class.getResourceAsStream("/catalog/os.txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource /catalog/os.txt");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read OS support catalog", ex);
        }
    }

    static OsCatalog parse(String text) {
        List<Row> rows = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\\|", -1);
            if (cols.length != 6) {
                throw new IllegalArgumentException("OS catalog line " + (i + 1) + " must have 6 columns");
            }
            rows.add(new Row(
                    names(cols[1]),
                    cols[2].trim(),
                    Integer.parseInt(cols[3].trim()),
                    cols[4].isBlank() ? null : LocalDate.parse(cols[4].trim()),
                    cols[5].isBlank() ? null : LocalDate.parse(cols[5].trim())));
        }
        return new OsCatalog(rows);
    }

    Map<String, Object> support(OsHit os, LocalDate today, Freshness freshness) {
        Map<String, Object> check = blank();
        if (os == null || !known(os.nameCode)) {
            return check;
        }
        String key = versionKey(os);
        if (key == null) {
            if ("windows".equals(os.nameCode) && "11".equals(os.version)) {
                return unspecified(newest(os.nameCode));
            }
            return check;
        }
        Row row = exact(os.nameCode, key);
        if (row == null) {
            row = star(os.nameCode);
        }
        if (row == null) {
            return outside(os.nameCode, key);
        }
        return fromRow(row, os.nameCode, today, freshness);
    }

    private Map<String, Object> fromRow(Row row, String nameCode, LocalDate today, Freshness freshness) {
        Row newest = newest(nameCode);
        int behind = newest == null ? 0 : Math.max(0, newest.rank - row.rank);
        boolean endOfLife = row.supportEnd != null && !today.isBefore(row.supportEnd);
        String reason = endOfLife ? null : freshness.reason(behind, row.released, today);
        Map<String, Object> check = blank();
        check.put("is_checkable", true);
        check.put("is_up_to_date", !endOfLife && reason == null && behind == 0);
        check.put("is_outdated", reason != null);
        check.put("is_end_of_life", endOfLife);
        check.put("is_ahead_of_catalog", false);
        check.put("latest_version", newest == null ? row.version : newest.version);
        check.put("release_date", row.released == null ? null : row.released.toString());
        check.put("support_end", row.supportEnd == null ? null : row.supportEnd.toString());
        check.put("versions_behind", behind);
        check.put("outdated_reason", reason);
        return check;
    }

    private Map<String, Object> outside(String nameCode, String key) {
        if (!ordered(nameCode)) {
            return blank();
        }
        int detected = order(nameCode, key);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Row row : rows) {
            if (!row.names.contains(nameCode) || "*".equals(row.version)) {
                continue;
            }
            int value = order(nameCode, row.version);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (detected > max) {
            return ahead(newest(nameCode));
        }
        if (detected < min) {
            return ancient(newest(nameCode));
        }
        return blank();
    }

    private static Map<String, Object> ahead(Row newest) {
        Map<String, Object> check = blank();
        check.put("is_checkable", true);
        check.put("is_up_to_date", true);
        check.put("is_outdated", false);
        check.put("is_end_of_life", false);
        check.put("is_ahead_of_catalog", true);
        check.put("latest_version", newest == null ? null : newest.version);
        check.put("versions_behind", 0);
        return check;
    }

    private static Map<String, Object> ancient(Row newest) {
        Map<String, Object> check = blank();
        check.put("is_checkable", true);
        check.put("is_up_to_date", false);
        check.put("is_outdated", false);
        check.put("is_end_of_life", true);
        check.put("latest_version", newest == null ? null : newest.version);
        return check;
    }

    /** Windows 11 is still a living line, but the feature update was not in the UA. */
    private static Map<String, Object> unspecified(Row newest) {
        Map<String, Object> check = blank();
        check.put("is_checkable", true);
        check.put("is_end_of_life", false);
        check.put("latest_version", newest == null ? null : newest.version);
        return check;
    }

    private boolean known(String nameCode) {
        for (Row row : rows) {
            if (row.names.contains(nameCode)) {
                return true;
            }
        }
        return false;
    }

    private Row exact(String nameCode, String version) {
        for (Row row : rows) {
            if (row.names.contains(nameCode) && row.version.equals(version)) {
                return row;
            }
        }
        return null;
    }

    private Row star(String nameCode) {
        for (Row row : rows) {
            if (row.names.contains(nameCode) && "*".equals(row.version)) {
                return row;
            }
        }
        return null;
    }

    private Row newest(String nameCode) {
        Row best = null;
        for (Row row : rows) {
            if (!row.names.contains(nameCode) || "*".equals(row.version)) {
                continue;
            }
            if (best == null || row.rank > best.rank) {
                best = row;
            }
        }
        return best;
    }

    static String versionKey(OsHit os) {
        String name = os.nameCode;
        if ("windows-phone".equals(name)) {
            return "*";
        }
        if ("windows".equals(name)) {
            if ("11".equals(os.version)) {
                Integer build = windows11Build(os);
                if (build == null) {
                    return null;
                }
                if (build >= 28000) {
                    return "11-26H1";
                }
                if (build >= 26200) {
                    return "11-25H2";
                }
                if (build >= 26100) {
                    return "11-24H2";
                }
                if (build >= 22631) {
                    return "11-23H2";
                }
                if (build >= 22621) {
                    return "11-22H2";
                }
                if (build >= 22000) {
                    return "11-21H2";
                }
                return null;
            }
            return os.version == null ? null : os.version.toLowerCase(Locale.ROOT);
        }
        if ("android".equals(name) || "ios".equals(name) || "ipados".equals(name)) {
            return os.versionFull.isEmpty() ? null : os.versionFull.get(0);
        }
        if ("macos".equals(name) || "mac-os-x".equals(name)) {
            if (os.versionFull.size() < 2) {
                return null;
            }
            int major = Integer.parseInt(os.versionFull.get(0));
            int minor = Integer.parseInt(os.versionFull.get(1));
            if (major >= 11) {
                return Integer.toString(major);
            }
            if (major == 10) {
                return "10." + minor;
            }
        }
        return null;
    }

    private static Integer windows11Build(OsHit os) {
        List<String> full = os.versionFull;
        if (full.size() >= 3 && "10".equals(full.get(0))) {
            try {
                return Integer.parseInt(full.get(2));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static boolean ordered(String nameCode) {
        return "android".equals(nameCode) || "ios".equals(nameCode) || "ipados".equals(nameCode)
                || "macos".equals(nameCode) || "mac-os-x".equals(nameCode);
    }

    private static int order(String nameCode, String version) {
        if ("macos".equals(nameCode) || "mac-os-x".equals(nameCode)) {
            if (version.startsWith("10.")) {
                return 1000 + Integer.parseInt(version.substring(3));
            }
            return Integer.parseInt(version) * 100;
        }
        return Integer.parseInt(version);
    }

    private static List<String> names(String raw) {
        List<String> names = new ArrayList<>();
        for (String piece : raw.split(",")) {
            if (!piece.isBlank()) {
                names.add(piece.trim());
            }
        }
        return List.copyOf(names);
    }

    private static Map<String, Object> blank() {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("is_checkable", false);
        check.put("is_up_to_date", null);
        check.put("is_outdated", null);
        check.put("is_end_of_life", null);
        check.put("is_ahead_of_catalog", false);
        check.put("latest_version", null);
        check.put("release_date", null);
        check.put("support_end", null);
        check.put("versions_behind", null);
        check.put("outdated_reason", null);
        return check;
    }

    private static final class Row {
        final List<String> names;
        final String version;
        final int rank;
        final LocalDate released;
        final LocalDate supportEnd;

        Row(List<String> names, String version, int rank, LocalDate released, LocalDate supportEnd) {
            this.names = names;
            this.version = version;
            this.rank = rank;
            this.released = released;
            this.supportEnd = supportEnd;
        }
    }
}
