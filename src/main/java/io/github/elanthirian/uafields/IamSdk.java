package io.github.elanthirian.uafields;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zoho IAM native client. The app id is the product. Android/31 is API 31, not Android 31.
 * Z_IAMSDK/2.1.6-beta7 (; Native) com.aratai.chat/1.46.4 (1875) Android/34 (Samsung; SM-F956B; hu-HU)
 */
final class IamSdk {
    private static final Pattern APP = Pattern.compile(
            "Z_IAMSDK/(\\S+)\\s+\\([^)]*\\)\\s+([A-Za-z0-9_.]+)/(\\d+(?:\\.\\d+)*)\\s+\\(([^)]+)\\)\\s+"
                    + "(Android|iOS)/(\\d+(?:\\.\\d+)*)\\s+\\(([^)]+)\\)");

    private IamSdk() {
    }

    static App match(String ua) {
        Matcher matcher = APP.matcher(ua);
        if (!matcher.find()) {
            return null;
        }
        String device = matcher.group(7);
        String vendor = null;
        String model = null;
        String locale = null;
        if (device.indexOf(';') >= 0) {
            String[] parts = device.split(";", -1);
            vendor = parts[0].trim();
            model = parts.length > 1 ? decode(parts[1].trim()) : null;
            locale = parts.length > 2 ? parts[2].trim() : null;
        } else {
            String decoded = decode(device.trim());
            if (decoded.startsWith("Apple ")) {
                vendor = "Apple";
                model = decoded.substring("Apple ".length()).trim();
            } else {
                model = decoded;
            }
        }
        return new App(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4).trim(),
                matcher.group(5), matcher.group(6), blank(vendor), blank(model), blank(locale));
    }

    static String appName(String bundle) {
        String lower = bundle.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("arattai") || lower.contains("aratai")) {
            return "Arattai";
        }
        return bundle;
    }

    /** Android SDK level used by this client. Null when the number is not a known API. */
    static String androidRelease(int api) {
        return switch (api) {
            case 21 -> "5.0";
            case 22 -> "5.1";
            case 23 -> "6.0";
            case 24 -> "7.0";
            case 25 -> "7.1";
            case 26 -> "8.0";
            case 27 -> "8.1";
            case 28 -> "9";
            case 29 -> "10";
            case 30 -> "11";
            case 31 -> "12";
            case 32 -> "12.1";
            case 33 -> "13";
            case 34 -> "14";
            case 35 -> "15";
            case 36 -> "16";
            case 37 -> "17";
            default -> null;
        };
    }

    private static String decode(String value) {
        return value.replace("%2C", ",").replace("%2c", ",");
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    record App(String sdk, String bundle, String appVersion, String build, String osName, String osVersion,
               String vendor, String model, String locale) {
    }
}
