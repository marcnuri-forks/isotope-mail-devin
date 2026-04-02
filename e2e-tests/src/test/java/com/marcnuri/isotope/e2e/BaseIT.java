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
import org.openqa.selenium.JavascriptExecutor;
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

    private static String currentUser;

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
     * Click an element using JavaScript to bypass overlay interception issues.
     */
    protected void jsClick(WebElement element) {
        ((JavascriptExecutor) driver()).executeScript("arguments[0].click();", element);
    }

    /**
     * Navigate to the login page with pre-filled GreenMail connection details,
     * enter the password, and submit the login form.
     */
    protected void performLogin(String user) {
        final String password = IsotopeTestEnvironment.PASSWORD;
        // Clear all browser state to ensure a clean login
        clearBrowserState();
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

        // Wait for redirect to main app (sidebar/drawer should appear)
        newWait(Duration.ofSeconds(30)).until(d ->
                !d.findElements(By.cssSelector("aside[class*='mdc-drawer']")).isEmpty()
                        && !d.getCurrentUrl().contains("/login"));
        currentUser = user;
    }

    /**
     * Wait for the message list to be loaded in the current folder.
     * The message list component renders a div with CSS Module class 'messageList'.
     * After login, the app auto-selects INBOX and streams messages via SSE.
     */
    protected void waitForMessageList() {
        newWait(Duration.ofSeconds(30)).until(d ->
                // CSS Module hashed class from message-list.scss (.messageList)
                !d.findElements(By.cssSelector("[class*='messageList']")).isEmpty()
        );
        // Also wait for at least one message item to be rendered
        newWait(Duration.ofSeconds(15)).until(d ->
                !d.findElements(By.cssSelector("[class*='messageList'] [class*='item']")).isEmpty()
        );
    }

    /**
     * Navigate back to the message list from any page (e.g. message viewer).
     * Uses the INBOX folder in the sidebar instead of page reload to preserve SPA state.
     */
    protected void navigateToInbox() {
        // Click the INBOX folder in the sidebar
        final var inboxItems = driver().findElements(By.cssSelector("[class*='mdc-list-item']"));
        for (WebElement item : inboxItems) {
            if (item.getText().toLowerCase().contains("inbox")) {
                jsClick(item);
                return;
            }
        }
    }

    /**
     * Clear browser state (cookies, localStorage, sessionStorage) and reset login tracking.
     */
    protected void clearBrowserState() {
        driver().manage().deleteAllCookies();
        ((JavascriptExecutor) driver()).executeScript(
                "try { window.localStorage.clear(); window.sessionStorage.clear(); } catch(e) {}");
        currentUser = null;
    }

    /**
     * Check if the given user is currently logged in.
     */
    protected boolean isLoggedInAs(String user) {
        return !driver().getCurrentUrl().contains("/login") && user.equals(currentUser);
    }

    /**
     * Check if any user is currently logged in.
     */
    protected boolean isLoggedIn() {
        return !driver().getCurrentUrl().contains("/login") && currentUser != null;
    }
}
