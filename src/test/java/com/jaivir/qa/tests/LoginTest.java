package com.jaivir.qa.tests;

import com.jaivir.qa.ai.SelfHealingService;
import com.jaivir.qa.base.BaseTest;
import com.jaivir.qa.config.ConfigManager;
import com.jaivir.qa.pages.LoginPage;
import com.jaivir.qa.pages.SecureAreaPage;
import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Login feature tests – demonstrates POM + PageFactory + ExtentReports integration.
 *
 * <p>Application URLs and test credentials come from {@code config.properties},
 * with optional overrides via {@code .env} or system environment variables.
 *
 * <p>Self-healing is wired into LoginPage.clickLogin() via BasePage.clickWithHealing().
 * If the login-button locator breaks at runtime, the AI will suggest a replacement
 * and pause for human approval (APPROVE / REJECT) before the test continues.
 */
public class LoginTest extends BaseTest {

    @Test(description = "Valid credentials should redirect to the Secure Area")
    public void loginWithValidCredentials() {
        log("=== TC-001: Login with valid credentials ===");

        SecureAreaPage securePage = loginWithConfiguredCredentials();

        // Validate we landed on the secure area
        waitForUrlContains(ConfigManager.get("SECURE_PATH"));
        String heading = securePage.getHeadingText();
        Assert.assertTrue(heading.contains("Secure Area"),
                "Expected heading to contain 'Secure Area' but was: " + heading);

        pass("Successfully logged in. Heading: " + heading);
    }

    @Test(description = "Invalid credentials should show an error message")
    public void loginWithInvalidCredentials() {
        log("=== TC-002: Login with invalid credentials ===");

        LoginPage loginPage = new LoginPage(getDriver()).navigateTo();
        loginPage.enterUsername(ConfigManager.get("INVALID_LOGIN_USERNAME"))
                 .enterPassword(ConfigManager.get("INVALID_LOGIN_PASSWORD"));

        // Click login; self-healing active on the button locator
        loginPage.clickLogin();

        // Verify error is shown
        Assert.assertTrue(loginPage.isErrorDisplayed(),
                "Expected an error flash message to be shown");

        String error = loginPage.getErrorText();
        Assert.assertFalse(error.isBlank(), "Error message text should not be blank");
        pass("Error correctly displayed: " + error);
    }

    @Test(description = "User can log out from the secure area")
    public void logoutFromSecureArea() {
        log("=== TC-003: Logout ===");

        SecureAreaPage securePage = loginWithConfiguredCredentials();

        waitForUrlContains(ConfigManager.get("SECURE_PATH"));
        Assert.assertTrue(securePage.isLogoutButtonDisplayed(), "Logout button should be visible");

        LoginPage afterLogout = securePage.logout();
        String loginPath = ConfigManager.get("LOGIN_PATH");
        waitForUrlContains(loginPath);
        Assert.assertTrue(getDriver().getCurrentUrl().contains(
                        loginPath),
                "After logout, URL should contain the configured login path");

        pass("Logout successful. URL: " + getDriver().getCurrentUrl());
    }

    @Test(description = "Self-healing demo: intentionally broken locator is healed by AI")
    public void selfHealingBrokenLocatorDemo() {
        log("=== TC-004: Self-Healing Demo ===");
        log("Using an intentionally wrong locator. AI will suggest a fix; human must APPROVE.");

        // Navigate using ConfigManager — no hardcoded URL
        getDriver().get(ConfigManager.loginUrl());
        log("Navigated to: " + getDriver().getCurrentUrl());

        By wrongLocator = By.id("loginButton_BROKEN");
        try {
            new SelfHealingService().clickWithHealing(getDriver(), wrongLocator);
            // If healing succeeded, URL stays on /login after clicking the real submit button
            Assert.assertTrue(getDriver().getCurrentUrl().contains(
                            ConfigManager.get("LOGIN_PATH")),
                    "Expected to remain on login page after clicking healed button");
            pass("Self-healing succeeded. Healed locator was approved and used.");
        } catch (IllegalStateException e) {
            log("Healing was rejected or failed: " + e.getMessage());
            Assert.fail("Self-healing was not approved or failed: " + e.getMessage());
        }
    }

    private SecureAreaPage loginWithConfiguredCredentials() {
        return new LoginPage(getDriver())
                .navigateTo()
                .loginAs(ConfigManager.get("LOGIN_USERNAME"),
                        ConfigManager.get("LOGIN_PASSWORD"));
    }

    private void waitForUrlContains(String fragment) {
        new WebDriverWait(getDriver(),
                Duration.ofSeconds(ConfigManager.getInt("EXPLICIT_WAIT", 15)))
                .until(ExpectedConditions.urlContains(fragment));
    }
}
