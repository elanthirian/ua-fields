package io.github.elanthirian.uafields;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites versions.txt and catalog/history from the vendor release feeds.
 * Not used during parse. Run from the module directory:
 * java -cp target/classes io.github.elanthirian.uafields.CatalogRefresh
 */
public final class CatalogRefresh {
    private static final String CHROME_VERSIONS =
            "https://versionhistory.googleapis.com/v1/chrome/platforms/win/channels/stable/versions?pageSize=1000";
    private static final String CHROME_RELEASE =
            "https://versionhistory.googleapis.com/v1/chrome/platforms/win/channels/stable/versions/";
    private static final String FIREFOX_LATEST = "https://product-details.mozilla.org/1.0/firefox_versions.json";
    private static final String FIREFOX_HISTORY =
            "https://product-details.mozilla.org/1.0/firefox_history_stability_releases.json";
    private static final String EDGE = "https://edgeupdates.microsoft.com/api/products";
    private static final String OPERA = "https://get.geo.opera.com/pub/opera/desktop/";
    private static final String SAFARI =
            "https://developer.apple.com/tutorials/data/documentation/safari-release-notes.json";
    private static final String SAMSUNG =
            "https://developer.samsung.com/browser/release-note/android-release-note.html";

    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\"(\\d+(?:\\.\\d+)+)\"");
    private static final Pattern RELEASE_DAY = Pattern.compile(
            "\"startTime\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})[^\"]*\".*?\"fraction\"\\s*:\\s*([0-9.]+)",
            Pattern.DOTALL);
    private static final Pattern OPERA_ROW = Pattern.compile(
            "href=\"(\\d+\\.\\d+\\.\\d+\\.\\d+)/\"[^<]*</a>\\s+(\\d{2}-[A-Za-z]{3}-\\d{4})");
    private static final Pattern SAFARI_VER = Pattern.compile("safari-(\\d+(?:_\\d+)*)-release");
    private static final Pattern SAMSUNG_ROW = Pattern.compile(
            "Samsung Internet for Android ([0-9][0-9.\\-]*)(?:</span>)?.*?<small class=\"date\">([^<]*)</small>",
            Pattern.DOTALL);
    private static final DateTimeFormatter OPERA_DAY = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    private CatalogRefresh() {
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of("src/main/resources/catalog") : Path.of(args[0]);
        new CatalogRefresh().refresh(root);
    }

    void refresh(Path root) throws Exception {
        Path history = root.resolve("history");
        Files.createDirectories(history);
        Map<String, String> latest = new LinkedHashMap<>();

        Map<String, String> chrome = chrome();
        write(history.resolve("chrome.txt"), CHROME_VERSIONS, chrome);
        latest.put("chrome", newest(chrome) + "|" + chrome.get(newest(chrome)));

        Map<String, String> firefox = objectDates(get(FIREFOX_HISTORY));
        write(history.resolve("firefox.txt"), FIREFOX_HISTORY, firefox);
        String firefoxLatest = jsonString(get(FIREFOX_LATEST), "LATEST_FIREFOX_VERSION");
        latest.put("firefox", firefoxLatest + "|" + firefox.getOrDefault(firefoxLatest, ""));

        Map<String, String> edge = edge();
        write(history.resolve("edge.txt"), EDGE, edge);
        String edgeLatest = newest(edge);
        latest.put("edge", edgeLatest + "|" + edge.get(edgeLatest));

        Map<String, String> opera = opera();
        write(history.resolve("opera.txt"), OPERA, opera);
        String operaLatest = newest(opera);
        latest.put("opera", operaLatest + "|" + opera.get(operaLatest));

        Map<String, String> safari = safari();
        write(history.resolve("safari.txt"), SAFARI, safari);
        latest.put("safari", newest(safari) + "|");

        Map<String, String> samsung = samsung();
        write(history.resolve("samsung-internet.txt"), SAMSUNG, samsung);
        String samsungLatest = newest(samsung);
        latest.put("samsung-internet", samsungLatest + "|" + samsung.get(samsungLatest));

        patchVersions(root.resolve("versions.txt"), latest);
        for (var row : latest.entrySet()) {
            System.out.println(row.getKey() + " " + row.getValue());
        }
    }

    private Map<String, String> chrome() throws Exception {
        List<String> versions = new ArrayList<>();
        Matcher matcher = VERSION.matcher(get(CHROME_VERSIONS));
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }
        Map<String, String> dates = new TreeMap<>(CatalogRefresh::compareVersions);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String[]>> jobs = new ArrayList<>();
            for (String version : versions) {
                jobs.add(pool.submit(() -> new String[] {version, chromeDate(version)}));
            }
            for (Future<String[]> job : jobs) {
                String[] row = job.get();
                if (row[1] != null) {
                    dates.put(row[0], row[1]);
                }
            }
        } finally {
            pool.shutdown();
        }
        return dates;
    }

    private String chromeDate(String version) throws Exception {
        String body = get(CHROME_RELEASE + version + "/releases?pageSize=50");
        String full = null;
        String any = null;
        Matcher matcher = RELEASE_DAY.matcher(body);
        while (matcher.find()) {
            String day = matcher.group(1);
            if (any == null || day.compareTo(any) < 0) {
                any = day;
            }
            if ("1".equals(matcher.group(2)) && (full == null || day.compareTo(full) < 0)) {
                full = day;
            }
        }
        return full != null ? full : any;
    }

    private Map<String, String> edge() throws Exception {
        String body = get(EDGE);
        int stable = body.indexOf("\"Product\": \"Stable\"");
        if (stable < 0) {
            stable = body.indexOf("\"Product\":\"Stable\"");
        }
        int next = body.indexOf("\"Product\"", stable + 10);
        String slice = next < 0 ? body.substring(stable) : body.substring(stable, next);
        Map<String, String> dates = new TreeMap<>(CatalogRefresh::compareVersions);
        for (String chunk : slice.split("\"ReleaseId\"")) {
            if (!chunk.contains("\"Platform\":\"Windows\"") && !chunk.contains("\"Platform\": \"Windows\"")) {
                continue;
            }
            Matcher version = Pattern.compile("\"ProductVersion\"\\s*:\\s*\"([^\"]+)\"").matcher(chunk);
            Matcher published = Pattern.compile("\"PublishedTime\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})").matcher(chunk);
            if (version.find() && published.find()) {
                dates.merge(version.group(1), published.group(1), (a, b) -> a.compareTo(b) >= 0 ? a : b);
            }
        }
        if (dates.isEmpty()) {
            throw new IllegalStateException("Edge stable feed had no Windows release");
        }
        return dates;
    }

    private Map<String, String> opera() throws Exception {
        Map<String, String> dates = new TreeMap<>(CatalogRefresh::compareVersions);
        Matcher matcher = OPERA_ROW.matcher(get(OPERA));
        while (matcher.find()) {
            String day = LocalDate.parse(matcher.group(2), OPERA_DAY).toString();
            dates.merge(matcher.group(1), day, (a, b) -> a.compareTo(b) <= 0 ? a : b);
        }
        return dates;
    }

    private Map<String, String> safari() throws Exception {
        Map<String, String> versions = new TreeMap<>(CatalogRefresh::compareVersions);
        Matcher matcher = SAFARI_VER.matcher(get(SAFARI));
        while (matcher.find()) {
            versions.put(matcher.group(1).replace('_', '.'), "");
        }
        return versions;
    }

    private Map<String, String> samsung() throws Exception {
        Map<String, String> dates = new TreeMap<>(CatalogRefresh::compareVersions);
        Matcher matcher = SAMSUNG_ROW.matcher(get(SAMSUNG));
        while (matcher.find()) {
            String version = matcher.group(1).split("-")[0];
            String day = samsungDay(matcher.group(2).trim());
            if (day != null) {
                dates.merge(version, day, (a, b) -> a.compareTo(b) <= 0 ? a : b);
            }
        }
        return dates;
    }

    private static String samsungDay(String raw) {
        String[] months = {"", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        String[] full = {"", "January", "February", "March", "April", "May", "June", "July", "August",
                "September", "October", "November", "December"};
        Matcher exact = Pattern.compile("([A-Za-z]+)\\s+(\\d{1,2}),?\\s+(\\d{4})").matcher(raw);
        if (exact.matches()) {
            return String.format("%s-%02d-%02d", exact.group(3), month(exact.group(1), months, full),
                    Integer.parseInt(exact.group(2)));
        }
        Matcher monthYear = Pattern.compile("([A-Za-z]+),?\\s+(\\d{4})").matcher(raw);
        if (monthYear.matches()) {
            return String.format("%s-%02d-01", monthYear.group(2), month(monthYear.group(1), months, full));
        }
        return null;
    }

    private static int month(String name, String[] months, String[] full) {
        for (int i = 1; i < months.length; i++) {
            if (months[i].equalsIgnoreCase(name) || full[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown month " + name);
    }

    private static Map<String, String> objectDates(String json) {
        Map<String, String> dates = new TreeMap<>(CatalogRefresh::compareVersions);
        Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").matcher(json);
        while (matcher.find()) {
            dates.put(matcher.group(1), matcher.group(2));
        }
        return dates;
    }

    private static String jsonString(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing " + key);
        }
        return matcher.group(1);
    }

    private void write(Path path, String source, Map<String, String> rows) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("# source: ").append(source).append('\n');
        out.append("# fetched: ").append(LocalDate.now()).append('\n');
        out.append("# version|YYYY-MM-DD  (date omitted when the source has no day)\n");
        for (var row : rows.entrySet()) {
            out.append(row.getKey()).append('|').append(row.getValue()).append('\n');
        }
        Files.writeString(path, out.toString());
    }

    private void patchVersions(Path path, Map<String, String> latest) throws Exception {
        List<String> lines = Files.readAllLines(path);
        List<String> rewritten = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                rewritten.add(line);
                continue;
            }
            String[] cols = line.split("\\|", -1);
            String update = latest.get(cols[0]);
            if (update != null && cols.length == 8) {
                String[] parts = update.split("\\|", -1);
                cols[1] = parts[0];
                cols[2] = parts.length > 1 ? parts[1] : "";
                rewritten.add(String.join("|", cols));
            } else {
                rewritten.add(line);
            }
        }
        Files.write(path, rewritten, StandardCharsets.UTF_8);
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(40))
                .header("User-Agent", "ua-fields-catalog")
                .GET()
                .build();
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return response.body();
            }
            if (response.statusCode() == 400 || response.statusCode() == 404) {
                throw new IllegalStateException(response.statusCode() + " " + url);
            }
            Thread.sleep(300L * (attempt + 1));
        }
        throw new IllegalStateException("Failed " + url);
    }

    private static String newest(Map<String, String> versions) {
        return versions.keySet().stream().max(CatalogRefresh::compareVersions).orElseThrow();
    }

    private static int compareVersions(String left, String right) {
        List<Integer> a = parts(left);
        List<Integer> b = parts(right);
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int cmp = Integer.compare(i < a.size() ? a.get(i) : 0, i < b.size() ? b.get(i) : 0);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static List<Integer> parts(String version) {
        List<Integer> parts = new ArrayList<>();
        for (String piece : version.split("[^0-9]+")) {
            if (!piece.isEmpty()) {
                parts.add(Integer.parseInt(piece));
            }
        }
        return parts;
    }
}
