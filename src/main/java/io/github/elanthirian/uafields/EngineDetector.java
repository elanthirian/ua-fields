package io.github.elanthirian.uafields;

import java.util.List;
import java.util.Set;

final class EngineDetector {
    private static final Set<String> BLINK = Set.of(
            "chrome", "headless-chrome", "chrome-webview", "chromium", "edge", "opera", "samsung-internet",
            "brave", "vivaldi", "yandex-browser", "uc-browser", "qq-browser", "whale", "duckduckgo", "silk");

    private EngineDetector() {
    }

    static EngineHit detect(String ua, SoftwareHit software) {
        String code = software == null ? null : software.nameCode;
        if ("internet-explorer".equals(code) || (ua.contains("Trident/") && !ua.contains("Chrome/"))) {
            return new EngineHit("Trident", parts(Text.versionAfter(ua, "Trident")));
        }
        if ("pale-moon".equals(code)) {
            String goanna = Text.versionAfter(ua, "Goanna");
            return new EngineHit("Goanna", goanna == null ? List.of() : Version.parse(goanna).parts());
        }
        if ("opera-mini".equals(code)) {
            return new EngineHit("Presto", parts(Text.versionAfter(ua, "Presto")));
        }
        if ("edge".equals(code) && ua.contains("Edge/") && !ua.contains("Edg/")) {
            return new EngineHit("EdgeHTML", parts(Text.versionAfter(ua, "Edge")));
        }
        boolean firefoxFamily = "firefox".equals(code) || "waterfox".equals(code) || "seamonkey".equals(code)
                || "firefox-focus".equals(code);
        if (firefoxFamily || (ua.contains("Gecko/") && !ua.contains("Chrome/") && !ua.contains("Safari/"))) {
            String gecko = Text.versionAfter(ua, "Gecko");
            return new EngineHit("Gecko", gecko == null ? List.of() : List.of(gecko));
        }
        if (code != null && BLINK.contains(code)) {
            int major = software.version.majorNumber();
            if ("opera".equals(code) && !ua.contains("OPR/") && major > 0 && major < 15) {
                return new EngineHit("Presto", parts(Text.versionAfter(ua, "Presto")));
            }
            if ("chrome".equals(code) && major > 0 && major < 28) {
                return new EngineHit("WebKit", parts(Text.versionAfter(ua, "AppleWebKit")));
            }
            return new EngineHit("Blink", List.of());
        }
        if (ua.contains("AppleWebKit/")) {
            return new EngineHit("WebKit", parts(Text.versionAfter(ua, "AppleWebKit")));
        }
        if (ua.contains("Gecko/")) {
            return new EngineHit("Gecko", parts(Text.versionAfter(ua, "Gecko")));
        }
        return null;
    }

    private static List<String> parts(String version) {
        if (version == null) {
            return List.of();
        }
        return Version.parse(version).parts();
    }
}
