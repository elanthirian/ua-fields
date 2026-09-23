package io.github.elanthirian.uafields;

import java.util.List;

final class SoftwareHit {
    final String name;
    final String nameCode;
    final Version version;
    final String type;
    final String subType;
    final String subDescription;
    final boolean needsVersion;

    SoftwareHit(String name, String nameCode, Version version, String type, String subType,
                String subDescription, boolean needsVersion) {
        this.name = name;
        this.nameCode = nameCode;
        this.version = version == null ? Version.empty() : version;
        this.type = type;
        this.subType = subType;
        this.subDescription = subDescription;
        this.needsVersion = needsVersion;
    }

    static SoftwareHit browser(String name, String version, String subDescription) {
        return new SoftwareHit(name, Text.slug(name), Version.parse(version), "browser", "web-browser",
                subDescription, true);
    }

    SoftwareHit renamed(String name, Version version, String type, String subType) {
        return new SoftwareHit(name, Text.slug(name), version, type, subType, subDescription, needsVersion);
    }
}

final class OsHit {
    final String name;
    final String nameCode;
    final String version;
    final List<String> versionFull;
    final String flavour;
    final String flavourCode;
    final String display;
    final List<String> notes;

    OsHit(String name, String version, List<String> versionFull, String flavour, String display, List<String> notes) {
        this.name = name;
        this.nameCode = Text.slug(name);
        this.version = version;
        this.versionFull = versionFull == null ? List.of() : versionFull;
        this.flavour = flavour;
        this.flavourCode = Text.slug(flavour);
        this.display = display;
        this.notes = notes == null ? List.of() : notes;
    }
}

final class HardwareHit {
    final String type;
    final String subType;
    final String subSubType;
    final String vendor;
    final String code;
    final String codeName;
    final String platform;

    HardwareHit(String type, String subType, String subSubType, String vendor, String code, String codeName,
                String platform) {
        this.type = type;
        this.subType = subType;
        this.subSubType = subSubType;
        this.vendor = vendor;
        this.code = code;
        this.codeName = codeName;
        this.platform = platform;
    }

    static HardwareHit server() {
        return new HardwareHit("server", null, null, null, null, null, null);
    }
}

final class EngineHit {
    final String name;
    final List<String> version;

    EngineHit(String name, List<String> version) {
        this.name = name;
        this.version = version == null ? List.of() : version;
    }
}
