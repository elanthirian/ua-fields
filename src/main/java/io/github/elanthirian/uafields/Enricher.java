package io.github.elanthirian.uafields;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Enricher {
    private static final Pattern DOTNET = Pattern.compile("\\.NET(?: CLR)? ?([0-9]+(?:\\.[0-9]+)*)");
    private static final Pattern DOTNET_SHORT = Pattern.compile("\\.NET([0-9]+(?:\\.[0-9]+)*)[A-Z]?");
    private static final Pattern YAHOO = Pattern.compile("(?i)Yahoo!? Toolbar(?:/([0-9.]+))?");
    private static final Pattern BUILD = Pattern.compile("Build/([A-Za-z0-9._-]+)");
    private static final Pattern MIDP = Pattern.compile("MIDP[/ ](\\d+(?:\\.\\d+)?)");

    private Enricher() {
    }

    static Result collect(String ua) {
        List<Map<String, Object>> frameworks = dotnet(ua);
        List<String> addons = new ArrayList<>();
        Matcher yahoo = YAHOO.matcher(ua);
        if (yahoo.find()) {
            String version = yahoo.group(1);
            addons.add(version == null ? "Yahoo Toolbar" : "Yahoo Toolbar v" + version);
        }
        List<String> capabilities = new ArrayList<>();
        if (Text.containsToken(ua, "Touch")) {
            capabilities.add("Touch capability");
        }
        if (ua.contains("Silk-Accelerated")) {
            capabilities.add("Silk Accelerated");
        }
        Matcher midp = MIDP.matcher(ua);
        if (midp.find()) {
            capabilities.add("MIDP v" + midp.group(1));
        }
        Map<String, String> dict = new LinkedHashMap<>();
        String dalvik = Text.versionAfter(ua, "Dalvik");
        if (dalvik != null) {
            dict.put("Dalvik library version", dalvik);
        }
        Matcher build = BUILD.matcher(ua);
        if (build.find()) {
            dict.put("System Build", build.group(1));
        }
        String arch = architecture(ua);
        if (arch != null) {
            dict.put("Architecture", arch);
        }
        List<String> notes = new ArrayList<>();
        if (ua.contains("Tablet PC")) {
            notes.add("Tablet PC");
        }
        return new Result(frameworks, List.copyOf(addons), List.copyOf(capabilities), Map.copyOf(dict), List.copyOf(notes));
    }

    private static List<Map<String, Object>> dotnet(String ua) {
        List<Map<String, Object>> versions = new ArrayList<>();
        Matcher matcher = DOTNET.matcher(ua);
        while (matcher.find()) {
            addVersion(versions, matcher.group(1));
        }
        Matcher shorthand = DOTNET_SHORT.matcher(ua);
        while (shorthand.find()) {
            addVersion(versions, shorthand.group(1));
        }
        if (versions.isEmpty()) {
            return List.of();
        }
        Map<String, Object> framework = new LinkedHashMap<>();
        framework.put("code", "microsoft-dotnet");
        framework.put("name", "Microsoft .Net");
        framework.put("versions", List.copyOf(versions));
        return List.of(framework);
    }

    private static void addVersion(List<Map<String, Object>> versions, String raw) {
        String joined = Version.parse(raw).text();
        for (Map<String, Object> item : versions) {
            Object full = item.get("full");
            if (full instanceof List<?> list && joined.equals(String.join(".", list.stream().map(String::valueOf).toList()))) {
                return;
            }
        }
        versions.add(versionMap(raw));
    }

    private static Map<String, Object> versionMap(String raw) {
        Version version = Version.parse(raw);
        String simple = version.parts().size() >= 2
                ? version.parts().get(0) + "." + version.parts().get(1)
                : version.major();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("simple", simple);
        item.put("full", version.parts());
        return item;
    }

    private static String architecture(String ua) {
        if (ua.contains("arm64") || ua.contains("aarch64")) {
            return "arm64";
        }
        if (ua.contains("Win64") || ua.contains("x86_64") || ua.contains("x64") || ua.contains("WOW64")) {
            return ua.contains("WOW64") ? "x64-wow64" : "x64";
        }
        if (ua.contains("i686") || ua.contains("i386")) {
            return "x86";
        }
        return null;
    }

    static final class Result {
        final List<Map<String, Object>> frameworks;
        final List<String> addons;
        final List<String> capabilities;
        final Map<String, String> dict;
        final List<String> notes;

        Result(List<Map<String, Object>> frameworks, List<String> addons, List<String> capabilities,
               Map<String, String> dict, List<String> notes) {
            this.frameworks = frameworks;
            this.addons = addons;
            this.capabilities = capabilities;
            this.dict = dict;
            this.notes = notes;
        }
    }
}
