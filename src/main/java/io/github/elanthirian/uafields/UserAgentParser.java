package io.github.elanthirian.uafields;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe parser. Versions are read from the string, not from a whitelist,
 * so a future Chrome or an unreleased browser token still produces a result.
 */
public final class UserAgentParser {
    private static final Pattern BRAND = Pattern.compile("\"([^\"]+)\";\\s*v=\"([^\"]*)\"");
    private static final Pattern GREASE = Pattern.compile("(?i)not.?a.?brand");

    private final VersionCatalog catalog;
    private final Clock clock;

    private UserAgentParser(VersionCatalog catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    public static UserAgentParser create() {
        return new UserAgentParser(VersionCatalog.bundled(), Clock.systemUTC());
    }

    public static UserAgentParser withCatalog(java.nio.file.Path path) {
        return new UserAgentParser(VersionCatalog.load(path), Clock.systemUTC());
    }

    public UserAgentParser withClock(Clock clock) {
        return new UserAgentParser(catalog, clock);
    }

    public ParseResult parse(String userAgent) {
        return parse(userAgent, null, ParseOptions.defaults());
    }

    public ParseResult parse(String userAgent, ClientHints hints) {
        return parse(userAgent, hints, ParseOptions.defaults());
    }

    public ParseResult parse(String userAgent, ClientHints hints, ParseOptions options) {
        String raw = userAgent == null ? "" : userAgent;
        ParseOptions opts = options == null ? ParseOptions.defaults() : options;
        String sanitized = opts.sanitize() ? Sanitizer.sanitize(raw) : raw;
        String ua = sanitized.isBlank() ? raw : sanitized;

        Signals signals = Signals.inspect(raw);
        SoftwareHit software = ua.isBlank() ? null : SoftwareDetector.detect(ua);
        OsHit os = ua.isBlank() ? null : OsDetector.detect(ua);
        HardwareHit hardware = ua.isBlank() ? null : HardwareDetector.detect(ua);
        if (software != null && "bot".equals(software.type) && (!opts.allowServersToImpersonateDevices() || hardware == null)) {
            hardware = HardwareHit.server();
        }
        software = applySoftwareHint(software, hints);
        if (software == null || !"bot".equals(software.type)) {
            os = applyOsHint(os, hints);
            hardware = applyHardwareHint(hardware, hints);
        }
        if (software != null && software.needsVersion && software.version.isEmpty()) {
            signals = signals.withWeird("didnt_have_software_version_but_needs_one");
        }

        EngineHit engine = ua.isBlank() ? null : EngineDetector.detect(ua, software);
        Enricher.Result extra = ua.isBlank() ? new Enricher.Result(List.of(), List.of(), List.of(), Map.of(), List.of())
                : Enricher.collect(ua);
        return new ParseResult(fields(raw, sanitized, software, os, hardware, engine, extra, signals, hints));
    }

    private Map<String, Object> fields(String raw, String sanitized, SoftwareHit software, OsHit os, HardwareHit hardware,
                                       EngineHit engine, Enricher.Result extra, Signals signals, ClientHints hints) {
        String softwareLabel = null;
        if (software != null) {
            softwareLabel = software.version.isEmpty() ? software.name : software.name + " " + software.version.major();
        }
        String simple = softwareLabel;
        if (softwareLabel != null && os != null && os.display != null) {
            simple = softwareLabel + " on " + os.display;
        } else if (softwareLabel != null && hardware != null && hardware.platform != null) {
            simple = softwareLabel + " on " + hardware.platform;
        }

        Map<String, List<String>> info = new LinkedHashMap<>();
        if (software != null && software.subDescription != null) {
            info.put("10", List.of(software.subDescription));
        }
        List<String> notes = new ArrayList<>();
        if (os != null) {
            notes.addAll(os.notes);
        }
        notes.addAll(extra.notes);
        if (!notes.isEmpty()) {
            info.put("20", List.copyOf(notes));
        }

        Map<String, String> dict = new LinkedHashMap<>(extra.dict);
        if (hints != null && hints.platformVersion() != null && !hints.platformVersion().isBlank()) {
            dict.put("Platform version", hints.platformVersion());
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("user_agent", raw.isEmpty() ? null : raw);
        fields.put("user_agent_sanitized", sanitized.isEmpty() ? null : sanitized);
        fields.put("simple_software_string", simple);
        fields.put("simple_sub_description_string", software == null ? null : software.subDescription);
        fields.put("simple_operating_platform_string", hardware == null ? null : hardware.platform);
        fields.put("software", softwareLabel);
        fields.put("software_name", software == null ? null : software.name);
        fields.put("software_name_code", software == null ? null : software.nameCode);
        fields.put("software_version", software == null || software.version.isEmpty() ? null : software.version.major());
        fields.put("software_version_full", software == null || software.version.isEmpty() ? null : software.version.parts());
        fields.put("software_type", software == null ? null : software.type);
        fields.put("software_sub_type", software == null ? null : software.subType);
        fields.put("operating_system", os == null ? null : os.display);
        fields.put("operating_system_name", os == null ? null : os.name);
        fields.put("operating_system_name_code", os == null ? null : os.nameCode);
        fields.put("operating_system_version", os == null ? null : os.version);
        fields.put("operating_system_version_full", os == null || os.versionFull.isEmpty() ? null : os.versionFull);
        fields.put("operating_system_flavour", os == null ? null : os.flavour);
        fields.put("operating_system_flavour_code", os == null ? null : os.flavourCode);
        fields.put("operating_system_frameworks", extra.frameworks);
        fields.put("operating_platform", hardware == null ? null : hardware.platform);
        fields.put("operating_platform_code", hardware == null ? null : hardware.code);
        fields.put("operating_platform_code_name", hardware == null ? null : hardware.codeName);
        fields.put("operating_platform_vendor_name", hardware == null ? null : hardware.vendor);
        fields.put("hardware_type", hardware == null ? null : hardware.type);
        fields.put("hardware_sub_type", hardware == null ? null : hardware.subType);
        fields.put("hardware_sub_sub_type", hardware == null ? null : hardware.subSubType);
        fields.put("layout_engine_name", engine == null ? null : engine.name);
        fields.put("layout_engine_version", engine == null ? null : engine.version);
        fields.put("extra_info", info);
        fields.put("extra_info_dict", dict);
        fields.put("capabilities", extra.capabilities);
        fields.put("detected_addons", extra.addons);
        fields.put("is_abusive", signals.abusive);
        fields.put("is_weird", signals.weird);
        fields.put("is_weird_reason_code", signals.weirdReason);
        fields.put("is_restricted", signals.restricted);
        fields.put("is_spam", signals.spam);
        fields.put("version_check", versionCheck(software));
        return fields;
    }

    private Map<String, Object> versionCheck(SoftwareHit software) {
        Map<String, Object> check = new LinkedHashMap<>();
        VersionCatalog.Entry entry = software == null ? null : catalog.find(software.nameCode);
        if (entry == null || software.version.isEmpty()) {
            check.put("is_checkable", false);
            check.put("is_up_to_date", null);
            check.put("latest_version", null);
            check.put("download_url", null);
            check.put("update_url", null);
            check.put("release_date", null);
            check.put("hours_released_ago", null);
            check.put("is_ahead_of_catalog", false);
            return check;
        }
        int segments = software.version.reduced() ? 1 : entry.compareSegments();
        int cmp = software.version.compareAt(entry.latest(), segments);
        boolean ahead = cmp > 0;
        check.put("is_checkable", true);
        check.put("is_up_to_date", !entry.endOfLife() && cmp >= 0);
        check.put("latest_version", entry.latest().parts());
        check.put("download_url", entry.downloadUrl());
        check.put("update_url", entry.updateUrl());
        check.put("release_date", entry.released() == null ? null : entry.released().toString());
        check.put("hours_released_ago", hoursSince(entry.released()));
        check.put("is_ahead_of_catalog", ahead);
        return check;
    }

    private Long hoursSince(java.time.LocalDate released) {
        if (released == null) {
            return null;
        }
        return ChronoUnit.HOURS.between(released.atStartOfDay(ZoneOffset.UTC).toInstant(), clock.instant());
    }

    private static SoftwareHit applySoftwareHint(SoftwareHit current, ClientHints hints) {
        if (hints == null || hints.secChUa() == null || hints.secChUa().isBlank()) {
            return current;
        }
        if (current != null && ("bot".equals(current.type) || "in-app-browser".equals(current.subType))) {
            return current;
        }
        String[] chosen = null;
        Matcher matcher = BRAND.matcher(hints.secChUa());
        while (matcher.find()) {
            String brand = matcher.group(1);
            String version = matcher.group(2);
            if (GREASE.matcher(brand).find()) {
                continue;
            }
            if ("Chromium".equalsIgnoreCase(brand)) {
                if (chosen == null) {
                    chosen = new String[] {brand, version};
                }
                continue;
            }
            chosen = new String[] {brand, version};
        }
        if (chosen == null) {
            return current;
        }
        String name = switch (chosen[0].toLowerCase(Locale.ROOT)) {
            case "google chrome" -> "Chrome";
            case "microsoft edge" -> "Edge";
            case "samsung internet" -> "Samsung Internet";
            case "yandex" -> "Yandex Browser";
            default -> chosen[0];
        };
        String sub = current == null ? null : current.subDescription;
        return new SoftwareHit(name, Text.slug(name), Version.parse(chosen[1]), "browser", "web-browser", sub, true);
    }

    private static OsHit applyOsHint(OsHit current, ClientHints hints) {
        if (hints == null || hints.platform() == null || hints.platform().isBlank()) {
            return current;
        }
        String platform = hints.platform().trim().toLowerCase(Locale.ROOT);
        String version = hints.platformVersion();
        boolean missing = version == null || version.isBlank();
        return switch (platform) {
            case "windows" -> missing
                    ? (current != null ? current : new OsHit("Windows", null, List.of(), null, "Windows", List.of()))
                    : OsDetector.windowsFromClientHint(version);
            case "macos", "mac os", "mac os x" -> {
                if (missing) {
                    yield current != null ? current : new OsHit("macOS", null, List.of(), null, "macOS", List.of());
                }
                Version parsed = Version.parse(version);
                Integer patch = parsed.parts().size() > 2 ? parsed.component(2) : null;
                yield OsDetector.mac(parsed.majorNumber(), parsed.component(1), patch);
            }
            case "android" -> missing ? current : OsDetector.android(version);
            case "ios" -> {
                if (missing) {
                    yield current;
                }
                Version parsed = Version.parse(version);
                boolean ipad = current != null && "iPadOS".equals(current.name);
                Integer patch = parsed.parts().size() > 2 ? parsed.component(2) : null;
                yield OsDetector.ios(parsed.majorNumber(), parsed.component(1), patch, ipad);
            }
            case "linux" -> current != null && current.name != null && !"Linux".equals(current.name)
                    ? current
                    : new OsHit("Linux", null, List.of(), current == null ? null : current.flavour,
                    current != null && current.display != null ? current.display : "Linux", List.of());
            case "chrome os", "chromeos" -> new OsHit("Chrome OS", null, List.of(), null, "Chrome OS", List.of());
            default -> current;
        };
    }

    private static HardwareHit applyHardwareHint(HardwareHit current, ClientHints hints) {
        if (hints == null) {
            return current;
        }
        HardwareHit hardware = current;
        if (Boolean.TRUE.equals(hints.mobile()) && (hardware == null || hardware.type == null || "computer".equals(hardware.type))) {
            hardware = new HardwareHit("mobile", "phone", null,
                    hardware == null ? null : hardware.vendor,
                    hardware == null ? null : hardware.code,
                    hardware == null ? null : hardware.codeName,
                    hardware == null ? null : hardware.platform);
        }
        if (hints.model() != null && !hints.model().isBlank()) {
            hardware = HardwareDetector.fromModel(hints.model(), hardware);
        }
        return hardware;
    }
}
