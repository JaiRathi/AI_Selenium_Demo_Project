# AI Selenium Framework V2 — Groq + Ollama + Mistral

Java 17+, Selenium 4, TestNG, Maven, Jackson.

V2 adds:
- AI failure analysis (RCA) from a TestNG listener.
- Guarded self-healing locator suggestions.
- Requirement-to-Selenium Java generation (Page Object + TestNG test).
- Provider selection across Groq, Ollama, and Mistral.

## Important safety design

- AI is advisory. A healed locator is used only after Selenium confirms it matches exactly one visible, enabled element.
- An approved healed locator is saved in `.ai-selenium/approved-locators.json` and reused for the same page and original locator on later runs. Saved locators are revalidated and require human approval before use each time. The original test source is not rewritten.
- The saved mapping uses the page's scheme, host, and path (not URL query parameters); opaque URLs are stored as hashes. Remove `.ai-selenium/approved-locators.json` to clear approved mappings.
- Generated Java is saved for review only. It is not compiled or executed automatically.
- Do not send production DOM, credentials, session tokens, customer data, or other sensitive content to cloud providers. Add redaction before enabling cloud AI in a real environment.
- The `.env` file is not attached to AI requests. Cloud API keys are used for HTTP authentication, while AI prompts may include runtime URLs, exceptions, stack traces, and DOM snapshots; screenshots remain local and only their paths are included. Review/redact runtime data before using a cloud provider.

## Prerequisites

- JDK 17+
- Maven 3.9+
- Google Chrome
- Ollama for local inference (optional)
- Groq/Mistral API keys for cloud inference (optional)

Selenium Manager can resolve ChromeDriver.

## Configure environment (macOS/Linux)

Keep provider credentials in environment variables, never in source files. Rotate any credentials
that were previously stored in source code.

Non-secret framework defaults and this demo's test fixtures are in
`src/test/resources/config.properties`. Put sensitive overrides, such as real login credentials
or AI provider keys, in `.env` or CI secrets. Environment variables override `.env`, which
overrides the properties file.

The framework loads `.env` from the project directory automatically. Copy the template, then add
your own keys:

```bash
cp -n .env.example .env
# Edit .env and fill in GROQ_API_KEY or MISTRAL_API_KEY.
```

`.env` is ignored by Git. Process environment variables override values in `.env`.

```bash
export GROQ_API_KEY="your-groq-key"
export GROQ_MODEL="llama-3.3-70b-versatile"
export MISTRAL_API_KEY="your-mistral-key"
export MISTRAL_MODEL="mistral-small-latest"
export OLLAMA_BASE_URL="http://localhost:11434"
export OLLAMA_MODEL="gemma3:1b"

# Providers used for RCA analysis:
export AI_PROVIDERS="groq,ollama,mistral"
# Primary provider used for locator healing and script generation:
export AI_PRIMARY_PROVIDER="groq"
```

PowerShell equivalent:

```powershell
$env:GROQ_API_KEY="your-groq-key"
$env:GROQ_MODEL="llama-3.3-70b-versatile"
$env:MISTRAL_API_KEY="your-mistral-key"
$env:MISTRAL_MODEL="mistral-small-latest"
$env:OLLAMA_BASE_URL="http://localhost:11434"
$env:OLLAMA_MODEL="gemma3:1b"
$env:AI_PROVIDERS="groq,ollama,mistral"
$env:AI_PRIMARY_PROVIDER="groq"
```

## Start Ollama

```bash
ollama pull gemma3:1b
ollama serve
curl http://localhost:11434/api/tags
```

If Ollama is already running in the background, do not start a second server.

## Run the demo suite

```bash
mvn clean test
```

The suite has:
- `SelfHealingDemoTest`: starts with an intentionally wrong locator and asks the configured AI provider for a replacement. It can pass only if the provider returns a valid locator matching one visible, enabled element on the demo page and a human approves it.
- `SelfHealingBrowserIntegrationTest`: exercises locator validation, the approval-before-click order, and the actual click in Chrome using a local HTML page and a deterministic AI-response stub. It does not require an AI service.
- `TestScriptGeneratorTest`: verifies script output is shown for approval before files are written and that rejection does not create files.
- `FailureRcaDemoTest`: intentionally fails so the TestNG listener creates an RCA report. Maven is expected to exit non-zero because this is a deliberate failure demo.

Reports:
- `target/ai-rca/*.md`
- `target/ai-rca/screenshots/*.png`
- `target/surefire-reports/`

For a real project, remove or disable the intentionally failing demo test.

## Generate Selenium code from a requirement

Set the requirement and run the generator:

```bash
export TEST_REQUIREMENT="Verify that a registered user can log in with valid credentials and is redirected to the dashboard."
mvn -q -Dexec.mainClass=com.jaivir.qa.ai.GenerateTestScriptMain -Dexec.classpathScope=test test-compile exec:java
```

