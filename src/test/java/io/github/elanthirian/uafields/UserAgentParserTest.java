package io.github.elanthirian.uafields;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAgentParserTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
    private final UserAgentParser parser = UserAgentParser.create().withClock(CLOCK);

    @Test
    void chromeOnMavericks() {
        ParseResult result = parser.parse(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.140 Safari/537.36");
        assertEquals("Chrome 64 on Mac OS X (Mavericks)", result.get("simple_software_string"));
        assertEquals("Chrome", result.get("software_name"));
        assertEquals("chrome", result.get("software_name_code"));
        assertEquals("64", result.get("software_version"));
        assertEquals(List.of("64", "0", "3282", "140"), result.get("software_version_full"));
        assertEquals("browser", result.get("software_type"));
        assertEquals("web-browser", result.get("software_sub_type"));
        assertEquals("Mac OS X (Mavericks)", result.get("operating_system"));
        assertEquals("Mac OS X", result.get("operating_system_name"));
        assertEquals("mac-os-x", result.get("operating_system_name_code"));
        assertEquals("Mavericks", result.get("operating_system_version"));
        assertEquals(List.of("10", "9", "5"), result.get("operating_system_version_full"));
        assertEquals("computer", result.get("hardware_type"));
        assertNull(result.get("hardware_sub_type"));
        assertEquals("Blink", result.get("layout_engine_name"));
        assertEquals(List.of(), result.get("layout_engine_version"));
        Map<String, Object> check = check(result);
        assertEquals(false, check.get("is_up_to_date"));
        assertEquals(false, check.get("is_ahead_of_catalog"));
        assertEquals(List.of("154", "0", "8037", "58"), check.get("latest_version"));
        assertEquals(48L, check.get("hours_released_ago"));
        assertFalse((Boolean) result.get("is_abusive"));
        assertFalse((Boolean) result.get("is_weird"));
    }

    @Test
    void reducedCurrentChromeIsUpToDateAndWindowsStays10WithoutABuild() {
        ParseResult result = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36");
        assertEquals("Chrome 154 on Windows 10", result.get("simple_software_string"));
        assertEquals(List.of("154", "0", "0", "0"), result.get("software_version_full"));
        assertEquals("Windows", result.get("operating_system_name"));
        assertEquals("10", result.get("operating_system_version"));
        assertEquals("x64", dict(result).get("Architecture"));
        Map<String, Object> check = check(result);
        assertEquals(true, check.get("is_up_to_date"));
        assertEquals(false, check.get("is_ahead_of_catalog"));
    }

    @Test
    void fullChromeBuildBehindTheCatalogIsNotCurrent() {
        ParseResult result = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.8000.1 Safari/537.36");
        assertEquals(false, check(result).get("is_up_to_date"));
        assertEquals(false, check(result).get("is_ahead_of_catalog"));
    }

    @Test
    void futureChromeStillParsesAndCountsAsCurrent() {
        ParseResult result = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/500.1.2.3 Safari/537.36");
        assertEquals("Chrome", result.get("software_name"));
        assertEquals("500", result.get("software_version"));
        assertEquals(List.of("500", "1", "2", "3"), result.get("software_version_full"));
        assertEquals(true, check(result).get("is_up_to_date"));
        assertEquals(true, check(result).get("is_ahead_of_catalog"));
    }

    @Test
    void windows11FromNtBuild() {
        ParseResult result = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0.22631; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36");
        assertEquals("Windows 11", result.get("operating_system"));
        assertEquals("11", result.get("operating_system_version"));
        assertEquals(List.of("10", "0", "22631"), result.get("operating_system_version_full"));
    }

    @Test
    void clientHintsPromoteWindows11AndCanNameAFutureBrowser() {
        ParseResult windows = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36",
                ClientHints.empty().platform("Windows").platformVersion("15.0.0").mobile(false));
        assertEquals("Windows 11", windows.get("operating_system"));
        assertEquals("15.0.0", dict(windows).get("Platform version"));

        ParseResult edge = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36",
                ClientHints.empty().secChUa("\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"153\", \"Microsoft Edge\";v=\"153\""));
        assertEquals("Edge", edge.get("software_name"));
        assertEquals("153", edge.get("software_version"));
        assertEquals(true, check(edge).get("is_up_to_date"));

        ParseResult future = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36",
                ClientHints.empty().secChUa("\"FutureBrowse\";v=\"3\""));
        assertEquals("FutureBrowse", future.get("software_name"));
        assertEquals("futurebrowse", future.get("software_name_code"));
        assertEquals("3", future.get("software_version"));
        assertEquals("browser", future.get("software_type"));
        assertEquals(false, check(future).get("is_checkable"));
    }

    @Test
    void firefoxOsFlavourEngineAndFutureMajor() {
        ParseResult current = parser.parse(
                "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:156.0) Gecko/20100101 Firefox/156.0");
        assertEquals("Firefox 156 on Ubuntu Linux", current.get("simple_software_string"));
        assertEquals("Linux", current.get("operating_system_name"));
        assertEquals("Ubuntu", current.get("operating_system_flavour"));
        assertEquals("ubuntu", current.get("operating_system_flavour_code"));
        assertEquals("Gecko", current.get("layout_engine_name"));
        assertEquals(List.of("20100101"), current.get("layout_engine_version"));
        assertEquals(true, check(current).get("is_up_to_date"));

        ParseResult catalina = parser.parse(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:136.0) Gecko/20100101 Firefox/136.0");
        assertEquals("macOS (Catalina)", catalina.get("operating_system"));
        assertEquals("macos", catalina.get("operating_system_name_code"));
        assertEquals(false, check(catalina).get("is_up_to_date"));

        ParseResult future = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:300.0) Gecko/20100101 Firefox/300.0");
        assertEquals("Firefox", future.get("software_name"));
        assertEquals("300", future.get("software_version"));
        assertEquals(true, check(future).get("is_ahead_of_catalog"));
    }

    @Test
    void safariIosAndFutureMacWithoutInventingACodename() {
        ParseResult iphone = parser.parse(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1");
        assertEquals("Safari 17 on iOS 17.4", iphone.get("simple_software_string"));
        assertEquals("iOS", iphone.get("operating_system_name"));
        assertEquals("17.4", iphone.get("operating_system_version"));
        assertEquals(List.of("17", "4"), iphone.get("operating_system_version_full"));
        assertEquals("mobile", iphone.get("hardware_type"));
        assertEquals("phone", iphone.get("hardware_sub_type"));
        assertEquals("Apple", iphone.get("operating_platform_vendor_name"));
        assertEquals("Apple iPhone", iphone.get("operating_platform"));
        assertEquals("WebKit", iphone.get("layout_engine_name"));
        assertEquals(List.of("605", "1", "15"), iphone.get("layout_engine_version"));
        assertEquals(false, check(iphone).get("is_up_to_date"));

        ParseResult ipad = parser.parse(
                "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1");
        assertEquals("iPadOS 17.4", ipad.get("operating_system"));
        assertEquals("tablet", ipad.get("hardware_sub_type"));

        ParseResult futureOs = parser.parse(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 27_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/40.0 Safari/605.1.15");
        assertEquals("Safari 40 on macOS 27.1", futureOs.get("simple_software_string"));
        assertEquals("27.1", futureOs.get("operating_system_version"));
        assertEquals(true, check(futureOs).get("is_ahead_of_catalog"));
    }

    @Test
    void edgeOperaAndSamsungDoNotCollapseToChrome() {
        ParseResult edge = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36 Edg/153.0.3404.52");
        assertEquals("Edge", edge.get("software_name"));
        assertEquals("153", edge.get("software_version"));
        assertEquals("Blink", edge.get("layout_engine_name"));
        assertEquals(true, check(edge).get("is_up_to_date"));

        ParseResult futureEdge = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/200.0.0.0 Safari/537.36 Edg/200.0.1.1");
        assertEquals("Edge", futureEdge.get("software_name"));
        assertEquals(true, check(futureEdge).get("is_ahead_of_catalog"));

        ParseResult legacy = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.140 Safari/537.36 Edge/18.18363");
        assertEquals("EdgeHTML", legacy.get("layout_engine_name"));

        ParseResult opera = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 OPR/136.0.0.0");
        assertEquals("Opera", opera.get("software_name"));
        assertEquals(true, check(opera).get("is_up_to_date"));

        ParseResult samsung = parser.parse(
                "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/30.0 Chrome/154.0.0.0 Mobile Safari/537.36");
        assertEquals("Samsung Internet", samsung.get("software_name"));
        assertEquals("samsung-internet", samsung.get("software_name_code"));
        assertEquals("Android 14", samsung.get("operating_system"));
        assertEquals("phone", samsung.get("hardware_sub_type"));
        assertEquals("Samsung", samsung.get("operating_platform_vendor_name"));
        assertEquals("SM-S928B", samsung.get("operating_platform_code"));
        assertEquals("Galaxy S24 Ultra", samsung.get("operating_platform_code_name"));
        assertEquals("Samsung Galaxy S24 Ultra (SM-S928B)", samsung.get("simple_operating_platform_string"));
        assertEquals(true, check(samsung).get("is_up_to_date"));
    }

    @Test
    void headlessChromeWebViewAndInAppBrowser() {
        ParseResult headless = parser.parse(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36");
        assertEquals("Headless Chrome", headless.get("software_name"));
        assertEquals("headless-chrome", headless.get("software_name_code"));
        assertEquals(true, check(headless).get("is_up_to_date"));
        assertEquals("computer", headless.get("hardware_type"));

        ParseResult webview = parser.parse(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UQ1A.240105.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/154.0.0.0 Mobile Safari/537.36");
        assertEquals("Chrome WebView", webview.get("software_name"));
        assertEquals("in-app-browser", webview.get("software_sub_type"));
        assertEquals("Google", webview.get("operating_platform_vendor_name"));
        assertEquals("Pixel 8", webview.get("operating_platform_code_name"));
        assertEquals("UQ1A.240105.004", dict(webview).get("System Build"));

        ParseResult instagram = parser.parse(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 320.0.0.0.0");
        assertEquals("Instagram", instagram.get("software_name"));
        assertEquals("in-app-browser", instagram.get("software_sub_type"));
        assertEquals("320", instagram.get("software_version"));
        assertEquals("phone", instagram.get("hardware_sub_type"));
    }

    @Test
    void botsLibrariesAndServerImpersonation() {
        ParseResult googlebot = parser.parse(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");
        assertEquals("Googlebot", googlebot.get("software_name"));
        assertEquals("bot", googlebot.get("software_type"));
        assertEquals("crawler", googlebot.get("software_sub_type"));
        assertEquals("2", googlebot.get("software_version"));
        assertEquals("server", googlebot.get("hardware_type"));

        String phoneBot = "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.7390.122 Mobile Safari/537.36 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
        ParseResult masked = parser.parse(phoneBot);
        assertEquals("Googlebot", masked.get("software_name"));
        assertEquals("server", masked.get("hardware_type"));
        assertEquals("Android (Marshmallow)", masked.get("operating_system"));

        ParseResult impersonated = parser.parse(phoneBot, null, ParseOptions.defaults().allowServersToImpersonateDevices(true));
        assertEquals("phone", impersonated.get("hardware_sub_type"));
        assertEquals("Google Nexus 5X", impersonated.get("operating_platform"));

        ParseResult gpt = parser.parse(
                "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko); compatible; GPTBot/1.2; +https://openai.com/gptbot");
        assertEquals("GPTBot", gpt.get("software_name"));
        assertEquals("ai-crawler", gpt.get("software_sub_type"));

        ParseResult curl = parser.parse("curl/8.7.1");
        assertEquals("curl", curl.get("software_name"));
        assertEquals("application", curl.get("software_type"));
        assertEquals("tool", curl.get("software_sub_type"));
        assertEquals("8", curl.get("software_version"));
        assertNull(curl.get("hardware_type"));

        ParseResult requests = parser.parse("python-requests/2.32.3");
        assertEquals("Python Requests", requests.get("software_name"));
        assertEquals("software-library", requests.get("software_sub_type"));

        ParseResult dalvik = parser.parse("Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/UQ1A)");
        assertEquals("Dalvik", dalvik.get("software_name"));
        assertEquals("Android 14", dalvik.get("operating_system"));
        assertEquals("Google Pixel 8", dalvik.get("operating_platform"));
        assertEquals("2.1.0", dict(dalvik).get("Dalvik library version"));
    }

    @Test
    void internetExplorerCompatibilityFrameworksAndEndOfLife() {
        ParseResult ie11 = parser.parse(
                "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko");
        assertEquals("Internet Explorer 11 on Windows 7", ie11.get("simple_software_string"));
        assertEquals("Trident", ie11.get("layout_engine_name"));
        assertEquals(List.of("7", "0"), ie11.get("layout_engine_version"));
        assertEquals(false, check(ie11).get("is_up_to_date"));
        assertEquals(true, check(ie11).get("is_checkable"));

        ParseResult compat = parser.parse(
                "Mozilla/5.0 (compatible; MSIE 7.0; Windows NT 6.1; WOW64; Trident/7.0; .NET CLR 2.0.50727; .NET CLR 3.5.30729; .NET CLR 3.0.30729)");
        assertEquals("11", compat.get("software_version"));
        assertEquals("Internet Explorer 7 Compatibility View", compat.get("simple_sub_description_string"));
        assertEquals(List.of("Internet Explorer 7 Compatibility View"), extra(compat).get("10"));
        List<Map<String, Object>> frameworks = frameworks(compat);
        assertEquals("microsoft-dotnet", frameworks.get(0).get("code"));
        assertEquals(3, ((List<?>) frameworks.get(0).get("versions")).size());
    }

    @Test
    void abusiveWeirdRestrictedSpamAndSanitizedGuid() {
        ParseResult injected = parser.parse("Mozilla/5.0 ' OR 1=1 --");
        assertTrue((Boolean) injected.get("is_abusive"));

        ParseResult scanner = parser.parse("sqlmap/1.8.5#stable (https://sqlmap.org)");
        assertEquals("security-analyser", scanner.get("software_sub_type"));
        assertTrue((Boolean) scanner.get("is_abusive"));

        ParseResult empty = parser.parse((String) null);
        assertTrue((Boolean) empty.get("is_weird"));
        assertEquals("empty", empty.get("is_weird_reason_code"));
        assertNull(empty.get("software_name"));

        ParseResult contradictory = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0) Firefox/100.0 Chrome/100.0.0.0");
        assertTrue((Boolean) contradictory.get("is_weird"));
        assertEquals("has_contradictory_info", contradictory.get("is_weird_reason_code"));

        ParseResult spam = parser.parse(
                "Mozilla/5.0 (compatible; seo backlink bot; http://spam.example http://spam.example/a http://spam.example/b)");
        assertTrue((Boolean) spam.get("is_spam"));

        ParseResult restricted = parser.parse("porn-scanner/1.0");
        assertTrue((Boolean) restricted.get("is_restricted"));

        String guid = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; {12345678-1234-1234-1234-1234567890ab}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36";
        ParseResult sanitized = parser.parse(guid);
        assertEquals("Chrome", sanitized.get("software_name"));
        assertFalse(String.valueOf(sanitized.get("user_agent_sanitized")).contains("12345678"));
        assertTrue(String.valueOf(sanitized.get("user_agent")).contains("12345678"));
    }

    @Test
    void unknownFutureProductTokenIsNotDropped() {
        ParseResult shaped = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) FutureBrowser/9.8.1");
        assertEquals("FutureBrowser", shaped.get("software_name"));
        assertEquals("9", shaped.get("software_version"));
        assertEquals(List.of("9", "8", "1"), shaped.get("software_version_full"));
        assertEquals("browser", shaped.get("software_type"));
        assertEquals("web-browser", shaped.get("software_sub_type"));

        ParseResult bare = parser.parse("FutureBrowser/9.8.1");
        assertEquals("application", bare.get("software_type"));
        assertNull(bare.get("software_sub_type"));
    }

    @Test
    void silkFireOsPlaystationAndChromeOs() {
        ParseResult silk = parser.parse(
                "Mozilla/5.0 (Linux; Android 9; KFMAWI) AppleWebKit/537.36 (KHTML, like Gecko) Silk/92.2.11 like Chrome/92.0.4515.159 Safari/537.36");
        assertEquals("Silk", silk.get("software_name"));
        assertEquals("92", silk.get("software_version"));
        assertEquals("Fire OS", silk.get("operating_system_name"));
        assertEquals("Amazon", silk.get("operating_platform_vendor_name"));
        assertEquals("tablet", silk.get("hardware_sub_type"));

        ParseResult ps = parser.parse(
                "Mozilla/5.0 (PlayStation; PlayStation 5/6.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15");
        assertEquals("large-screen", ps.get("hardware_type"));
        assertEquals("game-console", ps.get("hardware_sub_type"));
        assertEquals("PlayStation 5", ps.get("operating_system"));
        assertEquals("Sony", ps.get("operating_platform_vendor_name"));

        ParseResult cros = parser.parse(
                "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36");
        assertEquals("Chrome OS", cros.get("operating_system"));
        assertEquals("computer", cros.get("hardware_type"));
    }

    @Test
    void catalogFileOverridesBundledLatest(@TempDir Path dir) throws Exception {
        Path catalog = dir.resolve("versions.txt");
        Files.writeString(catalog, """
                chrome|10.0.0.0|2020-01-01|https://www.google.com/chrome/|https://support.google.com/chrome/answer/95414|3|false|chrome
                """);
        UserAgentParser custom = UserAgentParser.withCatalog(catalog).withClock(CLOCK);
        ParseResult result = custom.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.140 Safari/537.36");
        assertEquals(true, check(result).get("is_ahead_of_catalog"));
        assertEquals(true, check(result).get("is_up_to_date"));
        assertEquals(List.of("10", "0", "0", "0"), check(result).get("latest_version"));
    }

    @Test
    void uapCoreRulesCompileAndCoverFamiliesWeDidNotName() {
        assertTrue(UapCore.ruleCount() >= 1200, "rules=" + UapCore.ruleCount());
        assertEquals(List.of(), UapCore.skipped());

        ParseResult minefield = parser.parse(
                "Mozilla/5.0 (Windows; Windows NT 5.1; rv:2.0b3pre) Gecko/20100727 Minefield/4.0.1pre");
        assertEquals("Firefox (Minefield)", minefield.get("software_name"));
        assertEquals("4", minefield.get("software_version"));
        assertEquals(List.of("4", "0", "1"), minefield.get("software_version_full"));

        ParseResult coc = parser.parse(
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) coc_coc_browser/95.0.150 Chrome/89.0.4389.114 Safari/537.36");
        assertEquals("Coc Coc", coc.get("software_name"));
        assertEquals("95", coc.get("software_version"));
        assertEquals("browser", coc.get("software_type"));

        ParseResult ipadShell = parser.parse(
                "Mozilla/5.0 (iPad; CPU OS 7_0_4 like Mac OS X) AppleWebKit/537.51.1 (KHTML, like Gecko) Mobile/11B554a");
        assertEquals("Mobile Safari UI/WKWebView", ipadShell.get("software_name"));
        assertEquals("iOS 7.0.4", ipadShell.get("operating_system"));
        assertEquals("tablet", ipadShell.get("hardware_sub_type"));

        ParseResult playbook = parser.parse(
                "Mozilla/5.0 (BlackBerry PlayBook - RIM Tablet OS 2.1.0.1917; U) Safari/537.36");
        assertEquals("BlackBerry WebKit", playbook.get("software_name"));
        assertEquals("2", playbook.get("software_version"));

        ParseResult ladybird = parser.parse("Ladybird/1.2");
        assertEquals("Ladybird", ladybird.get("software_name"));
        assertEquals("browser", ladybird.get("software_type"));
        assertEquals("1", ladybird.get("software_version"));

        ParseResult ieMobile = parser.parse(
                "Mozilla/4.0 (compatible; MSIE 7.0; Windows Phone OS 7.0; Trident/3.1; IEMobile/7.0; SAMSUNG; SGH-i917)");
        assertEquals("IE Mobile", ieMobile.get("software_name"));
        assertEquals("7", ieMobile.get("software_version"));
        assertEquals("Windows Phone", ieMobile.get("operating_system_name"));
        assertEquals("Samsung", ieMobile.get("operating_platform_vendor_name"));

        ParseResult chromeFrame = parser.parse(
                "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1; chromeframe/11.0.660.0)");
        assertEquals("Chrome Frame", chromeFrame.get("software_name"));
        assertEquals("11", chromeFrame.get("software_version"));

        ParseResult oculus = parser.parse(
                "Mozilla/5.0 (X11; Linux x86_64; Quest 2) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/26.2.0.0.10 SamsungBrowser/4.0 Chrome/110.0.5481.192 VR Safari/537.36");
        assertEquals("Oculus Browser", oculus.get("software_name"));
        assertEquals("26", oculus.get("software_version"));

        ParseResult chess = parser.parse(
                "Chesscom-Android/4.9.21-googleplay (Android/15; SM-A165F; ru_RU; contact #android in Slack)");
        assertEquals("Chesscom-Android", chess.get("software_name"));
        assertNotEquals("Slack", chess.get("software_name"));

        ParseResult sdk = parser.parse(
                "ElasticMapReduce/1.0.0 emrfs/s3n {}, aws-sdk-java/1.11.129 Linux/4.4.35-33.55.amzn1.x86_64 OpenJDK_64-Bit_Server_VM/25.141-b16/1.8.0_141");
        assertEquals("aws-sdk-java", sdk.get("software_name"));
        assertEquals("1", sdk.get("software_version"));

        ParseResult freebsd = parser.parse(
                "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.0.19) Gecko/2010031218 FreeBSD/i386 Firefox/3.0.19");
        assertEquals("FreeBSD", freebsd.get("operating_system_name"));
        assertEquals("Firefox", freebsd.get("software_name"));

        ParseResult ds = parser.parse("Mozilla/5.0 (Nintendo 3DS; U; ; en) Version/1.7498.US");
        assertEquals("game-console", ds.get("hardware_sub_type"));
        assertEquals("3DS", ds.get("operating_platform_code"));
        assertNotEquals("Switch", ds.get("operating_platform_code"));

        ParseResult vita = parser.parse(
                "Mozilla/5.0 (PlayStation Vita 1.81) AppleWebKit/531.22.8 (KHTML, like Gecko) Silk/3.2");
        assertEquals("PlayStation Vita", vita.get("operating_platform_code"));
        assertEquals("Sony", vita.get("operating_platform_vendor_name"));

        ParseResult lumia = parser.parse(
                "Mozilla/5.0 (Mobile; Windows Phone 8.1; Android 4.0; ARM; Trident/7.0; Touch; rv:11.0; IEMobile/11.0; NOKIA; Lumia 920; ANZ821)");
        assertEquals("Windows Phone", lumia.get("operating_system_name"));
        assertEquals("IE Mobile", lumia.get("software_name"));
        assertEquals("Lumia 920", lumia.get("operating_platform_code"));

        ParseResult desktopCriOs = parser.parse(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/125 Version/13.0.3 Safari/605.1.15");
        assertEquals("iOS", desktopCriOs.get("operating_system_name"));
        assertEquals("Edge", desktopCriOs.get("software_name"));

        ParseResult appleTv = parser.parse(
                "AppleCoreMedia/1.0.0.12F69 (Apple TV; U; CPU OS 8_3 like Mac OS X; en_us)");
        assertEquals("ATV OS X", appleTv.get("operating_system_name"));
        assertEquals("8.3", appleTv.get("operating_system_version"));
        assertEquals("Apple TV", appleTv.get("operating_platform"));
    }

    @Test
    void versionCompareTreatsMissingComponentsAsZero() {
        assertTrue(Version.parse("154.0.8037").compareAt(Version.parse("154.0.8037.58"), 3) == 0);
        assertTrue(Version.parse("9.8.1").compareAt(Version.parse("9.8"), 0) > 0);
        assertTrue(Version.parse("Chrome/500.1.2.3").reduced() == false);
        assertTrue(Version.parse("154.0.0.0").reduced());
        assertEquals("500", Version.parse("Chrome/500.1.2.3").major());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> check(ParseResult result) {
        return (Map<String, Object>) result.get("version_check");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> dict(ParseResult result) {
        return (Map<String, String>) result.get("extra_info_dict");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> extra(ParseResult result) {
        return (Map<String, List<String>>) result.get("extra_info");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> frameworks(ParseResult result) {
        return (List<Map<String, Object>>) result.get("operating_system_frameworks");
    }
}
