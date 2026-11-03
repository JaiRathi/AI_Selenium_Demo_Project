package com.jaivir.qa.ai;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Human-in-the-loop approval gate.
 *
 * <p>Modes (set via {@code AI_HITL_MODE} in .env or environment):
 * <ul>
 *   <li>{@code interactive} (default) – prompts the operator on the terminal.</li>
 *   <li>{@code reject} – always rejects; safe for CI pipelines.</li>
 * </ul>
 *
 * <p><b>stdin note</b>: Maven Surefire forks a child JVM by default which
 * disconnects {@code System.in}. The recommended fix is {@code <forkCount>0</forkCount>}
 * in pom.xml (already set). As an extra safety net this class also tries to open
 * {@code /dev/tty} (the raw controlling terminal) when {@code System.in} appears
 * to be closed (returns {@code null} on the very first read).
 */
public class ApprovalService {

    private final String mode;
    private final BufferedReader injectedInput; // null ⟹ use openTerminalReader()
    private final PrintStream output;

    public ApprovalService() {
        this(EnvironmentConfig.get("AI_HITL_MODE", "interactive"), null, System.out);
    }

    /** Package-private constructor for unit tests – injects a custom reader. */
    ApprovalService(String mode, BufferedReader input, PrintStream output) {
        this.mode = Objects.requireNonNull(mode).trim().toLowerCase(Locale.ROOT);
        this.injectedInput = input;           // may be null when called from public constructor
        this.output = Objects.requireNonNull(output);
    }

    public boolean approve(String itemType, String summary, String evidence) {
        if ("reject".equals(mode)) {
            output.println("[HITL] Rejected by AI_HITL_MODE=reject.");
            AuditLogger.log(itemType, "REJECTED_BY_POLICY", summary);
            return false;
        }
        if (!"interactive".equals(mode)) {
            throw new IllegalArgumentException("AI_HITL_MODE must be 'interactive' or 'reject'");
        }

        output.println("\n========== HUMAN APPROVAL REQUIRED ==========");
        output.println("Item    : " + itemType);
        output.println("Proposal: " + summary);
        output.println("Evidence:\n" + truncate(evidence, 5000));
        output.println("=============================================");
        output.print("Approve? Type APPROVE to continue, REJECT to stop: ");
        output.flush();

        String answer = readAnswer();

        boolean approved = answer != null && "APPROVE".equals(answer.trim());
        AuditLogger.log(itemType, approved ? "APPROVED" : "REJECTED", summary);

        if (answer == null) {
            output.println("\n[HITL] stdin returned null (no terminal attached). Failing closed.");
            output.println("[HITL] Tip: ensure pom.xml has <forkCount>0</forkCount> in surefire.");
        } else {
            output.println(approved ? "[HITL] Decision: APPROVED ✔" : "[HITL] Decision: REJECTED ✘");
        }
        return approved;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Reads one line of input.
     * Priority: injected reader → System.in → /dev/tty (Unix fallback)
     */
    private String readAnswer() {
        // 1. injected reader (unit-test path)
        if (injectedInput != null) {
            return readLine(injectedInput);
        }

        // 2. System.in — works when forkCount=0 (normal interactive run)
        BufferedReader sysIn = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line = readLine(sysIn);
        if (line != null) return line;

        // 3. /dev/tty fallback — lets the operator type even if System.in was
        //    closed by a forked Maven Surefire JVM (Unix/macOS only).
        output.println("\n[HITL] System.in is closed. Trying /dev/tty ...");
        output.flush();
        try (BufferedReader tty = new BufferedReader(new FileReader("/dev/tty"))) {
            output.print("Approve? Type APPROVE to continue, REJECT to stop: ");
            output.flush();
            return readLine(tty);
        } catch (IOException e) {
            output.println("[HITL] /dev/tty unavailable: " + e.getMessage());
            return null;
        }
    }

    private static String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return "(none)";
        return value.length() <= max ? value : value.substring(0, max) + "\n...[truncated]";
    }
}
