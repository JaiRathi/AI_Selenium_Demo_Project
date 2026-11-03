package com.jaivir.qa.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Append-only local audit trail for human approval decisions. */
public final class AuditLogger {
    private AuditLogger() {}

    public static synchronized void log(String itemType, String decision, String summary) {
        try {
            Path dir = Path.of("target", "ai-hitl");
            Files.createDirectories(dir);
            String safe = (summary == null ? "" : summary).replace('\n', ' ')
                    .replace('\r', ' ').replace('|', '/');
            String line = Instant.now() + " | " + itemType + " | " + decision
                    + " | " + safe + System.lineSeparator();
            Files.writeString(dir.resolve("approval-audit.log"), line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("[HITL] Could not write approval audit log", e);
        }
    }
}
