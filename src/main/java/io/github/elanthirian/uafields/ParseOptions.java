package io.github.elanthirian.uafields;

public final class ParseOptions {
    private final boolean sanitize;
    private final boolean allowServersToImpersonateDevices;

    private ParseOptions(boolean sanitize, boolean allowServersToImpersonateDevices) {
        this.sanitize = sanitize;
        this.allowServersToImpersonateDevices = allowServersToImpersonateDevices;
    }

    public static ParseOptions defaults() {
        return new ParseOptions(true, false);
    }

    public ParseOptions sanitize(boolean value) {
        return new ParseOptions(value, allowServersToImpersonateDevices);
    }

    /** When true, a bot UA that also names a phone is reported as that phone instead of a server. */
    public ParseOptions allowServersToImpersonateDevices(boolean value) {
        return new ParseOptions(sanitize, value);
    }

    public boolean sanitize() {
        return sanitize;
    }

    public boolean allowServersToImpersonateDevices() {
        return allowServersToImpersonateDevices;
    }
}
