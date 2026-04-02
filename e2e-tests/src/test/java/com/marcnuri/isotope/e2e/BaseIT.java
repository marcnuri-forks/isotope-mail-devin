/*
 * Copyright 2024 Marc Nuri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marcnuri.isotope.e2e;

import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import java.time.Duration;

/**
 * Base class for all Isotope Mail e2e integration tests.
 * Provides shared utilities for WebDriver interaction.
 */
@ExtendWith(IsotopeTestEnvironment.class)
public abstract class BaseIT {

    protected WebDriver driver() {
        return IsotopeTestEnvironment.getDriver();
    }

    protected Wait<WebDriver> newWait() {
        return newWait(Duration.ofSeconds(10));
    }

    protected Wait<WebDriver> newWait(Duration timeout) {
        return new FluentWait<>(driver())
                .withTimeout(timeout)
                .pollingEvery(Duration.ofMillis(200))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
    }

    /**
     * Navigate to the login page with pre-filled GreenMail connection details,
     * enter the password, and submit the login form.
     */
    protected void performLogin(String user) {
        final String password = IsotopeTestEnvironment.PASSWORD;
        driver().get(IsotopeTestEnvironment.getLoginUrl(user));

        // Wait for login form to be visible
        newWait().until(d -> d.findElement(By.id("serverHost")).isDisplayed());

        // The login URL pre-fills host, port, user via query params.
        // We need to fill in the password and click Advanced to set SMTP settings.
        final WebElement passwordField = driver().findElement(By.id("password"));
        passwordField.clear();
        passwordField.sendKeys(password);

        // Submit the form
        driver().findElement(By.cssSelector("form button[type='submit']")).click();

        // Wait for redirect to main app (folder list should appear)
        newWait(Duration.ofSeconds(30)).until(d -> {
            // After login, the app redirects to "/" which shows the folder list
            return !d.findElements(By.cssSelector("[class*='sidebar']")).isEmpty()
                    || !d.findElements(By.cssSelector("[class*='folder']")).isEmpty()
                    || d.getCurrentUrl().endsWith("/")
                    || !d.getCurrentUrl().contains("/login");
        });
    }

    /**
     * Wait for the message list to be loaded in the current folder.
     */
    protected void waitForMessageList() {
        newWait(Duration.ofSeconds(15)).until(d ->
                !d.findElements(By.cssSelector("[class*='message-list']")).isEmpty()
                        || !d.findElements(By.cssSelector("[class*='messageList']")).isEmpty()
        );
    }

    /**
     * Check if the user is currently logged in (i.e., not on the login page).
     */
    protected boolean isLoggedIn() {
        return !driver().getCurrentUrl().contains("/login");
    }
}
