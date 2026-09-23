package io.github.elanthirian.uafields;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Classpath or file catalog of latest browser versions. Not a detection database. */
public final class VersionCatalog {
    private final Map<String, Entry> byAlias;

    private VersionCatalog(Map<String, Entry> byAlias) {
        this.byAlias = Map.copyOf(byAlias);
    }

    public static VersionCatalog bundled() {
        try (var in = VersionCatalog.class.getResourceAsStream("/catalog/versions.txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource /catalog/versions.txt");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read bundled version catalog", ex);
        }
    }

    public static VersionCatalog load(Path path) {
        try {
            return parse(Files.readString(path));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read version catalog " + path, ex);
        }
    }

    public Entry find(String softwareNameCode) {
        if (softwareNameCode == null) {
            return null;
        }
        return byAlias.get(softwareNameCode);
    }

    static VersionCatalog parse(String text) {
        Map<String, Entry> byAlias = new HashMap<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\\|", -1);
            if (cols.length != 8) {
                throw new IllegalArgumentException("Catalog line " + (i + 1) + " must have 8 columns");
            }
            Entry entry = new Entry(
                    cols[0].trim(),
                    Version.parse(cols[1]),
                    cols[2].isBlank() ? null : LocalDate.parse(cols[2].trim()),
                    blankToNull(cols[3]),
                    blankToNull(cols[4]),
                    Integer.parseInt(cols[5].trim()),
                    Boolean.parseBoolean(cols[6].trim()),
                    aliases(cols[0], cols[7]),
                    readHistory(cols[0].trim()));
            for (String alias : entry.aliases) {
                byAlias.put(alias, entry);
            }
        }
        return new VersionCatalog(byAlias);
    }

    private static List<String> aliases(String id, String raw) {
        List<String> aliases = new ArrayList<>();
        aliases.add(id.trim());
        if (!raw.isBlank()) {
            for (String piece : raw.split(",")) {
                if (!piece.isBlank()) {
                    aliases.add(piece.trim());
                }
            }
        }
        return List.copyOf(aliases);
    }

    private static Map<String, LocalDate> readHistory(String id) {
        var in = VersionCatalog.class.getResourceAsStream("/catalog/history/" + id + ".txt");
        if (in == null) {
            return Map.of();
        }
        Map<String, LocalDate> history = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int bar = trimmed.indexOf('|');
                if (bar <= 0) {
                    continue;
                }
                String version = trimmed.substring(0, bar).trim();
                String day = trimmed.substring(bar + 1).trim();
                if (day.isEmpty()) {
                    continue;
                }
                try {
                    history.put(version, LocalDate.parse(day));
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException("Bad history date for " + id + " " + version, ex);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read version history for " + id, ex);
        }
        return history;
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Entry {
        private final String id;
        private final Version latest;
        private final LocalDate released;
        private final String downloadUrl;
        private final String updateUrl;
        private final int compareSegments;
        private final boolean endOfLife;
        private final List<String> aliases;
        private final Map<String, LocalDate> history;
        private final Map<String, LocalDate> earliestByMajor;

        Entry(String id, Version latest, LocalDate released, String downloadUrl, String updateUrl,
              int compareSegments, boolean endOfLife, List<String> aliases, Map<String, LocalDate> history) {
            this.id = id;
            this.latest = latest;
            this.released = released;
            this.downloadUrl = downloadUrl;
            this.updateUrl = updateUrl;
            this.compareSegments = compareSegments;
            this.endOfLife = endOfLife;
            this.aliases = aliases;
            this.history = history;
            Map<String, LocalDate> byMajor = new HashMap<>();
            for (Map.Entry<String, LocalDate> row : history.entrySet()) {
                String major = row.getKey().split("\\.", 2)[0];
                LocalDate current = byMajor.get(major);
                if (current == null || row.getValue().isBefore(current)) {
                    byMajor.put(major, row.getValue());
                }
            }
            this.earliestByMajor = Map.copyOf(byMajor);
        }

        /**
         * Release day of the detected version. A reduced current major uses the catalog's
         * latest day. Unknown historical builds and versions past the catalog return null.
         */
        LocalDate releasedOn(Version version) {
            if (version == null || version.isEmpty()) {
                return null;
            }
            LocalDate exact = history.get(version.text());
            if (exact != null) {
                return exact;
            }
            int segments = version.reduced() ? 1 : compareSegments;
            if (version.compareAt(latest, segments) == 0) {
                return released;
            }
            if (compareSegments == 1) {
                return earliestByMajor.get(version.major());
            }
            return null;
        }

        public String id() {
            return id;
        }

        public Version latest() {
            return latest;
        }

        public LocalDate released() {
            return released;
        }

        public String downloadUrl() {
            return downloadUrl;
        }

        public String updateUrl() {
            return updateUrl;
        }

        public int compareSegments() {
            return compareSegments;
        }

        public boolean endOfLife() {
            return endOfLife;
        }
    }
}
