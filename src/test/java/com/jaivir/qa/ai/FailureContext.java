package com.jaivir.qa.ai;

public record FailureContext(
        String testName, String exception, String stackTrace,
        String currentUrl, String pageTitle, String domSnapshot,
        String screenshotPath) {}
