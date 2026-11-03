package com.jaivir.qa.pages;

import com.jaivir.qa.base.BasePage;
import com.jaivir.qa.config.ConfigManager;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Page Object for the Login page.
 * The URL is read from {@code config.properties} → {@code LOGIN_PATH} (default: /login)
 * combined with {@code BASE_URL}. Override either key in your {@code .env} or as a
 * system property to point at a different environment without touching source code.
 *
 * <p>All @FindBy fields are initialised by PageFactory (called in BasePage constructor).
 * The self-healing fallback in {@link BasePage#findWithHealing(By)} is used for
 * any locator that might break; standard @FindBy elements are used for stable ones.
 */
public class LoginPage extends BasePage {

    /* ── Stable locators (unlikely to change) ─────────────────────────────── */
    @FindBy(id = "username")
    private WebElement usernameField;

    @FindBy(id = "password")
    private WebElement passwordField;

    /* ── Potentially fragile locator – healed automatically if it breaks ──── */
    // The correct CSS selector for the login button on the-internet.herokuapp.com
    // is "button[type='submit']".  We intentionally store the By here so BasePage
    // can apply self-healing when this locator stops matching.
    private static final By LOGIN_BUTTON_BY = By.cssSelector("button[type='submit1']");

    @FindBy(css = ".flash.success")
    private WebElement successMessage;

    @FindBy(css = ".flash.error")
    private WebElement errorMessage;

    public LoginPage(WebDriver driver) {
        super(driver);
        log("LoginPage initialised via PageFactory");
    }

    // ── page actions ──────────────────────────────────────────────────────────

    public LoginPage navigateTo() {
        String url = ConfigManager.loginUrl();
        driver.get(url);
        log("Navigated to login page: " + driver.getCurrentUrl());
        return this;
    }

    public LoginPage enterUsername(String username) {
        waitForVisible(usernameField);
        usernameField.clear();
        usernameField.sendKeys(username);
        log("Entered username: " + username);
        return this;
    }

    public LoginPage enterPassword(String password) {
        waitForVisible(passwordField);
        passwordField.clear();
        passwordField.sendKeys(password);
        log("Entered password");
        return this;
    }

    /**
     * Clicks the login button using the self-healing strategy.
     * If the locator {@code LOGIN_BUTTON_BY} fails, the AI proposes a replacement
     * and a human must APPROVE it before the click proceeds.
     */
    public SecureAreaPage clickLogin() {
        log("Clicking login button (self-healing enabled)");
        clickWithHealing(LOGIN_BUTTON_BY);
        return new SecureAreaPage(driver);
    }

    /**
     * Performs a full login flow.
     */
    public SecureAreaPage loginAs(String username, String password) {
        return enterUsername(username)
                .enterPassword(password)
                .clickLogin();
    }

    public boolean isErrorDisplayed() {
        try {
            wait.until(ExpectedConditions.visibilityOf(errorMessage));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public String getErrorText() {
        waitForVisible(errorMessage);
        return errorMessage.getText();
    }
}
