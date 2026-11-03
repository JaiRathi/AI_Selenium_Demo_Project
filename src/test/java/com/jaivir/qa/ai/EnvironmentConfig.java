package com.jaivir.qa.ai;

import io.github.cdimascio.dotenv.Dotenv;

public final class EnvironmentConfig {
    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private EnvironmentConfig() {}

    public static String get(String name) {
        return resolve(name, System.getenv(name), DOTENV.get(name), null);
    }

    public static String get(String name, String fallback) {
        return resolve(name, System.getenv(name), DOTENV.get(name), fallback);
    }

    static String resolve(String name, String systemValue, String fileValue, String fallback) {
        if (systemValue != null && !systemValue.isBlank()) return systemValue;
        if (fileValue != null && !fileValue.isBlank()) return fileValue;
        return fallback;
    }
}
