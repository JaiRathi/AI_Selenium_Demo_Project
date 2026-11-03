package com.jaivir.qa.ai;

public enum AIProvider {
    GROQ, OLLAMA, MISTRAL;

    public static AIProvider from(String value) {
        return AIProvider.valueOf(value.trim().toUpperCase());
    }
}
