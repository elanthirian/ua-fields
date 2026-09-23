package io.github.elanthirian.uafields;

import java.util.ArrayList;
import java.util.List;

/**
 * Dotted numeric version. Components are parsed, not looked up, so a version
 * that has never shipped still compares and displays.
 */
public final class Version implements Comparable<Version> {
    private static final Version EMPTY = new Version(List.of());

    private final List<String> parts;

    private Version(List<String> parts) {
        this.parts = parts;
    }

    public static Version empty() {
        return EMPTY;
    }

    public static Version parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        int start = 0;
        String trimmed = raw.trim();
        while (start < trimmed.length() && !Character.isDigit(trimmed.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < trimmed.length()) {
            char c = trimmed.charAt(end);
            if (Character.isDigit(c) || c == '.') {
                end++;
            } else {
                break;
            }
        }
        if (end == start) {
            return EMPTY;
        }
        String[] split = trimmed.substring(start, end).split("\\.");
        List<String> parts = new ArrayList<>();
        for (String piece : split) {
            if (!piece.isEmpty()) {
                parts.add(piece);
            }
        }
        return parts.isEmpty() ? EMPTY : new Version(List.copyOf(parts));
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public List<String> parts() {
        return parts;
    }

    /** First component. Browser versions are shown this way (Chrome 154.0.8037.58 -> 154). */
    public String major() {
        return parts.isEmpty() ? null : parts.get(0);
    }

    public int majorNumber() {
        return parts.isEmpty() ? 0 : numeric(parts.get(0));
    }

    public int component(int index) {
        return index < parts.size() ? numeric(parts.get(index)) : 0;
    }

    public String text() {
        return String.join(".", parts);
    }

    /**
     * True when every component after the major is zero (Chrome/154.0.0.0).
     * Those strings are reduced user agents, not the build 154.0.0.
     */
    public boolean reduced() {
        if (parts.size() < 2) {
            return false;
        }
        for (int i = 1; i < parts.size(); i++) {
            if (numeric(parts.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }

    public int compareAt(Version other, int segments) {
        int limit = segments <= 0 ? Math.max(parts.size(), other.parts.size()) : segments;
        for (int i = 0; i < limit; i++) {
            int cmp = Integer.compare(component(i), other.component(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    @Override
    public int compareTo(Version other) {
        return compareAt(other, 0);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Version other && parts.equals(other.parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }

    @Override
    public String toString() {
        return text();
    }

    private static int numeric(String part) {
        int value = 0;
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }
}
