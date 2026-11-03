package com.jaivir.qa.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class AIClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    public String chat(AIProvider provider, String systemPrompt, String userPrompt)
            throws Exception {
        boolean local = provider == AIProvider.OLLAMA;
        String endpoint;
        String model;
        String key = null;

        switch (provider) {
            case GROQ -> {
                endpoint = "https://api.groq.com/openai/v1/chat/completions";
                model = env("GROQ_MODEL", "openai/gpt-oss-120b");
                key = requiredEnv("GROQ_API_KEY");
            }
            case MISTRAL -> {
                endpoint = "https://api.mistral.ai/v1/chat/completions";
                model = env("MISTRAL_MODEL", "mistral-small-latest");
                key = requiredEnv("MISTRAL_API_KEY");
            }
            case OLLAMA -> {
                endpoint = env("OLLAMA_BASE_URL", "http://localhost:11434")
                        .replaceAll("/+$", "") + "/api/chat";
                model = env("OLLAMA_MODEL", "gemma3:1b");
            }
            default -> throw new IllegalArgumentException("Unsupported provider " + provider);
        }

        Map<String, Object> payload;
        if (local) {
            payload = Map.of(
                    "model", model,
                    "stream", false,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "options", Map.of("temperature", 0.1));
        } else {
            payload = Map.of(
                    "model", model,
                    "temperature", 0.1,
                    "max_tokens", 3000,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)));
        }

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(150))
                .header("Content-Type", "application/json");
        if (!local) request.header("Authorization", "Bearer " + key);

        HttpResponse<String> response = HTTP.send(
                request.POST(HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(payload))).build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(provider + " HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 1500));
        }

        JsonNode root = JSON.readTree(response.body());
        String answer = local
                ? root.path("message").path("content").asText("")
                : root.path("choices").path(0).path("message").path("content").asText("");
        if (answer.isBlank()) throw new IllegalStateException(provider + " returned empty content");
        return answer;
    }

    public static AIProvider primaryProvider() {
        return AIProvider.from(env("AI_PRIMARY_PROVIDER", "groq"));
    }

    public static String[] configuredProviders() {
        return env("AI_PROVIDERS", "groq,ollama,mistral").split(",");
    }

    private static String requiredEnv(String name) {
        String value = EnvironmentConfig.get(name);
        if (value == null || value.isBlank())
            throw new IllegalStateException(
                    "Missing " + name + ". Set it in the process environment or project .env file.");
        return value;
    }

    private static String env(String name, String fallback) {
        return EnvironmentConfig.get(name, fallback);
    }

    private static String truncate(String value, int limit) {
        return value == null || value.length() <= limit
                ? value : value.substring(0, limit) + "...[truncated]";
    }
}
