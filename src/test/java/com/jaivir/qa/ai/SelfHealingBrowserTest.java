package com.jaivir.qa.ai;

import com.jaivir.qa.base.BaseTest;
import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

public class SelfHealingBrowserTest extends BaseTest {

    @Test(description = "A broken locator is validated, approved, and used to click")
    public void healsBrokenLocatorAndClicksButton() throws Exception {
        String html = """
                <!doctype html>
                <html>
                  <body>
                    <button id="login-submit"
                            onclick="this.textContent='Clicked'">Login</button>
                  </body>
                </html>
                """;
        String page = "data:text/html;base64," + Base64.getEncoder()
                .encodeToString(html.getBytes(StandardCharsets.UTF_8));
        getDriver().get(page);

        AtomicInteger suggestionCount = new AtomicInteger();
        AIClient deterministicAi = new AIClient() {
            @Override
            public String chat(AIProvider provider, String systemPrompt, String userPrompt) {
                suggestionCount.incrementAndGet();
                return """
                        {"strategy":"id","value":"login-submit",
                         "reason":"The button id is present in the page DOM"}
                        """;
            }
        };
        ApprovalService testApproval = new ApprovalService(
                "interactive",
                new BufferedReader(new StringReader("APPROVE")),
                System.out);
        Path locatorDirectory = Files.createTempDirectory("self-healing-test-");
        Path locatorFile = locatorDirectory.resolve("approved-locators.json");

        try {
            SelfHealingService healer = new SelfHealingService(
                    deterministicAi, testApproval, locatorFile);
            healer.clickWithHealing(getDriver(), By.id("missing-login-button"));

            Assert.assertEquals(suggestionCount.get(), 1,
                    "AI should be asked once after the original locator fails");
            Assert.assertEquals(
                    getDriver().findElement(By.id("login-submit")).getText(),
                    "Clicked",
                    "The approved replacement should click the intended button");
            Assert.assertTrue(Files.exists(locatorFile),
                    "The approved replacement should be saved for later validation");
        } finally {
            Files.deleteIfExists(locatorFile);
            Files.deleteIfExists(locatorDirectory);
        }
    }
}
