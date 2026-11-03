package com.jaivir.qa.config;

import com.jaivir.qa.ai.EnvironmentConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration manager for the AI Selenium Framework.
 *
 * <h3>Value resolution order (highest → lowest priority)</h3>
 * <ol>
 *   <li>System environment variable (set in shell / CI pipeline)</li>
 *   <li>{@code .env} file in project root (loaded by {@link EnvironmentConfig})</li>
 *   <li>{@code src/test/resources/config.properties} (default values)</li>
 * </ol>
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * String baseUrl  = ConfigManager.get("BASE_URL");
 * String username = ConfigManager.get("LOGIN_USERNAME");
 * int    timeout  = ConfigManager.getInt("EXPLICIT_WAIT", 15);
 * }</pre>
 */
public final class ConfigManager {

    private static final String CONFIG_FILE = "config.properties";
    private static final Properties PROPS = load();

    private ConfigManager() {}

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Returns the value for {@code key}, resolving env-var → .env → config.properties.
     * Returns {@code null} if not found anywhere.
     */
    public static String get(String key) {
        // 1. System env / .env file (handled by EnvironmentConfig)
        String envValue = EnvironmentConfig.get(key);
        if (envValue != null && !envValue.isBlank()) return envValue.trim();

        // 2. config.properties
        String propValue = PROPS.getProperty(key);
        return propValue != null ? propValue.trim() : null;
    }

    /**
     * Returns the value for {@code key}, falling back to {@code defaultValue}
     * if the key is not set in any source.
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the integer value for {@code key}.
     *
     * @throws NumberFormatException if the value exists but cannot be parsed as int.
     */
    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        return Integer.parseInt(value);
    }

    /**
     * Returns the boolean value for {@code key}.
     * {@code "true"} (case-insensitive) → {@code true}; anything else → {@code false}.
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    // ── convenience URL builders ──────────────────────────────────────────────

    /**
     * Returns {@code BASE_URL} with any trailing slash stripped.
     * Example: {@code "https://the-internet.herokuapp.com"}
     */
    public static String baseUrl() {
        return get("BASE_URL", "https://the-internet.herokuapp.com")
                .replaceAll("/+$", "");
    }

    /**
     * Appends {@code path} to {@link #baseUrl()}, ensuring exactly one {@code /}
     * between them.
     * Example: {@code url("/login")} → {@code "https://the-internet.herokuapp.com/login"}
     */
    public static String url(String path) {
        String base = baseUrl();
        String p = path.startsWith("/") ? path : "/" + path;
        return base + p;
    }

    /**
     * Shortcut for {@code url(get("LOGIN_PATH", "/login"))}.
     */
    public static String loginUrl() {
        return url(get("LOGIN_PATH", "/login"));
    }

    /**
     * Shortcut for {@code url(get("SECURE_PATH", "/secure"))}.
     */
    public static String secureUrl() {
        return url(get("SECURE_PATH", "/secure"));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream is = ConfigManager.class
                .getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                System.err.println("[ConfigManager] WARNING: '" + CONFIG_FILE
                        + "' not found on classpath — using env / .env values only.");
            } else {
                p.load(is);
                System.out.println("[ConfigManager] Loaded '" + CONFIG_FILE + "'");
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "[ConfigManager] Failed to load '" + CONFIG_FILE + "'", e);
        }
        return p;
    }
}

