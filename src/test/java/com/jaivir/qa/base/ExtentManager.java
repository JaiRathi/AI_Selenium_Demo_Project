package com.jaivir.qa.base;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

/**
 * Singleton holder for the ExtentReports instance.
 * Thread-safe via double-checked locking. Flush is called by
 * {@link ExtentTestListener#onFinish(org.testng.ISuite)}.
 */
public final class ExtentManager {

    private static volatile ExtentReports extent;

    private ExtentManager() {}

    public static ExtentReports getInstance() {
        if (extent == null) {
            synchronized (ExtentManager.class) {
                if (extent == null) {
                    extent = createInstance();
                }
            }
        }
        return extent;
    }

    private static ExtentReports createInstance() {
        String reportPath = System.getProperty("user.dir")
                + "/target/extent-reports/index.html";

        ExtentSparkReporter spark = new ExtentSparkReporter(reportPath);
        spark.config().setDocumentTitle("AI Selenium Framework — Test Report");
        spark.config().setReportName("Automation Results");
        spark.config().setTheme(Theme.DARK);
        spark.config().setEncoding("UTF-8");
        spark.config().setTimeStampFormat("dd-MMM-yyyy HH:mm:ss");

        ExtentReports reports = new ExtentReports();
        reports.attachReporter(spark);
        reports.setSystemInfo("Framework",  "AI Selenium Framework v3");
        reports.setSystemInfo("Author",     "Jaivir Singh");
        reports.setSystemInfo("Java",       System.getProperty("java.version"));
        reports.setSystemInfo("OS",         System.getProperty("os.name"));
        return reports;
    }
}
