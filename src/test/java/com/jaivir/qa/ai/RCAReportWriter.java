package com.jaivir.qa.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

public class RCAReportWriter {
    public Path write(FailureContext c, Map<String, String> analyses) throws Exception {
        Path dir = Path.of("target", "ai-rca");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().toString().replace(':', '-');
        Path report = dir.resolve(c.testName().replaceAll("[^A-Za-z0-9_-]", "_")
                + "-" + stamp + ".md");

        StringBuilder out = new StringBuilder("# AI Root Cause Analysis\n\n")
                .append("- Test: ").append(c.testName()).append('\n')
                .append("- URL: ").append(s(c.currentUrl())).append('\n')
                .append("- Page title: ").append(s(c.pageTitle())).append('\n')
                .append("- Exception: `").append(s(c.exception())).append("`\n")
                .append("- Screenshot: ").append(s(c.screenshotPath())).append("\n\n")
                .append("> AI analysis is advisory only. TestNG status is unchanged.\n");
        analyses.forEach((provider, analysis) -> out.append("\n## ")
                .append(provider).append("\n\n").append(analysis).append("\n"));
        out.append("\n## Stack trace\n\n```text\n")
                .append(s(c.stackTrace())).append("\n```\n");
        Files.writeString(report, out, StandardCharsets.UTF_8);
        return report;
    }

    private String s(String value) { return value == null ? "(not available)" : value; }
}
