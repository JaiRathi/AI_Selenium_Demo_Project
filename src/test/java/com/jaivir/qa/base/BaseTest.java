package com.jaivir.qa.base;

import com.aventstack.extentreports.ExtentTest;
import com.jaivir.qa.config.ConfigManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.ITestResult;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Base class for all test classes.
 * <ul>
 *   <li>Manages a ThreadLocal {@link WebDriver} so parallel tests are safe.</li>
 *   <li>Reads browser settings from {@link ConfigManager} (config.properties / .env / env vars).</li>
 *   <li>Exposes helpers to log steps into the current {@link ExtentTest}.</li>
 * </ul>
 */
public abstract class BaseTest {

    private static final ThreadLocal<WebDriver> DRIVER_THREAD = new ThreadLocal<>();

    // ── driver lifecycle ──────────────────────────────────────────────────────

    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        ChromeOptions options = new ChromeOptions();

        // All settings read from config.properties / .env / environment
        if (ConfigManager.getBoolean("HEADLESS", false))
            options.addArguments("--headless=new");

        int width  = ConfigManager.getInt("WINDOW_WIDTH",  1440);
        int height = ConfigManager.getInt("WINDOW_HEIGHT",  900);
        options.addArguments("--window-size=" + width + "," + height);
        options.addArguments("--disable-extensions");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);

        // Page-load and implicit wait from config
        int pageLoad = ConfigManager.getInt("PAGE_LOAD_TIMEOUT", 30);
        int implicit = ConfigManager.getInt("IMPLICIT_WAIT", 0);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pageLoad));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicit));

        DRIVER_THREAD.set(driver);
        log("Browser launched for: " + method.getName()
                + "  [headless=" + ConfigManager.getBoolean("HEADLESS", false)
                + "  base=" + ConfigManager.baseUrl() + "]");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        WebDriver driver = DRIVER_THREAD.get();
        if (driver != null) {
            if (result.getStatus() == ITestResult.FAILURE) {
                ExtentTestListener.captureScreenshot(result, driver);
            }
            try {
                driver.quit();
            } finally {
                DRIVER_THREAD.remove();
            }
        }
    }

    // ── public accessors ──────────────────────────────────────────────────────

    /** Returns the WebDriver for the current thread. */
    public WebDriver getDriver() {
        return DRIVER_THREAD.get();
    }

    /**
     * Logs a step message both to stdout and (if available) into the current
     * ExtentTest node managed by {@link ExtentTestListener}.
     */
    protected void log(String message) {
        System.out.println("[TEST] " + message);
        ExtentTest test = ExtentTestListener.CURRENT_TEST.get();
        if (test != null) test.info(message);
    }

    /**
     * Logs a passing checkpoint into ExtentTest.
     */
    protected void pass(String message) {
        System.out.println("[PASS] " + message);
        ExtentTest test = ExtentTestListener.CURRENT_TEST.get();
        if (test != null) test.pass(message);
    }
}
