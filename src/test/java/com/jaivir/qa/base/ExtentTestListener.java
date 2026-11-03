package com.jaivir.qa.base;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import com.jaivir.qa.ai.FailureContext;
import com.jaivir.qa.ai.RCAReportWriter;
import com.jaivir.qa.ai.RCAService;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * TestNG listener that:
 *  1. Creates / closes an {@link ExtentTest} per test method.
 *  2. Captures a screenshot and embeds it in the report on failure.
 *  3. Triggers the AI Root-Cause-Analysis on failure.
 *  4. Flushes ExtentReports at suite end.
 */
public class ExtentTestListener implements ITestListener, ISuiteListener {

    private static final ExtentReports EXTENT = ExtentManager.getInstance();
    private static final String SCREENSHOT_PATH_ATTRIBUTE =
            ExtentTestListener.class.getName() + ".screenshotPath";

    // ── per-thread test node ──────────────────────────────────────────────────
    static final ThreadLocal<ExtentTest> CURRENT_TEST = new ThreadLocal<>();

    // ── ITestListener ─────────────────────────────────────────────────────────

    @Override
    public void onTestStart(ITestResult result) {
        ExtentTest test = EXTENT.createTest(
                result.getTestClass().getRealClass().getSimpleName()
                        + " :: " + result.getName());
        CURRENT_TEST.set(test);
        test.info("Test started");
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ExtentTest test = CURRENT_TEST.get();
        if (test != null) test.pass("Test passed ✔");
        CURRENT_TEST.remove();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        ExtentTest test = CURRENT_TEST.get();
        if (test != null) test.skip("Test skipped — "
                + (result.getThrowable() != null ? result.getThrowable().getMessage() : "no reason"));
        CURRENT_TEST.remove();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        ExtentTest test = CURRENT_TEST.get();
        Throwable throwable = result.getThrowable();

        // ── driver from BaseTest ───────────────────────────────────────────────
        WebDriver driver = null;
        Object instance = result.getInstance();
        if (instance instanceof BaseTest bt) {
            driver = bt.getDriver();
        }

        // ── screenshot ────────────────────────────────────────────────────────
        String screenshotPath = captureScreenshot(result, driver);

        if (test != null) {
            // embed screenshot into Extent report
            if (screenshotPath != null && !screenshotPath.startsWith("(")) {
                try {
                    test.fail("Screenshot at failure",
                            MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
                } catch (Exception e) {
                    test.fail("Screenshot capture failed: " + e.getMessage());
                }
            }
            if (throwable != null) {
                test.fail(throwable);
            } else {
                test.fail("Test failed without exception");
            }
        }
        CURRENT_TEST.remove();

        // ── AI Root-Cause Analysis ────────────────────────────────────────────
        runRCA(result, driver, screenshotPath, throwable);
    }

    // ── ISuiteListener ────────────────────────────────────────────────────────

    @Override
    public void onFinish(ISuite suite) {
        EXTENT.flush();
        System.out.println("[ExtentReports] Report written to target/extent-reports/index.html");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    static String captureScreenshot(ITestResult result, WebDriver driver) {
        Object existingPath = result.getAttribute(SCREENSHOT_PATH_ATTRIBUTE);
        if (existingPath instanceof String path) return path;

        String screenshotPath = captureScreenshot(driver, result.getName());
        result.setAttribute(SCREENSHOT_PATH_ATTRIBUTE, screenshotPath);
        if (screenshotPath.startsWith("(")) {
            System.err.println("[Screenshot] " + result.getName() + ": " + screenshotPath);
        } else {
            System.out.println("[Screenshot] Saved failure screenshot: " + screenshotPath);
        }
        return screenshotPath;
    }

    private static String captureScreenshot(WebDriver driver, String testName) {
        if (!(driver instanceof TakesScreenshot ts)) return "(driver unavailable)";
        try {
            Path dir = Path.of("target", "extent-reports", "screenshots");
            Files.createDirectories(dir);
            Path dest = dir.resolve(
                    testName.replaceAll("[^A-Za-z0-9_-]", "_")
                            + "-" + System.currentTimeMillis() + ".png");
            Files.copy(ts.getScreenshotAs(OutputType.FILE).toPath(), dest,
                    StandardCopyOption.REPLACE_EXISTING);
            return dest.toString();
        } catch (Exception e) {
            return "(capture failed: " + e.getClass().getSimpleName() + ": "
                    + e.getMessage() + ")";
        }
    }

    private void runRCA(ITestResult result, WebDriver driver,
                        String screenshotPath, Throwable throwable) {
        try {
            StringWriter sw = new StringWriter();
            if (throwable != null) throwable.printStackTrace(new PrintWriter(sw));

            FailureContext ctx = new FailureContext(
                    result.getTestClass().getRealClass().getSimpleName()
                            + "-" + result.getName(),
                    throwable != null ? throwable.toString() : "(no exception)",
                    sw.toString(),
                    safeGet(() -> driver != null ? driver.getCurrentUrl() : null),
                    safeGet(() -> driver != null ? driver.getTitle() : null),
                    safeGet(() -> driver != null ? limit(driver.getPageSource(), 12000) : null),
                    screenshotPath);

            Map<String, String> analyses = new RCAService().analyze(ctx);
            Path report = new RCAReportWriter().write(ctx, analyses);
            System.out.println("[AI-RCA] Report: " + report.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("[AI-RCA] Could not create report: " + e.getMessage());
        }
    }

    private String safeGet(ThrowingSupplier s) {
        try { return s.get(); } catch (Exception e) { return "(unavailable)"; }
    }

    private String limit(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "...[truncated]";
    }

    @FunctionalInterface
    private interface ThrowingSupplier { String get() throws Exception; }
}
