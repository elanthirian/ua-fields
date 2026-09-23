package io.github.elanthirian.uafields;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
                    aliases(cols[0], cols[7]));
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

        Entry(String id, Version latest, LocalDate released, String downloadUrl, String updateUrl,
              int compareSegments, boolean endOfLife, List<String> aliases) {
            this.id = id;
            this.latest = latest;
            this.released = released;
            this.downloadUrl = downloadUrl;
            this.updateUrl = updateUrl;
            this.compareSegments = compareSegments;
            this.endOfLife = endOfLife;
            this.aliases = aliases;
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
