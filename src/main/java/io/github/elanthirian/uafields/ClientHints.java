package io.github.elanthirian.uafields;

/**
 * Optional User-Agent Client Hints. When present they override a reduced user agent.
 * Pass the header values, not a pre-parsed structure.
 */
public final class ClientHints {
    private final String secChUa;
    private final String platform;
    private final String platformVersion;
    private final Boolean mobile;
    private final String model;

    private ClientHints(String secChUa, String platform, String platformVersion, Boolean mobile, String model) {
        this.secChUa = secChUa;
        this.platform = platform;
        this.platformVersion = platformVersion;
        this.mobile = mobile;
        this.model = model;
    }

    public static ClientHints empty() {
        return new ClientHints(null, null, null, null, null);
    }

    public ClientHints secChUa(String value) {
        return new ClientHints(value, platform, platformVersion, mobile, model);
    }

    public ClientHints platform(String value) {
        return new ClientHints(secChUa, value, platformVersion, mobile, model);
    }

    public ClientHints platformVersion(String value) {
        return new ClientHints(secChUa, platform, value, mobile, model);
    }

    public ClientHints mobile(Boolean value) {
        return new ClientHints(secChUa, platform, platformVersion, value, model);
    }

    public ClientHints model(String value) {
        return new ClientHints(secChUa, platform, platformVersion, mobile, value);
    }

    public String secChUa() {
        return secChUa;
    }

    public String platform() {
        return platform;
    }

    public String platformVersion() {
        return platformVersion;
    }

    public Boolean mobile() {
        return mobile;
    }

    public String model() {
        return model;
    }
}
