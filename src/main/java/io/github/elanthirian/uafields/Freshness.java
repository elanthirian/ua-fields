package io.github.elanthirian.uafields;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** major_gap and age_months from catalog/freshness.txt. */
final class Freshness {
    final int majorGap;
    final int ageMonths;

    private Freshness(int majorGap, int ageMonths) {
        this.majorGap = majorGap;
        this.ageMonths = ageMonths;
    }

    static Freshness bundled() {
        try (var in = Freshness.class.getResourceAsStream("/catalog/freshness.txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource /catalog/freshness.txt");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read freshness policy", ex);
        }
    }

    static Freshness parse(String text) {
        Integer gap = null;
        Integer age = null;
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\\|", -1);
            if (cols.length != 2) {
                throw new IllegalArgumentException("Freshness line " + (i + 1) + " must have 2 columns");
            }
            int value = Integer.parseInt(cols[1].trim());
            switch (cols[0].trim()) {
                case "major_gap" -> gap = value;
                case "age_months" -> age = value;
                default -> throw new IllegalArgumentException("Unknown freshness key " + cols[0].trim());
            }
        }
        if (gap == null || age == null) {
            throw new IllegalArgumentException("Freshness file needs major_gap and age_months");
        }
        return new Freshness(gap, age);
    }

    /** version_gap wins when both rules match. Null means the release is not outdated. */
    String reason(int versionsBehind, LocalDate released, LocalDate today) {
        if (versionsBehind >= majorGap) {
            return "version_gap";
        }
        if (released != null && ChronoUnit.MONTHS.between(released, today) >= ageMonths) {
            return "age";
        }
        return null;
    }
}
