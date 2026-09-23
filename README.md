# ua-fields

Java 17 library that turns a user-agent string into the same field set published by the [WhatIsMyBrowser parse API](https://developers.whatismybrowser.com/api/features/user-agent-parse). Detection is structural. A Chrome, Firefox, Edge, or other token with a version that does not exist yet still parses, and it is not marked out of date.

This project is not affiliated with WhatIsMyBrowser. The field names match their public response so the result is familiar. The rules, catalog, and code are independent.

## Use

```java
UserAgentParser parser = UserAgentParser.create();
ParseResult result = parser.parse(request.getHeader("User-Agent"));
String name = (String) result.get("software_name");          // Chrome
String simple = (String) result.get("simple_software_string"); // Chrome 154 on Windows 10
```

Client Hints override a reduced user agent when you have them:

```java
ParseResult result = parser.parse(ua, ClientHints.empty()
        .secChUa(request.getHeader("Sec-CH-UA"))
        .platform(request.getHeader("Sec-CH-UA-Platform"))
        .platformVersion(request.getHeader("Sec-CH-UA-Platform-Version"))
        .mobile(Boolean.valueOf(request.getHeader("Sec-CH-UA-Mobile")))
        .model(request.getHeader("Sec-CH-UA-Model")));
```

Bots stay bots. Hints do not reclassify Googlebot as Chrome.

```text
mvn package
java -jar target/ua-fields-0.1.0.jar 'Mozilla/5.0 ...'
```

Zero runtime dependencies. JUnit is test-scoped only.

## Future versions

Families are recognized by stable tokens (`Chrome/`, `Edg/`, `Firefox/`, `OPR/`, `Version/` + `Safari`, bot product tokens). The version is whatever dotted number is attached. `Chrome/500.1.2.3` is Chrome 500.

`version_check` compares that number with the offline snapshot in [src/main/resources/catalog/versions.txt](src/main/resources/catalog/versions.txt). That file is the right runtime catalog: parsing does not call the network, and the compare rule, download URL, and end-of-life flag are not fields the vendors publish. The numbers in it are not typed by hand. They are a snapshot of the official release feeds, taken 2026-09-23:

| Browser | Latest in the snapshot | Source |
| --- | --- | --- |
| Chrome | 154.0.8037.58 (2026-09-22) | [Chrome version history](https://versionhistory.googleapis.com/v1/chrome/platforms/win/channels/stable/versions), Windows stable |
| Firefox | 156.0.1 (2026-09-22) | [firefox_versions.json](https://product-details.mozilla.org/1.0/firefox_versions.json) and [firefox_history_stability_releases.json](https://product-details.mozilla.org/1.0/firefox_history_stability_releases.json) |
| Edge | 153.0.4234.48 (2026-09-20) | [edgeupdates.microsoft.com/api/products](https://edgeupdates.microsoft.com/api/products), Stable / Windows. That API publishes the current build, not the archive |
| Opera | 136.0.6008.22 (2026-09-17) | [Opera desktop archive](https://get.geo.opera.com/pub/opera/desktop/) |
| Samsung Internet | 25.0.0.41 (2024-05-11) | [Samsung Internet for Android release notes](https://developer.samsung.com/browser/release-note/android-release-note.html). Samsung Browser for Windows is a different product and is not this row |
| Safari | 27 | [Safari release notes](https://developer.apple.com/documentation/safari-release-notes). Apple lists the versions and does not publish a release date there, so the date column is empty |
| Internet Explorer | 11.0 (2013-10-17), eol | [IE 11 lifecycle](https://learn.microsoft.com/en-us/lifecycle/products/internet-explorer-11). Support ended 2022-06-15 |

The matching history files are under [src/main/resources/catalog/history/](src/main/resources/catalog/history/). Chrome Windows stable is every version the history API still lists (54 through 154). Firefox is Mozilla's stability-release map. Opera is every desktop build in the public archive, including channel builds. `CatalogRefresh` rewrites the snapshot and those files from the same URLs.

| Situation | `is_up_to_date` | `is_ahead_of_catalog` |
| --- | --- | --- |
| Older than the catalog | false | false |
| Same as the catalog | true | false |
| Newer than the catalog | true | true |
| Product line marked `eol` (Internet Explorer) | false | false if it is not newer |

Chrome compares the first three components, except a reduced string such as `Chrome/154.0.0.0` (major followed only by zeros). Those are compared on the major only, because the UA no longer carries the real build. Firefox, Safari, Edge, Opera, and Samsung Internet compare the major. Edge and Opera still store the full official build; a reduced or same-major UA stays current.

`release_date` and `hours_released_ago` are for the detected build when that build is in the history. A reduced UA of the current major uses the latest build's date. A version the history does not contain, including one ahead of the catalog, leaves both null.

A browser this catalog has never heard of still returns `software_name` and `software_version`. `is_checkable` is false. A `Sec-CH-UA` brand that is not grease and not only `Chromium` is used as the name, including brands that do not exist yet.

Names this library does not special-case come from [uap-core `regexes.yaml`](https://github.com/ua-parser/uap-core/blob/master/regexes.yaml) (Apache 2.0, Copyright 2009 Google Inc.). The vendored copy is [src/main/resources/uap-core/regexes.yaml](src/main/resources/uap-core/regexes.yaml): 433 user-agent rules, 204 OS rules, and 633 device rules, byte-identical to upstream `73e7340` (2026-08-24). The runner follows uap-php replacement rules (`$1`..`$9`, empty groups become null). It matches the upstream `test_ua`, `test_os`, and `test_device` fixtures.

A specific family we already named is kept when it is the same product: Chrome stays Chrome rather than `Chrome Mobile`, Safari stays Safari rather than `Mobile Safari`, Silk stays Silk rather than `Amazon Silk`. A more specific uap-core family replaces a broad label. `Coc Coc` is not reported as Chrome, `IE Mobile` is not reported as Internet Explorer, and an iPad WebKit shell with no `Version/` token becomes `Mobile Safari UI/WKWebView`. Device rules supply model names we do not map ourselves, without replacing a code name this library already resolved (for example Galaxy S24 Ultra).

Replace the catalog without a code change:

```java
UserAgentParser.withCatalog(Path.of("/etc/ua-fields/versions.txt"));
```

File format, one product per line, 8 pipe-separated columns:

```text
id|latest|released|download|update|compareSegments|eol|aliases
chrome|154.0.8037.58|2026-09-22|https://www.google.com/chrome/|https://support.google.com/chrome/answer/95414|3|false|chrome,headless-chrome,chrome-webview,chromium
```

The bundled snapshot is dated 2026-09-23. Replace `versions.txt` when you want a different "latest". Leave `catalog/history/` in place if you still want per-build dates.

## Fields

Every parse returns these keys. Unknown values are null. Lists and maps are empty rather than null when the parser looked and found nothing (`capabilities`, `detected_addons`, `operating_system_frameworks`, `extra_info`, `extra_info_dict`).

| Field | Meaning |
| --- | --- |
| `user_agent` | Original string |
| `user_agent_sanitized` | GUIDs, UUIDs, and control characters removed |
| `simple_software_string` | `Chrome 64 on Mac OS X (Mavericks)` |
| `simple_sub_description_string` | Extra phrase, for example IE compatibility view |
| `simple_operating_platform_string` | Device line, when one was found |
| `software` / `software_name` / `software_name_code` | Label, name, and slug |
| `software_version` / `software_version_full` | Major, and every component |
| `software_type` | `browser`, `bot`, `application` |
| `software_sub_type` | `web-browser`, `in-app-browser`, `crawler`, `ai-crawler`, `ai-agent`, `analyser`, `security-analyser`, `site-monitor`, `feed-fetcher`, `tool`, `software-library`, `email-client` |
| `operating_system` / `operating_system_name` / `operating_system_name_code` | Display line, name, slug |
| `operating_system_version` / `operating_system_version_full` | Codename when one is real (`Mavericks`, `Catalina`, `Tahoe`), otherwise the number. Unknown future macOS majors are not given a made-up codename |
| `operating_system_flavour` / `operating_system_flavour_code` | Ubuntu, Debian, Fedora, and the other Linux flavours that put their name in the UA |
| `operating_system_frameworks` | .NET CLR versions found in the string |
| `operating_platform` / `operating_platform_code` / `operating_platform_code_name` / `operating_platform_vendor_name` | Model line. Vendor comes from the model prefix even when the marketing name is not in the small known-model map |
| `hardware_type` | `computer`, `mobile`, `large-screen`, `server` |
| `hardware_sub_type` | `phone`, `tablet`, `ebook-reader`, `music-player`, `wearable`, `tv`, `game-console` |
| `hardware_sub_sub_type` | `watch`, `vr` |
| `layout_engine_name` / `layout_engine_version` | Blink, WebKit, Gecko, Trident, EdgeHTML, Presto, Goanna. Blink's frozen AppleWebKit token is not reported as its version |
| `extra_info` | `"10"` software notes, `"20"` platform notes |
| `extra_info_dict` | Build id, Dalvik version, architecture, client-hint platform version |
| `capabilities` | Touch, Silk Accelerated, MIDP |
| `detected_addons` | Toolbar tokens such as Yahoo Toolbar |
| `is_abusive` | SQL injection, XSS, traversal, JNDI, or a known attack tool |
| `is_weird` / `is_weird_reason_code` | Empty, too long, or contradictory (`has_contradictory_info`) |
| `is_restricted` | Crude tokens stuffed into the UA |
| `is_spam` | SEO / backlink bait, or three or more URLs |
| `version_check` | `is_checkable`, `is_up_to_date`, `latest_version`, `download_url`, `update_url`, `release_date`, `hours_released_ago`, `is_ahead_of_catalog` |

`ParseOptions.allowServersToImpersonateDevices(true)` keeps the phone or tablet embedded in a Googlebot-style UA. The default reports `hardware_type=server`.

## What v0.1 does not claim

- It is not a database of tens of thousands of device models. Unknown model codes still return the code and, when the prefix is known, the vendor.
- Brave that sends only a Chrome token is reported as Chrome. Brave is recognized when the UA contains `Brave`.
- A frozen `Windows NT 10.0` UA is Windows 10 until a build `>= 22000` is present or `Sec-CH-UA-Platform-Version` has major `>= 13` (Windows 11).
- Per-build dates exist only for builds the official history file lists. Chrome's feed currently starts at 54. Edge's update API does not publish the old archive. Safari versions are listed without dates.
- There is no network call during parse. Currency of "latest" is the catalog snapshot you ship. `java -cp target/classes io.github.elanthirian.uafields.CatalogRefresh` rewrites it from the vendor feeds.

## Build

Java 17+.

```text
mvn test
```

Apache License 2.0. The WhichBrowser strings under `corpus/whichbrowser/` are not part of that license. See [corpus/whichbrowser/SOURCE.txt](corpus/whichbrowser/SOURCE.txt).

## Corpus report

[corpus/whichbrowser/useragents.txt](corpus/whichbrowser/useragents.txt) is the public WhichBrowser list (104,761 lines, fetched 2026-09-23).

[corpus/whichbrowser/results.jsonl.gz](corpus/whichbrowser/results.jsonl.gz) is the full row-level report. Each line is one JSON object with the input and every parse field:

```text
{"line":1,"status":"passed","user_agent":"...","parse":{...}}
```

`status` is `passed` when `software_name` is present, otherwise `failed`. This run, after the uap-core rules: 104,730 passed, 31 failed, 0 exceptions. `hours_released_ago` is computed at 2026-09-23T05:15:00Z.

```text
gzip -dc corpus/whichbrowser/results.jsonl.gz | sed -n '1p'
```

