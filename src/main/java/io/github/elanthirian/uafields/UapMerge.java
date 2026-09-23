package io.github.elanthirian.uafields;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fills gaps from uap-core. A family this library already named is kept when it is the same
 * product (Chrome vs Chrome Mobile, Silk vs Amazon Silk). A more specific uap-core family replaces
 * a broad label such as Internet Explorer, Java, or a raw device code.
 */
final class UapMerge {
    private static final Set<String> GENERIC_DEVICE = Set.of(
            "other", "mac", "pc", "spider", "windows", "linux", "smartphone", "feature phone",
            "generic smartphone", "generic feature phone", "generic tablet", "desktop");

    private UapMerge() {
    }

    static SoftwareHit software(SoftwareHit current, String userAgent) {
        UapCore.Agent agent = UapCore.get().matchUserAgent(userAgent);
        if (agent == null || agent.family() == null || "Other".equalsIgnoreCase(agent.family())) {
            return current;
        }
        if (current == null || current.fallback) {
            return fromAgent(agent, current);
        }
        if ("application".equals(current.type)) {
            return current;
        }
        if (equivalent(current.name, agent.family()) || genericFamily(agent.family())) {
            return current;
        }
        return fromAgent(agent, current);
    }

    static OsHit os(OsHit current, String userAgent) {
        UapCore.Agent agent = UapCore.get().matchOs(userAgent);
        if (agent == null || agent.family() == null || "Other".equalsIgnoreCase(agent.family())) {
            return current;
        }
        if (current == null) {
            return fromOs(agent);
        }
        if (sameOs(current, agent)) {
            return current;
        }
        String ours = current.name.toLowerCase(Locale.ROOT);
        String theirs = agent.family().toLowerCase(Locale.ROOT);
        if ("Linux".equals(current.name) && current.flavour == null && !"linux".equals(theirs)) {
            return fromOs(agent);
        }
        if (theirs.contains(ours) && theirs.length() > ours.length()) {
            return fromOs(agent);
        }
        return current;
    }

    static HardwareHit hardware(HardwareHit current, String userAgent) {
        UapCore.Device device = UapCore.get().matchDevice(userAgent);
        if (device == null || device.family() == null || "Other".equalsIgnoreCase(device.family())) {
            return current;
        }
        if (current != null && current.codeName != null) {
            return current;
        }
        if (!specificDevice(device)) {
            return current;
        }
        return fromDevice(device, current);
    }

    private static SoftwareHit fromAgent(UapCore.Agent agent, SoftwareHit current) {
        String bot = SoftwareDetector.botSubTypeForName(agent.family());
        if (bot == null && current != null && "bot".equals(current.type) && related(current.name, agent.family())) {
            bot = current.subType;
        }
        String type = bot == null ? "browser" : "bot";
        String sub = bot == null ? "web-browser" : bot;
        return new SoftwareHit(agent.family(), Text.slug(agent.family()), Version.parse(agent.versionText()),
                type, sub, null, false);
    }

    private static OsHit fromOs(UapCore.Agent agent) {
        String version = agent.versionText();
        if ("Android".equalsIgnoreCase(agent.family()) && version != null) {
            return OsDetector.android(version);
        }
        if (("iOS".equalsIgnoreCase(agent.family()) || "iPhone OS".equalsIgnoreCase(agent.family())) && version != null) {
            Version parsed = Version.parse(version);
            return OsDetector.ios(parsed.majorNumber(), parsed.component(1),
                    parsed.parts().size() > 2 ? parsed.component(2) : null, false);
        }
        if (("Mac OS X".equalsIgnoreCase(agent.family()) || "Mac OS".equalsIgnoreCase(agent.family())) && version != null) {
            Version parsed = Version.parse(version);
            return OsDetector.mac(parsed.majorNumber(), parsed.component(1),
                    parsed.parts().size() > 2 ? parsed.component(2) : null);
        }
        String display = version == null ? agent.family() : agent.family() + " " + version;
        List<String> full = version == null ? List.of() : Version.parse(version).parts();
        return new OsHit(agent.family(), version, full, null, display, List.of());
    }

