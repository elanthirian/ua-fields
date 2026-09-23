package io.github.elanthirian.uafields;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SoftwareDetector {
    private static final String[][] BOTS = {
            {"Mediapartners-Google", "Mediapartners-Google", "crawler"},
            {"AdsBot-Google", "AdsBot-Google", "crawler"},
            {"FeedFetcher-Google", "FeedFetcher-Google", "feed-fetcher"},
            {"Google-InspectionTool", "Google-InspectionTool", "crawler"},
            {"Storebot-Google", "Storebot-Google", "crawler"},
            {"Google-Extended", "Google-Extended", "ai-crawler"},
            {"Googlebot", "Googlebot", "crawler"},
            {"Applebot-Extended", "Applebot-Extended", "ai-crawler"},
            {"Applebot", "Applebot", "crawler"},
            {"ChatGPT-User", "ChatGPT-User", "ai-agent"},
            {"OAI-SearchBot", "OAI-SearchBot", "ai-crawler"},
            {"GPTBot", "GPTBot", "ai-crawler"},
            {"Claude-User", "Claude-User", "ai-agent"},
            {"ClaudeBot", "ClaudeBot", "ai-crawler"},
            {"PerplexityBot", "PerplexityBot", "ai-crawler"},
            {"Amazonbot", "Amazonbot", "crawler"},
            {"Bytespider", "Bytespider", "ai-crawler"},
            {"Meta-ExternalAgent", "Meta-ExternalAgent", "ai-crawler"},
            {"CCBot", "CCBot", "ai-crawler"},
            {"facebookexternalhit", "Facebook External Hit", "crawler"},
            {"Twitterbot", "Twitterbot", "crawler"},
            {"LinkedInBot", "LinkedInBot", "crawler"},
            {"Slackbot", "Slackbot", "crawler"},
            {"Discordbot", "Discordbot", "crawler"},
            {"TelegramBot", "TelegramBot", "crawler"},
            {"Pinterestbot", "Pinterestbot", "crawler"},
            {"bingbot", "Bingbot", "crawler"},
            {"Baiduspider", "Baiduspider", "crawler"},
            {"YandexBot", "YandexBot", "crawler"},
            {"DuckDuckBot", "DuckDuckBot", "crawler"},
            {"AhrefsBot", "AhrefsBot", "analyser"},
            {"SemrushBot", "SemrushBot", "analyser"},
            {"MJ12bot", "MJ12bot", "crawler"},
            {"DotBot", "DotBot", "crawler"},
            {"PetalBot", "PetalBot", "crawler"},
            {"UptimeRobot", "UptimeRobot", "site-monitor"},
            {"Pingdom", "Pingdom", "site-monitor"},
            {"StatusCake", "StatusCake", "site-monitor"},
            {"Site24x7", "Site24x7", "site-monitor"},
            {"sqlmap", "sqlmap", "security-analyser"},
            {"nikto", "Nikto", "security-analyser"},
            {"nessus", "Nessus", "security-analyser"},
            {"Nmap", "Nmap", "security-analyser"},
            {"masscan", "masscan", "security-analyser"},
            {"ZmEu", "ZmEu", "security-analyser"},
            {"Acunetix", "Acunetix", "security-analyser"}
    };

    private static final Pattern PRODUCT = Pattern.compile("([A-Za-z][A-Za-z0-9_.+-]{0,40})/(\\d+(?:\\.\\d+)*)");
    private static final Pattern MSIE = Pattern.compile("MSIE\\s+(\\d+(?:\\.\\d+)*)");
    private static final Pattern TRIDENT = Pattern.compile("Trident/(\\d+(?:\\.\\d+)*)");
    private static final Pattern RV = Pattern.compile("rv:(\\d+(?:\\.\\d+)*)");
    private static final Set<String> IGNORE_PRODUCTS = Set.of(
            "mozilla", "applewebkit", "version", "safari", "mobile", "gecko", "khtml", "build", "compatible");

    private SoftwareDetector() {
    }

    static String botSubTypeForName(String family) {
        if (family == null) {
            return null;
        }
        for (String[] row : BOTS) {
            if (row[0].equalsIgnoreCase(family) || row[1].equalsIgnoreCase(family)) {
                return row[2];
            }
        }
        return null;
    }

    static SoftwareHit detect(String ua) {
        SoftwareHit hit = bot(ua);
        if (hit != null) {
            return hit;
        }
        hit = email(ua);
        if (hit != null) {
            return hit;
        }
        hit = iam(ua);
        if (hit != null) {
            return hit;
        }
        hit = inApp(ua);
        if (hit != null) {
            return hit;
        }
        hit = browser(ua);
        if (hit != null) {
            return hit;
        }
        hit = library(ua);
        if (hit != null) {
            return hit;
        }
        return fallback(ua);
    }

    private static SoftwareHit bot(String ua) {
        String lower = ua.toLowerCase(java.util.Locale.ROOT);
        for (String[] row : BOTS) {
            String token = row[0].toLowerCase(java.util.Locale.ROOT);
            int from = 0;
            while (from < lower.length()) {
                int at = lower.indexOf(token, from);
                if (at < 0) {
                    break;
                }
                int end = at + token.length();
                boolean leftOk = at == 0 || !Character.isLetterOrDigit(lower.charAt(at - 1));
                boolean rightOk = end >= lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
                if (leftOk && rightOk) {
                    String version = end < ua.length() && ua.charAt(end) == '/' ? Text.readVersion(ua, end + 1) : null;
                    return new SoftwareHit(row[1], Text.slug(row[1]), Version.parse(version), "bot", row[2], null, false);
                }
                from = at + 1;
            }
        }
        return null;
    }

    private static SoftwareHit email(String ua) {
        String thunderbird = Text.versionAfter(ua, "Thunderbird");
        if (thunderbird != null || Text.containsToken(ua, "Thunderbird")) {
            return application("Thunderbird", thunderbird, "email-client");
        }
        if (ua.contains("Outlook-Express")) {
            return application("Outlook Express", Text.versionAfter(ua, "Outlook-Express"), "email-client");
        }
        if (ua.contains("Microsoft Outlook") || ua.contains("Outlook-iOS") || ua.contains("MSOffice")) {
            return application("Outlook", null, "email-client");
        }
        return null;
    }

    private static SoftwareHit inApp(String ua) {
        SoftwareHit hit = namedApp(ua, "Instagram", "Instagram");
        if (hit != null) {
            return hit;
        }
        if (ua.contains("FB_IAB") || ua.contains("FBAN") || ua.contains("FBAV")) {
            return inAppBrowser("Facebook", Text.group(Pattern.compile("FBAV/([0-9.]+)"), ua));
        }
        if (ua.contains("TikTok") || ua.contains("musical_ly") || ua.contains("BytedanceWebview")) {
            return inAppBrowser("TikTok", Text.versionAfter(ua, "TikTok"));
        }
        hit = namedApp(ua, "Snapchat", "Snapchat");
        if (hit != null) {
            return hit;
        }
        if (ua.contains("TwitterAndroid") || ua.contains("Twitter for")) {
            return inAppBrowser("Twitter", Text.versionAfter(ua, "Twitter"));
        }
        if (ua.contains("LinkedInApp")) {
            return inAppBrowser("LinkedIn", Text.versionAfter(ua, "LinkedInApp"));
        }
        hit = namedApp(ua, "Line", "LINE");
        if (hit != null) {
            return hit;
        }
        if (ua.contains("MicroMessenger")) {
            return inAppBrowser("WeChat", Text.versionAfter(ua, "MicroMessenger"));
        }
        hit = namedApp(ua, "Pinterest", "Pinterest");
        if (hit != null) {
            return hit;
        }
        if (ua.contains("GSA/")) {
            return inAppBrowser("Google Search App", Text.versionAfter(ua, "GSA"));
        }
        if (ua.contains("Slack/") || ua.contains("Slack_") || ua.contains("Slackbot")) {
            return inAppBrowser("Slack", Text.versionAfter(ua, "Slack"));
        }
        if (ua.contains("Discord/")) {
            return inAppBrowser("Discord", Text.versionAfter(ua, "Discord"));
        }
        hit = namedApp(ua, "WhatsApp", "WhatsApp");
        if (hit != null) {
            return hit;
        }
        if (ua.contains("Telegram")) {
            return inAppBrowser("Telegram", Text.versionAfter(ua, "Telegram"));
        }
        return null;
    }

    private static SoftwareHit namedApp(String ua, String token, String name) {
        if (!Text.containsToken(ua, token) && !ua.contains(token + "/")) {
            return null;
        }
        String version = Text.versionAfter(ua, token);
        if (version == null) {
            version = Text.group(Pattern.compile(Pattern.quote(token) + " ([0-9.]+)"), ua);
        }
        return inAppBrowser(name, version);
    }

    private static SoftwareHit inAppBrowser(String name, String version) {
        return new SoftwareHit(name, Text.slug(name), Version.parse(version), "browser", "in-app-browser", null, false);
    }

    private static SoftwareHit application(String name, String version, String subType) {
        return new SoftwareHit(name, Text.slug(name), Version.parse(version), "application", subType, null, false);
    }

    private static SoftwareHit browser(String ua) {
        String version = Text.versionAfter(ua, "EdgA");
        if (version == null) {
            version = Text.versionAfter(ua, "EdgiOS");
        }
        if (version == null) {
            version = Text.versionAfter(ua, "Edg");
        }
        if (version != null) {
            return SoftwareHit.browser("Edge", version, null);
        }
        version = Text.versionAfter(ua, "Edge");
        if (version != null) {
            return SoftwareHit.browser("Edge", version, null);
        }
        version = Text.versionAfter(ua, "Opera Mini");
        if (version != null) {
            return SoftwareHit.browser("Opera Mini", version, null);
        }
        version = Text.versionAfter(ua, "OPR");
        if (version == null) {
            version = Text.versionAfter(ua, "Opera");
        }
        if (version != null) {
            return SoftwareHit.browser("Opera", version, null);
        }
        version = Text.versionAfter(ua, "SamsungBrowser");
        if (version != null) {
            return SoftwareHit.browser("Samsung Internet", version, null);
        }
        version = firstVersion(ua, "Brave", "Vivaldi", "YaBrowser", "UCBrowser", "QQBrowser", "Whale");
        if (ua.contains("Brave/") || ua.contains("Brave ")) {
            return SoftwareHit.browser("Brave", version != null ? version : Text.versionAfter(ua, "Chrome"), null);
        }
        if ((version = Text.versionAfter(ua, "Vivaldi")) != null) {
            return SoftwareHit.browser("Vivaldi", version, null);
        }
        if ((version = Text.versionAfter(ua, "YaBrowser")) != null) {
            return SoftwareHit.browser("Yandex Browser", version, null);
        }
        if ((version = Text.versionAfter(ua, "UCBrowser")) != null) {
            return SoftwareHit.browser("UC Browser", version, null);
        }
        if ((version = Text.versionAfter(ua, "QQBrowser")) != null) {
            return SoftwareHit.browser("QQ Browser", version, null);
        }
        if ((version = Text.versionAfter(ua, "DuckDuckGo")) != null) {
            return SoftwareHit.browser("DuckDuckGo", version, null);
        }
        if ((version = Text.versionAfter(ua, "Whale")) != null) {
            return SoftwareHit.browser("Whale", version, null);
        }
        if ((version = Text.versionAfter(ua, "Silk")) != null) {
            return SoftwareHit.browser("Silk", version, null);
        }
        version = Text.versionAfter(ua, "HeadlessChrome");
        if (version != null) {
            return SoftwareHit.browser("Headless Chrome", version, null);
        }
        version = Text.versionAfter(ua, "Chrome");
        if (version == null) {
            version = Text.versionAfter(ua, "CriOS");
        }
        if (version != null && (ua.contains("; wv)") || ua.contains("; wv;") || ua.contains(" wv)"))) {
            return new SoftwareHit("Chrome WebView", "chrome-webview", Version.parse(version), "browser",
                    "in-app-browser", null, true);
        }
        if (version != null && (ua.contains("Chrome/") || ua.contains("CriOS/"))) {
            return SoftwareHit.browser("Chrome", version, null);
        }
        if ((version = Text.versionAfter(ua, "Waterfox")) != null) {
            return SoftwareHit.browser("Waterfox", version, null);
        }
        if ((version = Text.versionAfter(ua, "PaleMoon")) != null) {
            return SoftwareHit.browser("Pale Moon", version, null);
        }
        if ((version = Text.versionAfter(ua, "SeaMonkey")) != null) {
            return SoftwareHit.browser("SeaMonkey", version, null);
        }
        if ((version = Text.versionAfter(ua, "Focus")) != null) {
            return SoftwareHit.browser("Firefox Focus", version, null);
        }
        version = Text.versionAfter(ua, "Firefox");
        if (version == null) {
            version = Text.versionAfter(ua, "FxiOS");
        }
        if (version != null) {
            return SoftwareHit.browser("Firefox", version, null);
        }
        String safari = Text.group(Pattern.compile("Version/(\\d+(?:\\.\\d+)*)"), ua);
        if (safari != null && ua.contains("Safari/") && !ua.contains("Chrome/") && !ua.contains("CriOS/")
                && !ua.contains("Chromium/")) {
            if (ua.contains("Android")) {
                return SoftwareHit.browser("Android Browser", safari, null);
            }
            return SoftwareHit.browser("Safari", safari, null);
        }
        return internetExplorer(ua);
    }

    private static String firstVersion(String ua, String... tokens) {
        for (String token : tokens) {
            String version = Text.versionAfter(ua, token);
            if (version != null) {
                return version;
            }
        }
        return null;
    }

    private static SoftwareHit internetExplorer(String ua) {
        Matcher msie = MSIE.matcher(ua);
        Matcher trident = TRIDENT.matcher(ua);
        boolean hasMsie = msie.find();
        boolean hasTrident = trident.find();
        if (!hasMsie && !(hasTrident && ua.contains("rv:"))) {
            return null;
        }
        Integer msieMajor = hasMsie ? Version.parse(msie.group(1)).majorNumber() : null;
        Integer tridentMajor = hasTrident ? Version.parse(trident.group(1)).majorNumber() : null;
        String rv = Text.group(RV, ua);
        int real;
        if (tridentMajor != null) {
            real = switch (tridentMajor) {
                case 7 -> 11;
                case 6 -> 10;
                case 5 -> 9;
                case 4 -> 8;
                default -> msieMajor != null ? msieMajor : Version.parse(rv).majorNumber();
            };
        } else if (rv != null) {
            real = Version.parse(rv).majorNumber();
        } else {
            real = msieMajor == null ? 0 : msieMajor;
        }
        String sub = msieMajor != null && msieMajor == 7 && real > 7
                ? "Internet Explorer 7 Compatibility View" : null;
        return SoftwareHit.browser("Internet Explorer", Integer.toString(real), sub);
    }

    private static SoftwareHit library(String ua) {
        SoftwareHit hit = product(ua, "okhttp", "OkHttp", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "Dalvik", "Dalvik", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "python-requests", "Python Requests", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "python-urllib3", "Python urllib3", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "Python-urllib", "Python urllib", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "Go-http-client", "Go HTTP Client", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "Apache-HttpClient", "Apache HttpClient", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "Scrapy", "Scrapy", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "aiohttp", "aiohttp", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "axios", "Axios", "software-library");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "node-fetch", "node-fetch", "software-library");
        if (hit != null) {
            return hit;
        }
        if ("node".equals(ua) || hasSlashToken(ua, "node")) {
            return application("Node.js", Text.versionAfter(ua, "node"), "software-library");
        }
        if (ua.contains("libwww-perl")) {
            return application("libwww-perl", null, "software-library");
        }
        hit = product(ua, "curl", "curl", "tool");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "Wget", "wget", "tool");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "wget", "wget", "tool");
        if (hit != null) {
            return hit;
        }
        hit = product(ua, "Java-http-client", "Java HTTP Client", "software-library");
        if (hit != null) {
            return hit;
        }
        return javaRuntime(ua);
    }

    private static SoftwareHit iam(String ua) {
        IamSdk.App app = IamSdk.match(ua);
        if (app == null) {
            return null;
        }
        return application(IamSdk.appName(app.bundle()), app.appVersion(), "mobile-app");
    }

    private static SoftwareHit javaRuntime(String ua) {
        if (!hasSlashToken(ua, "Java")) {
            return null;
        }
        return application("Java", javaVersion(ua), "software-library");
    }

    /** Java update builds use an underscore (1.8.0_191). */
    private static String javaVersion(String ua) {
        String lower = ua.toLowerCase(java.util.Locale.ROOT);
        int at = lower.indexOf("java/");
        if (at < 0) {
            return null;
        }
        int start = at + "java/".length();
        int i = start;
        while (i < ua.length()) {
            char c = ua.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == '_') {
                i++;
            } else {
                break;
            }
        }
        if (i == start) {
            return null;
        }
        return ua.substring(start, i).replace('_', '.');
    }

    private static SoftwareHit product(String ua, String token, String name, String subType) {
        if (!hasSlashToken(ua, token)) {
            return null;
        }
        return application(name, Text.versionAfter(ua, token), subType);
    }

    private static boolean hasSlashToken(String ua, String token) {
        String lower = ua.toLowerCase(java.util.Locale.ROOT);
        String needle = token.toLowerCase(java.util.Locale.ROOT) + "/";
        int from = 0;
        while (from < lower.length()) {
            int at = lower.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            if (at == 0 || !isProductChar(lower.charAt(at - 1))) {
                return true;
            }
            from = at + 1;
        }
        return false;
    }

    private static boolean isProductChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
    }

    private static SoftwareHit fallback(String ua) {
        Matcher matcher = PRODUCT.matcher(ua);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (IGNORE_PRODUCTS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            boolean browserShaped = ua.contains("Mozilla/5.0") || ua.contains("Mozilla/4.0");
            return new SoftwareHit(name, Text.slug(name), Version.parse(matcher.group(2)),
                    browserShaped ? "browser" : "application",
                    browserShaped ? "web-browser" : null,
                    null, false).asFallback();
        }
        return null;
    }
}
