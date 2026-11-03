package com.jaivir.qa.pages;

import com.jaivir.qa.base.BasePage;
import com.jaivir.qa.config.ConfigManager;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * Page Object for the Secure Area page reached after a successful login.
 * The expected URL path is read from {@code config.properties} → {@code SECURE_PATH}
 * (default: /secure) combined with {@code BASE_URL}.
 */
public class SecureAreaPage extends BasePage {

    @FindBy(css = "h2")
    private WebElement heading;

    @FindBy(css = ".flash.success")
    private WebElement successBanner;

    @FindBy(css = "a[href='/logout']")
    private WebElement logoutButton;

    public SecureAreaPage(WebDriver driver) {
        super(driver);
        log("SecureAreaPage initialised via PageFactory");
    }

    public String getHeadingText() {
        waitForVisible(heading);
        return heading.getText();
    }

    public String getSuccessBannerText() {
        try {
            waitForVisible(successBanner);
            return successBanner.getText();
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isLogoutButtonDisplayed() {
        try {
            return logoutButton.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public LoginPage logout() {
        waitForClickable(logoutButton);
        logoutButton.click();
        log("Clicked logout");
        return new LoginPage(driver);
    }
}
