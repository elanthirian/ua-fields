package io.github.elanthirian.uafields;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * First-match runner for the ua-parser/uap-core regex set.
 * Replacement rules follow uap-php: $1..$9, then the captured group, then null.
 */
final class UapCore {
    private static final UapCore LOADED = load();

    private final List<Rule> userAgents;
    private final List<Rule> operatingSystems;
    private final List<Rule> devices;
    private final List<String> skipped;

    private UapCore(List<Rule> userAgents, List<Rule> operatingSystems, List<Rule> devices, List<String> skipped) {
        this.userAgents = userAgents;
        this.operatingSystems = operatingSystems;
        this.devices = devices;
        this.skipped = skipped;
    }

    static UapCore get() {
        return LOADED;
    }

    static int ruleCount() {
        return LOADED.userAgents.size() + LOADED.operatingSystems.size() + LOADED.devices.size();
    }

    static List<String> skipped() {
        return LOADED.skipped;
    }

    Agent matchUserAgent(String userAgent) {
        Match match = first(userAgents, userAgent);
        if (match == null) {
            return null;
        }
        String group1 = groupOrOther(match.matcher, 1);
        String family = expandOr(match.rule, "family_replacement", group1, match.matcher);
        if (family == null) {
            family = "Other";
        }
        return new Agent(family,
                expandOr(match.rule, "v1_replacement", group(match.matcher, 2), match.matcher),
                expandOr(match.rule, "v2_replacement", group(match.matcher, 3), match.matcher),
                expandOr(match.rule, "v3_replacement", group(match.matcher, 4), match.matcher));
    }

    Agent matchOs(String userAgent) {
        Match match = first(operatingSystems, userAgent);
        if (match == null) {
            return null;
        }
        String family = expandOr(match.rule, "os_replacement", groupOrOther(match.matcher, 1), match.matcher);
        if (family == null) {
            family = "Other";
        }
        return new Agent(family,
                expandOr(match.rule, "os_v1_replacement", group(match.matcher, 2), match.matcher),
                expandOr(match.rule, "os_v2_replacement", group(match.matcher, 3), match.matcher),
                expandOr(match.rule, "os_v3_replacement", group(match.matcher, 4), match.matcher),
                expandOr(match.rule, "os_v4_replacement", group(match.matcher, 5), match.matcher));
    }

    Device matchDevice(String userAgent) {
        Match match = first(devices, userAgent);
        if (match == null) {
            return null;
        }
        String group1 = groupOrOther(match.matcher, 1);
        String family = expandOr(match.rule, "device_replacement", group1, match.matcher);
        if (family == null) {
            family = "Other";
        }
        String brand = expandOr(match.rule, "brand_replacement", null, match.matcher);
        String modelDefault = "Other".equals(group1) ? null : group1;
        String model = expandOr(match.rule, "model_replacement", modelDefault, match.matcher);
        return new Device(family, brand, model);
    }

    private static Match first(List<Rule> rules, String userAgent) {
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern.matcher(userAgent);
            if (matcher.find()) {
                return new Match(rule, matcher);
            }
        }
        return null;
    }

    private static String expandOr(Rule rule, String key, String fallback, Matcher matcher) {
        if (!rule.fields.containsKey(key)) {
            return blankToNull(fallback);
        }
        return blankToNull(expand(rule.fields.get(key), matcher));
    }

    private static String expand(String template, Matcher matcher) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '$' && i + 1 < template.length()) {
                char next = template.charAt(i + 1);
                if (next >= '1' && next <= '9') {
                    String value = group(matcher, next - '0');
                    if (value != null) {
                        out.append(value);
                    }
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String group(Matcher matcher, int index) {
        if (index <= 0 || index > matcher.groupCount()) {
            return null;
        }
        return matcher.group(index);
    }

    private static String groupOrOther(Matcher matcher, int index) {
        String value = group(matcher, index);
        return value == null ? "Other" : value;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static UapCore load() {
        List<Rule> userAgents = new ArrayList<>();
        List<Rule> operatingSystems = new ArrayList<>();
        List<Rule> devices = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                UapCore.class.getResourceAsStream("/uap-core/regexes.yaml"), StandardCharsets.UTF_8))) {
            String section = "";
            Map<String, String> fields = null;
            String regex = null;
            String flag = null;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("user_agent_parsers:")) {
                    flush(section, regex, flag, fields, userAgents, operatingSystems, devices, skipped);
                    section = "ua";
                    regex = null;
                    fields = null;
                    flag = null;
                    continue;
                }
                if (trimmed.startsWith("os_parsers:")) {
                    flush(section, regex, flag, fields, userAgents, operatingSystems, devices, skipped);
                    section = "os";
                    regex = null;
                    fields = null;
                    flag = null;
                    continue;
                }
                if (trimmed.startsWith("device_parsers:")) {
                    flush(section, regex, flag, fields, userAgents, operatingSystems, devices, skipped);
                    section = "device";
                    regex = null;
                    fields = null;
                    flag = null;
                    continue;
                }
                if (trimmed.startsWith("- regex:")) {
                    flush(section, regex, flag, fields, userAgents, operatingSystems, devices, skipped);
                    regex = unquote(trimmed.substring(trimmed.indexOf(':') + 1));
                    flag = null;
                    fields = new LinkedHashMap<>();
                    continue;
                }
                int colon = trimmed.indexOf(':');
                if (regex != null && colon > 0) {
                    String key = trimmed.substring(0, colon).trim();
                    String value = unquote(trimmed.substring(colon + 1));
                    if ("regex_flag".equals(key)) {
                        flag = value;
                    } else if (fields != null) {
                        fields.put(key, value);
                    }
                }
            }
            flush(section, regex, flag, fields, userAgents, operatingSystems, devices, skipped);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read uap-core regexes", ex);
        }
        return new UapCore(userAgents, operatingSystems, devices, List.copyOf(skipped));
    }

    private static void flush(String section, String regex, String flag, Map<String, String> fields,
                              List<Rule> userAgents, List<Rule> operatingSystems, List<Rule> devices,
                              List<String> skipped) {
        if (regex == null || section.isEmpty()) {
            return;
        }
        int flags = flag != null && flag.indexOf('i') >= 0 ? Pattern.CASE_INSENSITIVE : 0;
        try {
            Rule rule = new Rule(Pattern.compile(regex, flags), fields == null ? Map.of() : Map.copyOf(fields));
            switch (section) {
                case "ua" -> userAgents.add(rule);
                case "os" -> operatingSystems.add(rule);
                case "device" -> devices.add(rule);
                default -> skipped.add(section + " " + regex);
            }
        } catch (PatternSyntaxException ex) {
            skipped.add(ex.getMessage());
        }
    }

    private static String unquote(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
    }

    private record Rule(Pattern pattern, Map<String, String> fields) {
    }

    private record Match(Rule rule, Matcher matcher) {
    }

    record Agent(String family, String major, String minor, String patch, String extra) {
        Agent(String family, String major, String minor, String patch) {
            this(family, major, minor, patch, null);
        }

        String versionText() {
            StringBuilder out = new StringBuilder();
            for (String part : new String[] {major, minor, patch, extra}) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append('.');
                }
                out.append(part.trim());
            }
            return out.length() == 0 ? null : out.toString();
        }
    }

    record Device(String family, String brand, String model) {
    }
}
