package com.jaivir.qa.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.regex.Pattern;

public class TestScriptGenerator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AIClient aiClient;
    private final ApprovalService approvalService;
    private final Path outputRoot;

    public record GeneratedFiles(Path pageObject, Path testClass) {}

    public TestScriptGenerator() {
        this(new AIClient(), new ApprovalService(), Path.of("generated-tests"));
    }

    TestScriptGenerator(AIClient aiClient, ApprovalService approvalService, Path outputRoot) {
        this.aiClient = Objects.requireNonNull(aiClient);
        this.approvalService = Objects.requireNonNull(approvalService);
        this.outputRoot = Objects.requireNonNull(outputRoot);
    }

    public GeneratedFiles generate(String requirement, String packageName,
                                   String pageClassName, String testClassName)
            throws Exception {
        validateJavaName(pageClassName);
        validateJavaName(testClassName);
        if (packageName == null || !packageName.matches("[a-zA-Z_][\\w]*(\\.[a-zA-Z_][\\w]*)*")) {
            throw new IllegalArgumentException("Invalid Java package name");
        }
        if (requirement == null || requirement.isBlank())
            throw new IllegalArgumentException("Requirement must not be blank");

        String system = """
                You generate maintainable Java Selenium 4 + TestNG automation code.
                Use Page Object Model with Selenium PageFactory, constructor injection of WebDriver,
                PageFactory.initElements(driver, this), @FindBy locators, and explicit waits.
                Prefer stable locators (id, name, data-testid, accessible labels),
                and meaningful TestNG assertions. Never invent real application locators:
                use clearly marked TODO placeholders when the requirement has no DOM evidence.
                Do not include credentials. Return only JSON with exactly these string fields:
                {"pageObjectCode":"complete Java source","testCode":"complete Java source"}.
                Escape JSON strings correctly. Do not use Markdown fences.
                """;

        String user = """
                Generate two Java source files for this test requirement:
                REQUIREMENT:
                %s

                Package: %s
                Page Object class: %s
                TestNG class: %s

                Constraints:
                - Selenium WebDriver and TestNG only.
                - The page object uses @FindBy fields and calls PageFactory.initElements(driver, this)
                  in its WebDriver constructor.
                - Use WebDriverWait and ExpectedConditions for waits.
                - Test class extends no custom base class; include a minimal @BeforeMethod
                  ChromeDriver setup and @AfterMethod quit.
                - Include clear TODO comments where URL, locators, or test data are unknown.
                - Include assertions that match the requirement, without claiming unknown outcomes.
                - Include package declaration in both source files.
                """.formatted(requirement, packageName, pageClassName, testClassName);

        String raw = aiClient.chat(AIClient.primaryProvider(), system, user);
        JsonNode result = JSON.readTree(extractJson(raw));
        String pageCode = result.path("pageObjectCode").asText("");
        String testCode = result.path("testCode").asText("");
        if (pageCode.isBlank() || testCode.isBlank())
            throw new IllegalStateException("AI did not return both Java source files");

        Path packageDir = outputRoot.resolve(packageName.replace('.', '/'));
        Path pagePath = packageDir.resolve(pageClassName + ".java");
        Path testPath = packageDir.resolve(testClassName + ".java");
        if (Files.exists(pagePath) || Files.exists(testPath)) {
            throw new IllegalStateException(
                    "Refusing to overwrite generated source; move or remove the existing file first: "
                            + pagePath.toAbsolutePath() + " or " + testPath.toAbsolutePath());
        }

        String reviewBundle = "Page Object target: " + pagePath.toAbsolutePath()
                + "\nTest class target: " + testPath.toAbsolutePath()
                + "\n\nPAGE OBJECT\n\n" + pageCode
                + "\n\nTEST CLASS\n\n" + testCode;
        boolean approved = approvalService.approve(
                "Generated Selenium code", pageClassName + " + " + testClassName,
                reviewBundle);
        if (!approved) {
            throw new IllegalStateException(
                    "Human review rejected generated code; files were not written.");
        }

        Files.createDirectories(packageDir);
        boolean pageWritten = false;
        try {
            Files.writeString(pagePath, pageCode, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            pageWritten = true;
            Files.writeString(testPath, testCode, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.io.IOException | RuntimeException writeFailure) {
            if (pageWritten) {
                try {
                    Files.deleteIfExists(pagePath);
                } catch (java.io.IOException | RuntimeException cleanupFailure) {
                    writeFailure.addSuppressed(cleanupFailure);
                }
            }
            throw writeFailure;
        }
        return new GeneratedFiles(pagePath, testPath);
    }

    private void validateJavaName(String name) {
        if (name == null || !Pattern.matches("[A-Z][A-Za-z0-9_]*", name))
            throw new IllegalArgumentException("Class name must be a valid UpperCamelCase Java identifier");
    }

    private String extractJson(String response) {
        String value = response.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("AI response did not contain JSON");
        return value.substring(start, end + 1);
    }
}
