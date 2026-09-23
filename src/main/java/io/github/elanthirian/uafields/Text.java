package io.github.elanthirian.uafields;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Text {
    private Text() {
    }

    static String slug(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? null : slug;
    }

    static String group(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    static boolean containsToken(String haystack, String token) {
        String lower = haystack.toLowerCase(Locale.ROOT);
        String needle = token.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lower.length()) {
            int at = lower.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            int end = at + needle.length();
            boolean leftOk = at == 0 || !Character.isLetterOrDigit(lower.charAt(at - 1));
            boolean rightOk = end >= lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = at + 1;
        }
        return false;
    }

    static String versionAfter(String ua, String token) {
        String lower = ua.toLowerCase(Locale.ROOT);
        String needle = token.toLowerCase(Locale.ROOT);
        int at = lower.indexOf(needle);
        if (at < 0) {
            return null;
        }
        int i = at + needle.length();
        if (i >= ua.length() || ua.charAt(i) != '/') {
            return null;
        }
        return readVersion(ua, i + 1);
    }

    static String readVersion(String value, int start) {
        int i = start;
        while (i < value.length()) {
            char c = value.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.') {
                i++;
            } else {
                break;
            }
        }
        return i == start ? null : value.substring(start, i);
    }
}
