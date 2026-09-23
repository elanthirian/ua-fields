package io.github.elanthirian.uafields;

import java.util.regex.Pattern;

final class Signals {
    private static final Pattern[] ABUSIVE = {
            Pattern.compile("(?i)union(?:\\s|/\\*|\\+)+select"),
            Pattern.compile("(?i)information_schema"),
            Pattern.compile("(?i)<\\s*script"),
            Pattern.compile("(?i)javascript\\s*:"),
            Pattern.compile("(?i)(?:\\.\\./|%2e%2e(?:%2f|/))"),
            Pattern.compile("(?i)\\$\\{\\s*jndi\\s*:"),
            Pattern.compile("(?i)'\\s*or\\s+'?\\d"),
            Pattern.compile("(?i)\\b(?:sqlmap|nikto|nessus|nmap|zmeu|masscan|acunetix|openvas|dirbuster)\\b"),
            Pattern.compile("(?i)(?:eval\\s*\\(|document\\.cookie|onerror\\s*=)")
    };

    private static final Pattern RESTRICTED = Pattern.compile("(?i)\\b(?:porn|xxx|hentai)\\b");
    private static final Pattern SPAM_PHRASE = Pattern.compile("(?i)(?:\\bseo\\b.*\\bbacklink\\b|\\bbacklink\\b.*\\bseo\\b|buy cheap|\\bviagra\\b|\\bcialis\\b|casino bonus)");

    final boolean abusive;
    final boolean weird;
    final String weirdReason;
    final boolean restricted;
    final boolean spam;

    private Signals(boolean abusive, boolean weird, String weirdReason, boolean restricted, boolean spam) {
        this.abusive = abusive;
        this.weird = weird;
        this.weirdReason = weirdReason;
        this.restricted = restricted;
        this.spam = spam;
    }

    static Signals inspect(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Signals(false, true, "empty", false, false);
        }
        boolean abusive = false;
        for (Pattern pattern : ABUSIVE) {
            if (pattern.matcher(raw).find()) {
                abusive = true;
                break;
            }
        }
        boolean restricted = RESTRICTED.matcher(raw).find();
        int links = count(raw, "http://") + count(raw, "https://");
        boolean spam = links >= 3 || SPAM_PHRASE.matcher(raw).find();
        String reason = null;
        if (raw.length() > 2048) {
            reason = "too_long";
        } else if (contradicts(raw)) {
            reason = "has_contradictory_info";
        }
        return new Signals(abusive, reason != null, reason, restricted, spam);
    }

    Signals withWeird(String reason) {
        if (weird) {
            return this;
        }
        return new Signals(abusive, true, reason, restricted, spam);
    }

    private static boolean contradicts(String raw) {
        boolean firefox = raw.contains("Firefox/");
        boolean chrome = raw.contains("Chrome/") || raw.contains("CriOS/");
        boolean msie = raw.contains("MSIE ");
        if (firefox && chrome) {
            return true;
        }
        if (msie && chrome) {
            return true;
        }
        if (raw.contains("iPhone") && raw.contains("Windows NT")) {
            return true;
        }
        return raw.contains("Android") && raw.contains("Windows NT");
    }

    private static int count(String value, String token) {
        int found = 0;
        int from = 0;
        while (from < value.length()) {
            int at = value.indexOf(token, from);
            if (at < 0) {
                return found;
            }
            found++;
            from = at + token.length();
        }
        return found;
    }
}
