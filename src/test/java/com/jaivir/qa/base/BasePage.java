package com.jaivir.qa.base;

import com.aventstack.extentreports.ExtentTest;
import com.jaivir.qa.ai.SelfHealingService;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Base class for all Page Objects.
 * <ul>
 *   <li>Calls {@code PageFactory.initElements(driver, this)} in the constructor.</li>
 *   <li>Wraps element lookups with {@link SelfHealingService} so broken locators
 *       are automatically healed, pending human approval.</li>
 *   <li>Provides common waits and a step-logging helper.</li>
 * </ul>
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WebDriverWait wait;
    private final SelfHealingService healer;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(15));
        this.healer = new SelfHealingService();
        PageFactory.initElements(driver, this);
    }

    // ── self-healing helpers ──────────────────────────────────────────────────

    /**
     * Finds an element using {@link SelfHealingService}.
     * If the original locator fails, the AI suggests a replacement, and the
     * human is asked to APPROVE or REJECT it before execution continues.
     */
    protected WebElement findWithHealing(By by) {
        return healer.findElementWithHealing(driver, by);
    }

    /**
     * Clicks an element using the self-healing strategy.
     */
    protected void clickWithHealing(By by) {
        healer.clickWithHealing(driver, by);
    }

    // ── standard waits ────────────────────────────────────────────────────────

    protected void waitForVisible(WebElement element) {
        wait.until(ExpectedConditions.visibilityOf(element));
    }

    protected void waitForClickable(WebElement element) {
        wait.until(ExpectedConditions.elementToBeClickable(element));
    }

    protected void waitForUrlContains(String fragment) {
        wait.until(ExpectedConditions.urlContains(fragment));
    }

    // ── logging ───────────────────────────────────────────────────────────────

    protected void log(String message) {
        System.out.println("[PAGE] " + message);
        ExtentTest test = ExtentTestListener.CURRENT_TEST.get();
        if (test != null) test.info(message);
    }
}
