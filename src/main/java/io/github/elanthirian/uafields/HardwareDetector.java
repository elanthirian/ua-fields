package io.github.elanthirian.uafields;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HardwareDetector {
    private static final Pattern ANDROID_MODEL = Pattern.compile("Android \\d+(?:\\.\\d+)*;\\s*(?:[a-zA-Z]{2}-[a-zA-Z]{2};\\s*)?([^;)]+)");
    private static final Map<String, String> KNOWN = Map.ofEntries(
            Map.entry("SM-T817R4", "Galaxy Tab S2 9.7"),
            Map.entry("SM-T817", "Galaxy Tab S2 9.7"),
            Map.entry("SM-G960U", "Galaxy S9"),
            Map.entry("SM-G960F", "Galaxy S9"),
            Map.entry("SM-G973F", "Galaxy S10"),
            Map.entry("SM-G998B", "Galaxy S21 Ultra"),
            Map.entry("SM-S901B", "Galaxy S22"),
            Map.entry("SM-S908B", "Galaxy S22 Ultra"),
            Map.entry("SM-S911B", "Galaxy S23"),
            Map.entry("SM-S918B", "Galaxy S23 Ultra"),
            Map.entry("SM-S921B", "Galaxy S24"),
            Map.entry("SM-S928B", "Galaxy S24 Ultra"),
            Map.entry("SM-S931B", "Galaxy S25"),
            Map.entry("SM-S938B", "Galaxy S25 Ultra"),
            Map.entry("SM-A546B", "Galaxy A54"),
            Map.entry("SM-F946B", "Galaxy Z Fold5"),
            Map.entry("SM-F956B", "Galaxy Z Fold6"),
            Map.entry("SM-F741B", "Galaxy Z Flip6"),
            Map.entry("LE2111", "OnePlus 9"),
            Map.entry("2411DRN47I", "Redmi 14C 5G"),
            Map.entry("Pixel 8", "Pixel 8"),
            Map.entry("Pixel 8 Pro", "Pixel 8 Pro"),
            Map.entry("Pixel 9", "Pixel 9"),
            Map.entry("Pixel 9 Pro", "Pixel 9 Pro"));

    private HardwareDetector() {
    }

    static HardwareHit detect(String ua) {
        HardwareHit sdk = iamDevice(ua);
        if (sdk != null) {
            return sdk;
        }
        if (ua.contains("Vision Pro") || ua.contains("visionOS")) {
            return device("mobile", "wearable", "vr", "Apple", "Vision Pro", "Vision Pro", "Apple Vision Pro");
        }
        if (ua.contains("watchOS") || ua.contains("Apple Watch")) {
            return device("mobile", "wearable", "watch", "Apple", null, "Apple Watch", "Apple Watch");
        }
        if (Text.containsToken(ua, "iPod")) {
            return device("mobile", "music-player", null, "Apple", "iPod", "iPod", "Apple iPod");
        }
        if (Text.containsToken(ua, "iPad")) {
            return device("mobile", "tablet", null, "Apple", "iPad", "iPad", "Apple iPad");
        }
        if (Text.containsToken(ua, "iPhone")) {
            return device("mobile", "phone", null, "Apple", "iPhone", "iPhone", "Apple iPhone");
        }
        if (ua.contains("Apple TV") || ua.contains("AppleTV")) {
            return device("large-screen", "tv", null, "Apple", "Apple TV", "Apple TV", "Apple TV");
        }
        if (ua.toLowerCase(Locale.ROOT).contains("playstation")) {
            String generation = Text.group(Pattern.compile("(?i)PlayStation (\\d+)"), ua);
            if (generation != null) {
                String name = "PlayStation " + generation;
                return device("large-screen", "game-console", null, "Sony", name, name, "Sony " + name);
            }
        }
        if (ua.contains("Xbox")) {
            return device("large-screen", "game-console", null, "Microsoft", "Xbox", "Xbox", "Microsoft Xbox");
        }
        if (ua.contains("Nintendo Switch")) {
            return device("large-screen", "game-console", null, "Nintendo", "Switch", "Nintendo Switch", "Nintendo Switch");
        }
        if (ua.contains("Nintendo")) {
            return device("large-screen", "game-console", null, "Nintendo", null, null, "Nintendo");
        }
        if (ua.contains("SMART-TV") || ua.contains("SmartTV") || ua.contains("Smart-TV") || ua.contains("HbbTV")
                || ua.contains("Web0S") || ua.contains("BRAVIA") || Text.containsToken(ua, "Tizen") && ua.toLowerCase(Locale.ROOT).contains("tv")) {
            String vendor = ua.contains("Web0S") ? "LG" : (ua.contains("BRAVIA") ? "Sony" : (ua.contains("Tizen") ? "Samsung" : null));
            return device("large-screen", "tv", null, vendor, null, null, vendor == null ? "Smart TV" : vendor + " Smart TV");
        }
        if (ua.contains("Kindle/") && !ua.contains("Android")) {
            return device("mobile", "ebook-reader", null, "Amazon", "Kindle", "Kindle", "Amazon Kindle");
        }
        HardwareHit android = androidDevice(ua);
        if (android != null) {
            return android;
        }
        if (ua.contains("CrOS") || ua.contains("Windows NT") || ua.contains("Macintosh") || ua.contains("X11") || ua.contains("Linux")) {
            return device("computer", null, null, null, null, null, null);
        }
        return null;
    }

    private static HardwareHit iamDevice(String ua) {
        IamSdk.App app = IamSdk.match(ua);
        if (app == null || app.model() == null) {
            return null;
        }
        if ("Apple".equalsIgnoreCase(app.vendor()) && app.model().startsWith("iPhone")) {
            String codeName = iphoneName(app.model());
            String platform = codeName == null ? "Apple " + app.model() : "Apple " + codeName + " (" + app.model() + ")";
            return device("mobile", "phone", null, "Apple", app.model(), codeName, platform);
        }
        HardwareHit described = fromModel(app.model(), device("mobile", "phone", null, app.vendor(), app.model(), null, null));
        String vendor = described.vendor != null ? described.vendor : app.vendor();
        String codeName = described.codeName;
        String platform;
        if (codeName != null && vendor != null && codeName.toLowerCase(Locale.ROOT).startsWith(vendor.toLowerCase(Locale.ROOT))) {
            platform = codeName + " (" + app.model() + ")";
        } else if (codeName != null && vendor != null) {
            platform = vendor + " " + codeName + " (" + app.model() + ")";
        } else if (vendor != null) {
            platform = vendor + " " + app.model();
        } else {
            platform = described.platform;
        }
        String sub = described.subType == null ? "phone" : described.subType;
        return device("mobile", sub, null, vendor, app.model(), codeName, platform);
    }

    private static String iphoneName(String identifier) {
        return switch (identifier) {
            case "iPhone17,1" -> "iPhone 16 Pro";
            case "iPhone17,2" -> "iPhone 16 Pro Max";
            case "iPhone17,3" -> "iPhone 16";
            case "iPhone17,4" -> "iPhone 16 Plus";
            case "iPhone17,5" -> "iPhone 16e";
            default -> null;
        };
    }

    static HardwareHit fromModel(String model, HardwareHit current) {
        if (model == null || model.isBlank()) {
            return current;
        }
        String cleaned = cleanModel(model);
        if (cleaned == null) {
            return current;
        }
        if ("iPad".equalsIgnoreCase(cleaned)) {
            return device("mobile", "tablet", null, "Apple", "iPad", "iPad", "Apple iPad");
        }
        if ("iPhone".equalsIgnoreCase(cleaned)) {
            return device("mobile", "phone", null, "Apple", "iPhone", "iPhone", "Apple iPhone");
        }
        Described described = describe(cleaned);
        String type = current != null && current.type != null ? current.type : "mobile";
        String sub = current != null ? current.subType : null;
        if (described.tablet) {
            type = "mobile";
            sub = "tablet";
        } else if ("mobile".equals(type) && sub == null) {
            sub = "phone";
        }
        return device(type, sub, current == null ? null : current.subSubType, described.vendor, described.code,
                described.codeName, described.platform);
    }

    private static HardwareHit androidDevice(String ua) {
        if (!ua.contains("Android") && !ua.contains("Dalvik/")) {
            return null;
        }
        Matcher matcher = ANDROID_MODEL.matcher(ua);
        String model = null;
        if (matcher.find()) {
            model = cleanModel(matcher.group(1));
        }
        boolean tablet = ua.contains("Tablet") || (model != null && isTabletCode(model))
                || (ua.contains("Android") && !ua.contains("Mobile") && !ua.contains("Dalvik/"));
        if (ua.contains("Mobile")) {
            tablet = model != null && isTabletCode(model);
        }
        Described described = model == null ? new Described(null, null, null, null, false) : describe(model);
        if (described.tablet) {
            tablet = true;
        }
        String type = "mobile";
        String sub = tablet ? "tablet" : "phone";
        if (model != null && model.startsWith("KF")) {
            return device(type, "tablet", null, "Amazon", model, described.codeName,
                    platform("Amazon", described.codeName, model));
        }
        return device(type, sub, null, described.vendor, described.code, described.codeName, described.platform);
    }

    private static Described describe(String model) {
        String codeName = KNOWN.get(model);
        if (codeName == null && model.startsWith("Pixel")) {
            codeName = model;
        }
        String vendor = vendorOf(model);
        if (vendor == null && codeName != null && codeName.startsWith("Pixel")) {
            vendor = "Google";
        }
        boolean tablet = isTabletCode(model);
        return new Described(vendor, model, codeName, platform(vendor, codeName, model), tablet);
    }

    private static String platform(String vendor, String codeName, String code) {
        StringBuilder out = new StringBuilder();
        if (vendor != null) {
            out.append(vendor);
        }
        if (codeName != null) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(codeName);
        }
        if (code != null && (codeName == null || !code.equals(codeName))) {
            if (out.length() == 0) {
                out.append(code);
            } else if (codeName == null) {
                out.append(' ').append(code);
            } else {
                out.append(" (").append(code).append(')');
            }
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static boolean isTabletCode(String model) {
        return model.startsWith("SM-T") || model.startsWith("SM-P") || model.startsWith("SM-X") || model.contains("Tablet");
    }

    private static String vendorOf(String model) {
        String upper = model.toUpperCase(Locale.ROOT);
        if (upper.startsWith("SM-") || upper.startsWith("GT-") || upper.startsWith("SCH-") || upper.startsWith("SGH-")) {
            return "Samsung";
        }
        if (upper.startsWith("PIXEL") || upper.startsWith("NEXUS")) {
            return "Google";
        }
        if (upper.startsWith("REDMI") || upper.startsWith("POCO") || upper.startsWith("XIAOMI") || upper.startsWith("MI ")) {
            return "Xiaomi";
        }
        if (upper.startsWith("ONEPLUS") || upper.startsWith("KB") || upper.startsWith("LE") || upper.startsWith("AC")) {
            return "OnePlus";
        }
        if (upper.startsWith("HUAWEI") || upper.startsWith("LYA-") || upper.startsWith("VOG-") || upper.startsWith("ANA-")
                || upper.startsWith("ELS-") || upper.startsWith("NOH-")) {
            return "Huawei";
        }
        if (upper.startsWith("MOTO")) {
            return "Motorola";
        }
        if (upper.startsWith("NOKIA") || upper.startsWith("TA-")) {
            return "Nokia";
        }
        if (upper.startsWith("KF")) {
            return "Amazon";
        }
        if (upper.startsWith("CPH")) {
            return "OPPO";
        }
        if (upper.startsWith("VIVO") || upper.startsWith("V2")) {
            return "vivo";
        }
        if (upper.startsWith("RMX")) {
            return "realme";
        }
        if (upper.startsWith("LM-") || upper.startsWith("LG-")) {
            return "LG";
        }
        return null;
    }

    private static String cleanModel(String raw) {
        if (raw == null) {
            return null;
        }
        String model = raw.trim();
        int build = model.indexOf(" Build/");
        if (build >= 0) {
            model = model.substring(0, build).trim();
        }
        if (model.isEmpty() || "U".equals(model) || "wv".equals(model) || model.equalsIgnoreCase("Mobile")) {
            return null;
        }
        if (model.matches("[a-z]{2}-[a-zA-Z]{2}")) {
            return null;
        }
        return model;
    }

    private static HardwareHit device(String type, String subType, String subSubType, String vendor, String code,
                                      String codeName, String platform) {
        return new HardwareHit(type, subType, subSubType, vendor, code, codeName, platform);
    }

    private record Described(String vendor, String code, String codeName, String platform, boolean tablet) {
    }
}