Generated source files are saved under `generated-tests/` for review. The generator requests a Page Object class using Selenium PageFactory (`@FindBy` and `PageFactory.initElements`) and a TestNG test class. Review package names, locators, test data, assertions, and environment-specific details before copying into `src/test/java`.

## Choose a different primary provider

```bash
export AI_PRIMARY_PROVIDER="ollama"
# or
export AI_PRIMARY_PROVIDER="mistral"
```

## Project structure

```text
src/test/java/com/jaivir/qa/
  ai/
    AIClient.java
    AIProvider.java
    RCAService.java
    SelfHealingService.java
    TestScriptGenerator.java
    GenerateTestScriptMain.java
  framework/
    BaseTest.java
    WebDriverProvider.java
    AIFailureListener.java
  tests/
    SelfHealingDemoTest.java
    FailureRcaDemoTest.java
src/test/resources/testng.xml
```


## Human-in-the-Loop (HITL) — V3

This version adds explicit human approval gates. AI suggestions are **not silently trusted**.

### Approval points

1. **Self-healing locator:** AI proposes a replacement; Selenium first checks it matches exactly one visible and enabled element; a human must approve before the retry is allowed. The approved locator mapping is saved for later runs, revalidated, and shown for approval again before reuse.
2. **Generated test code:** AI generates Page Object + TestNG source; a human reviews the complete source and approves before files are written under `generated-tests/`.
3. **RCA review:** AI produces an RCA Markdown report; a human can review and record an approval/rejection decision using `RCAReviewMain`.

### HITL modes

Default is `interactive`. The framework prompts for approval using terminal input; only the exact
answer `APPROVE` permits the proposed action. `REJECT`, end-of-input, or an input error does not
permit it. There is no automatic-approval mode.

```bash
# Default: prompt a human; type APPROVE or REJECT
export AI_HITL_MODE=interactive

# Reject all approval requests
export AI_HITL_MODE=reject
```

PowerShell:

```powershell
$env:AI_HITL_MODE="interactive"
```

**Security recommendation:** Keep `interactive` for local development and require an equivalent protected approval workflow in CI/CD. The framework intentionally does not support bypassing human approval. If standard input is unavailable, the request fails closed.

### Audit trail

Decisions are appended to:

```text
target/ai-hitl/approval-audit.log
```

RCA human review:

```bash
mvn -q -Dexec.mainClass=com.jaivir.qa.ai.RCAReviewMain `
  -Dexec.args="target/ai-rca/your-report.md" `
  -Dexec.classpathScope=test test-compile exec:java
```

On macOS/Linux, replace the PowerShell line continuation backticks with `\\` line continuations or run the command on one line. The decision is written beside the RCA report as `.human-review.txt`.

### Demo execution notes

`mvn test` runs the non-interactive login checks. The self-healing demo requires human
approval and is intentionally excluded from the default suite. A deterministic browser test
is included in the default suite: it uses a local page, a stubbed AI suggestion, and an
injected approval response, so it requires neither an AI provider nor operator input.

![Animated self-healing UI test flow](docs/self-healing-ui-flow.gif)

Open the [interactive animated diagram](docs/self-healing-ui-flow.html) in a browser.

To run only the deterministic self-healing browser test:

```bash
HEADLESS=true mvn -Dsurefire.suiteXmlFiles= -Dtest=SelfHealingBrowserTest test
```

To run the live AI + human-approval demo from a real terminal:

```bash
mvn -DforkCount=0 -Dsurefire.suiteXmlFiles= -Dtest=LoginTest#selfHealingBrokenLocatorDemo test
```

Run this command from a terminal. Surefire's default forked test JVM may not receive
interactive terminal input, leaving the human approval prompt waiting after you type a response.
`-DforkCount=0` runs the test in Maven's JVM so the `APPROVE` or `REJECT` response is received.

To run only the RCA demo:

```bash
mvn -Dsurefire.suiteXmlFiles= -Dtest=FailureRcaDemoTest test
```

To run the local approval and script-generation regression tests:

```bash
HEADLESS=true mvn -Dsurefire.suiteXmlFiles= -Dtest=ApprovalServiceTest,SelfHealingServiceTest,SelfHealingBrowserIntegrationTest,TestScriptGeneratorTest test
```

If running TestNG through an IDE or CI job, provide an interactive standard input stream; otherwise approval fails closed. A generated file is never overwritten, and its exact destination is included in the approval evidence.

### Updated project structure

```text
src/test/java/com/jaivir/qa/ai/
  AIClient.java
  AIProvider.java
  ApprovalService.java
  AuditLogger.java
  FailureContext.java
  GenerateTestScriptMain.java
  RCAReviewMain.java
  RCAReportWriter.java
  RCAService.java
  SelfHealingService.java
  TestScriptGenerator.java
src/test/java/com/jaivir/qa/framework/
  AIFailureListener.java
  BaseTest.java
  WebDriverProvider.java
src/test/java/com/jaivir/qa/tests/
  SelfHealingDemoTest.java
  FailureRcaDemoTest.java
```
