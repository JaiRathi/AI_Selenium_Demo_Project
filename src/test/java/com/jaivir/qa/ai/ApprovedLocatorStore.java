package com.jaivir.qa.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ApprovedLocatorStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<ApprovedLocator>> LOCATOR_LIST =
            new TypeReference<>() {};

    record ApprovedLocator(String pageUrl, String originalLocator, String strategy,
                           String value, String reason) {}

    private final Path file;

    ApprovedLocatorStore(Path file) {
        this.file = file;
    }

    synchronized Optional<ApprovedLocator> find(String pageUrl, String originalLocator) {
        return readAll().stream()
                .filter(entry -> entry.pageUrl().equals(pageUrl)
                        && entry.originalLocator().equals(originalLocator))
                .findFirst();
    }

    synchronized void save(ApprovedLocator approvedLocator) {
        List<ApprovedLocator> entries = new ArrayList<>(readAll());
        entries.removeIf(entry -> entry.pageUrl().equals(approvedLocator.pageUrl())
                && entry.originalLocator().equals(approvedLocator.originalLocator()));
        entries.add(approvedLocator);

        Path parent = file.toAbsolutePath().getParent();
        Path temporaryFile = null;
        try {
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, "approved-locators-", ".tmp");
            Files.writeString(temporaryFile, JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(entries), StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save approved locator mapping to "
                    + file.toAbsolutePath(), e);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException e) {
                    System.err.println("[AI-Healer] Could not remove temporary locator mapping: "
                            + e.getMessage());
                }
            }
        }
    }

    private List<ApprovedLocator> readAll() {
        if (!Files.exists(file)) return List.of();
        try {
            return JSON.readValue(Files.readString(file), LOCATOR_LIST);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read approved locator mappings from "
                    + file.toAbsolutePath(), e);
        }
    }
}
