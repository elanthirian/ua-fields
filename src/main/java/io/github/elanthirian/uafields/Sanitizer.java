package io.github.elanthirian.uafields;

final class Sanitizer {
    private Sanitizer() {
    }

    static String sanitize(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "";
        }
        String cleaned = userAgent.replaceAll("[\\p{Cntrl}]", "");
        cleaned = cleaned.replaceAll(
                "(?i)\\{[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\}", "");
        cleaned = cleaned.replaceAll(
                "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "");
        cleaned = cleaned.replaceAll("[ \\t]{2,}", " ").trim();
        return cleaned;
    }
}
