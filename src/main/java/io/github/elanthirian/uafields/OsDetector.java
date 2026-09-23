package io.github.elanthirian.uafields;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OsDetector {
    private static final Pattern MAC = Pattern.compile("Mac OS X[ _](\\d+)[_.](\\d+)(?:[_.](\\d+))?");
    private static final Pattern IOS = Pattern.compile("(?:iPhone OS|CPU iPhone OS|CPU OS)\\s+(\\d+)[_.](\\d+)(?:[_.](\\d+))?");
    private static final Pattern ANDROID = Pattern.compile("Android (\\d+(?:\\.\\d+)*)");
    private static final Pattern WINDOWS = Pattern.compile("Windows NT (\\d+\\.\\d+)(?:\\.(\\d+))?");

    private OsDetector() {
    }

    static OsHit detect(String ua) {
        if (ua.contains("watchOS") || ua.contains("Watch OS")) {
            String version = Text.group(Pattern.compile("(?:watchOS|Watch OS)[/ ](\\d+(?:[_.]\\d+)*)"), ua);
            version = version == null ? null : version.replace('_', '.');
            return new OsHit("watchOS", version, parts(version), null,
                    version == null ? "watchOS" : "watchOS " + version, List.of());
        }
        if (ua.contains("visionOS")) {
            String version = Text.group(Pattern.compile("visionOS[/ ](\\d+(?:[_.]\\d+)*)"), ua);
            version = version == null ? null : version.replace('_', '.');
            return new OsHit("visionOS", version, parts(version), null,
                    version == null ? "visionOS" : "visionOS " + version, List.of());
        }
        Matcher android = ANDROID.matcher(ua);
        if (android.find()) {
            if (ua.contains("Silk/") || ua.contains("Kindle Fire") || ua.matches("(?s).*\\bKF[A-Z0-9]{2,}.*")) {
                return new OsHit("Fire OS", null, List.of(), null, "Fire OS", List.of());
            }
            return android(android.group(1));
        }
        Matcher ios = IOS.matcher(ua);
        if (ios.find() || ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            if (ios.find(0)) {
                boolean ipad = ua.contains("iPad");
                int major = Integer.parseInt(ios.group(1));
                int minor = Integer.parseInt(ios.group(2));
                Integer patch = ios.group(3) == null ? null : Integer.parseInt(ios.group(3));
                return ios(major, minor, patch, ipad);
            }
        }
        Matcher mac = MAC.matcher(ua);
        if (mac.find()) {
            int major = Integer.parseInt(mac.group(1));
            int minor = Integer.parseInt(mac.group(2));
            Integer patch = mac.group(3) == null ? null : Integer.parseInt(mac.group(3));
            return mac(major, minor, patch);
        }
        if (ua.contains("Macintosh")) {
            return new OsHit("macOS", null, List.of(), null, "macOS", List.of());
        }
        Matcher windows = WINDOWS.matcher(ua);
        if (windows.find()) {
            return windowsNt(windows.group(1), windows.group(2));
        }
        if (ua.contains("Windows Phone")) {
            String version = Text.group(Pattern.compile("Windows Phone (?:OS )?(\\d+(?:\\.\\d+)*)"), ua);
            return new OsHit("Windows Phone", version, parts(version), null,
                    version == null ? "Windows Phone" : "Windows Phone " + version, List.of());
        }
        if (ua.contains("CrOS")) {
            return new OsHit("Chrome OS", null, List.of(), null, "Chrome OS", List.of());
        }
        if (ua.contains("Tizen")) {
            String version = Text.group(Pattern.compile("Tizen[/ ](\\d+(?:\\.\\d+)*)"), ua);
            return new OsHit("Tizen", version, parts(version), null,
                    version == null ? "Tizen" : "Tizen " + version, List.of());
        }
        if (ua.contains("Web0S") || ua.contains("webOS")) {
            return new OsHit("webOS", null, List.of(), null, "webOS", List.of());
        }
        if (ua.contains("PlayStation")) {
            String generation = Text.group(Pattern.compile("PlayStation (\\d+)"), ua);
            String display = generation == null ? "PlayStation" : "PlayStation " + generation;
            return new OsHit("PlayStation", generation, generation == null ? List.of() : List.of(generation), null, display, List.of());
        }
        return linux(ua);
    }

    static OsHit mac(int major, int minor, Integer patch) {
        String codename = macCodename(major, minor);
        String name = major > 10 || (major == 10 && minor >= 12) ? "macOS" : "Mac OS X";
        List<String> full = numericParts(major, minor, patch);
        String readable = codename != null ? codename : numericText(major, minor, patch);
        String display = codename != null ? name + " (" + codename + ")" : name + " " + readable;
        return new OsHit(name, readable, full, null, display, List.of());
    }

    static OsHit windowsNt(String nt, String build) {
        int buildNumber = build == null ? -1 : Integer.parseInt(build);
        String marketing = switch (nt) {
            case "10.0" -> buildNumber >= 22000 ? "11" : "10";
            case "6.3" -> "8.1";
            case "6.2" -> "8";
            case "6.1" -> "7";
            case "6.0" -> "Vista";
            case "5.2" -> "XP";
            case "5.1" -> "XP";
            case "5.0" -> "2000";
            default -> nt;
        };
        List<String> full = new ArrayList<>();
        int dot = nt.indexOf('.');
        full.add(dot < 0 ? nt : nt.substring(0, dot));
        full.add(dot < 0 ? "0" : nt.substring(dot + 1));
        if (build != null) {
            full.add(build);
        }
        List<String> notes = "5.2".equals(nt) ? List.of("Possibly running on Windows Server 2003") : List.of();
        return new OsHit("Windows", marketing, List.copyOf(full), null, "Windows " + marketing, notes);
    }

    /** Chromium reports Windows 11 as platform major 13 or higher. 0.1/0.2/0.3 are 7/8/8.1. */
    static OsHit windowsFromClientHint(String platformVersion) {
        Version version = Version.parse(platformVersion);
        int major = version.majorNumber();
        int minor = version.component(1);
        String marketing;
        if (major >= 13) {
            marketing = "11";
        } else if (major > 0) {
            marketing = "10";
        } else if (minor >= 3) {
            marketing = "8.1";
        } else if (minor == 2) {
            marketing = "8";
        } else if (minor == 1) {
            marketing = "7";
        } else {
            marketing = "10";
        }
        return new OsHit("Windows", marketing, List.of(marketing), null, "Windows " + marketing, List.of());
    }

    static OsHit android(String raw) {
        Version version = Version.parse(raw);
        String dessert = androidDessert(version.majorNumber(), version.component(1));
        String readable = dessert != null ? dessert : version.text();
        String display = dessert != null ? "Android (" + dessert + ")" : "Android " + version.major();
        return new OsHit("Android", readable, version.parts(), null, display, List.of());
    }

    static OsHit ios(int major, int minor, Integer patch, boolean ipad) {
        String name = ipad && major >= 13 ? "iPadOS" : "iOS";
        String readable = numericText(major, minor, patch);
        return new OsHit(name, readable, numericParts(major, minor, patch), null, name + " " + readable, List.of());
    }

    static String macCodename(int major, int minor) {
        if (major >= 27) {
            return null;
        }
        if (major == 26) {
            return "Tahoe";
        }
        if (major >= 11) {
            return switch (major) {
                case 11 -> "Big Sur";
                case 12 -> "Monterey";
                case 13 -> "Ventura";
                case 14 -> "Sonoma";
                case 15 -> "Sequoia";
                default -> null;
            };
        }
        if (major == 10) {
            return switch (minor) {
                case 0 -> "Cheetah";
                case 1 -> "Puma";
                case 2 -> "Jaguar";
                case 3 -> "Panther";
                case 4 -> "Tiger";
                case 5 -> "Leopard";
                case 6 -> "Snow Leopard";
                case 7 -> "Lion";
                case 8 -> "Mountain Lion";
                case 9 -> "Mavericks";
                case 10 -> "Yosemite";
                case 11 -> "El Capitan";
                case 12 -> "Sierra";
                case 13 -> "High Sierra";
                case 14 -> "Mojave";
                case 15 -> "Catalina";
                default -> null;
            };
        }
        return null;
    }

    private static String androidDessert(int major, int minor) {
        return switch (major) {
            case 9 -> "Pie";
            case 8 -> "Oreo";
            case 7 -> "Nougat";
            case 6 -> "Marshmallow";
            case 5 -> "Lollipop";
            case 4 -> minor >= 4 ? "KitKat" : (minor >= 1 ? "Jelly Bean" : "Ice Cream Sandwich");
            case 3 -> "Honeycomb";
            case 2 -> {
                if (minor >= 3) {
                    yield "Gingerbread";
                }
                yield minor == 2 ? "Froyo" : "Eclair";
            }
            case 1 -> minor >= 6 ? "Donut" : (minor >= 5 ? "Cupcake" : null);
            default -> null;
        };
    }

    private static OsHit linux(String ua) {
        if (!(ua.contains("Linux") || ua.contains("X11"))) {
            return null;
        }
        String flavour = null;
        if (Text.containsToken(ua, "Ubuntu")) {
            flavour = "Ubuntu";
        } else if (Text.containsToken(ua, "Debian")) {
            flavour = "Debian";
        } else if (Text.containsToken(ua, "Fedora")) {
            flavour = "Fedora";
        } else if (Text.containsToken(ua, "CentOS")) {
            flavour = "CentOS";
        } else if (ua.contains("Red Hat") || Text.containsToken(ua, "RHEL")) {
            flavour = "Red Hat";
        } else if (ua.contains("Linux Mint") || Text.containsToken(ua, "Mint")) {
            flavour = "Linux Mint";
        } else if (Text.containsToken(ua, "Arch")) {
            flavour = "Arch";
        } else if (ua.contains("openSUSE") || Text.containsToken(ua, "SUSE")) {
            flavour = "openSUSE";
        } else if (Text.containsToken(ua, "Gentoo")) {
            flavour = "Gentoo";
        } else if (Text.containsToken(ua, "Alpine")) {
            flavour = "Alpine";
        } else if (Text.containsToken(ua, "Kali")) {
            flavour = "Kali";
        }
        String display = flavour == null ? "Linux" : flavour + " Linux";
        return new OsHit("Linux", null, List.of(), flavour, display, List.of());
    }

    private static List<String> parts(String dotted) {
        if (dotted == null || dotted.isBlank()) {
            return List.of();
        }
        return Version.parse(dotted).parts();
    }

    private static List<String> numericParts(int major, int minor, Integer patch) {
        List<String> full = new ArrayList<>();
        full.add(Integer.toString(major));
        full.add(Integer.toString(minor));
        if (patch != null) {
            full.add(Integer.toString(patch));
        }
        return List.copyOf(full);
    }

    private static String numericText(int major, int minor, Integer patch) {
        if ((patch == null || patch == 0) && minor == 0) {
            return Integer.toString(major);
        }
        if (patch == null || patch == 0) {
            return major + "." + minor;
        }
        return major + "." + minor + "." + patch;
    }
}