    private static HardwareHit fromDevice(UapCore.Device device, HardwareHit current) {
        String code = device.model() != null ? device.model() : device.family();
        String vendor = device.brand() != null ? device.brand() : (current == null ? null : current.vendor);
        String type = current != null && current.type != null && !"computer".equals(current.type)
                ? current.type : guessType(device);
        String sub;
        if (current != null && "tablet".equals(current.subType)) {
            sub = "tablet";
        } else if (current != null && current.subType != null && !"phone".equals(current.subType)) {
            sub = current.subType;
        } else {
            sub = guessSub(device, type);
        }
        return new HardwareHit(type, sub, current == null ? null : current.subSubType, vendor, code, null,
                joinPlatform(vendor, code));
    }

    private static boolean equivalent(String ours, String theirs) {
        String other = theirs.toLowerCase(Locale.ROOT);
        return switch (ours) {
            case "Chrome" -> other.equals("chrome") || other.startsWith("chrome ");
            case "Chromium" -> other.equals("chromium") || other.startsWith("chrome");
            case "Headless Chrome" -> other.contains("headless");
            case "Safari" -> other.contains("safari");
            case "Firefox" -> other.equals("firefox") || other.startsWith("firefox ");
            case "Edge" -> other.equals("edge") || other.startsWith("edge ");
            case "Opera" -> other.equals("opera") || other.startsWith("opera ");
            case "Silk" -> other.contains("silk");
            case "Internet Explorer" -> other.equals("ie") || other.equals("internet explorer");
            default -> ours.equalsIgnoreCase(theirs);
        };
    }

    private static boolean genericFamily(String family) {
        String other = family.toLowerCase(Locale.ROOT);
        return other.equals("chrome") || other.startsWith("chrome mobile")
                || other.equals("safari") || other.equals("mobile safari")
                || other.equals("firefox") || other.equals("firefox mobile")
                || other.equals("edge") || other.equals("edge mobile")
                || other.equals("opera") || other.equals("opera mobile")
                || other.equals("android") || other.equals("android browser")
                || other.equals("webkit") || other.equals("ie") || other.equals("other");
    }

    private static boolean sameOs(OsHit current, UapCore.Agent agent) {
        String ours = current.name.toLowerCase(Locale.ROOT);
        String theirs = agent.family().toLowerCase(Locale.ROOT);
        if (ours.equals(theirs)) {
            return true;
        }
        if ((ours.equals("macos") || ours.equals("mac os x"))
                && (theirs.equals("mac os x") || theirs.equals("mac os") || theirs.equals("macos"))) {
            return true;
        }
        return (ours.equals("ios") || ours.equals("ipados"))
                && (theirs.equals("ios") || theirs.equals("iphone os") || theirs.equals("ipados"));
    }

    private static boolean specificDevice(UapCore.Device device) {
        if (genericDevice(device.family())) {
            return false;
        }
        return device.model() == null || !genericDevice(device.model());
    }

    private static boolean genericDevice(String value) {
        return GENERIC_DEVICE.contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean related(String ours, String theirs) {
        String left = ours.toLowerCase(Locale.ROOT).replace(" ", "");
        String right = theirs.toLowerCase(Locale.ROOT).replace(" ", "");
        return right.contains(left) || left.contains(right);
    }

    private static String guessType(UapCore.Device device) {
        String family = device.family().toLowerCase(Locale.ROOT);
        if (family.contains("ipad") || family.contains("iphone") || family.contains("ipod")
                || family.contains("phone") || family.contains("tablet")) {
            return "mobile";
        }
        if (family.contains("tv") || family.contains("playstation") || family.contains("xbox")
                || family.contains("nintendo")) {
            return "large-screen";
        }
        return "computer";
    }

    private static String guessSub(UapCore.Device device, String type) {
        String family = ((device.family() == null ? "" : device.family()) + " "
                + (device.model() == null ? "" : device.model())).toLowerCase(Locale.ROOT);
        if (family.contains("ipad") || family.contains("tablet")) {
            return "tablet";
        }
        if (family.contains("iphone") || family.contains("phone")) {
            return "phone";
        }
        if (family.contains("playstation") || family.contains("xbox") || family.contains("nintendo")) {
            return "game-console";
        }
        if (family.contains("tv")) {
            return "tv";
        }
        if ("mobile".equals(type)) {
            return "phone";
        }
        return null;
    }

    private static String joinPlatform(String vendor, String code) {
        if (vendor == null || vendor.isBlank()) {
            return code;
        }
        if (code == null || code.isBlank() || code.toLowerCase(Locale.ROOT).startsWith(vendor.toLowerCase(Locale.ROOT))) {
            return code == null || code.isBlank() ? vendor : code;
        }
        return vendor + " " + code;
    }
}
