package io.github.elanthirian.uafields.cli;

import io.github.elanthirian.uafields.JsonWriter;
import io.github.elanthirian.uafields.UserAgentParser;

import java.nio.charset.StandardCharsets;

/** Reads one user agent from the arguments, or from stdin when there are none. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String userAgent;
        if (args.length == 0) {
            userAgent = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } else {
            userAgent = String.join(" ", args);
        }
        var result = UserAgentParser.create().parse(userAgent);
        System.out.println(JsonWriter.write(result.fields()));
    }
}
