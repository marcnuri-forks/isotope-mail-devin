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

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for the login functionality.
 */
class LoginIT extends BaseIT {

    @Test
    void loginFormDisplaysRequiredFields() {
        driver().get(IsotopeTestEnvironment.getFrontendUrl() + "/login");
        newWait().until(d -> d.findElement(By.id("serverHost")).isDisplayed());

        assertThat(driver().findElement(By.id("serverHost")).isDisplayed()).isTrue();
        assertThat(driver().findElement(By.id("serverPort")).isDisplayed()).isTrue();
        assertThat(driver().findElement(By.id("user")).isDisplayed()).isTrue();
        assertThat(driver().findElement(By.id("password")).isDisplayed()).isTrue();
    }

    @Test
    void loginWithValidCredentialsRedirectsToApp() {
        performLogin(IsotopeTestEnvironment.USER1);

        // Should no longer be on login page
        assertThat(driver().getCurrentUrl()).doesNotContain("/login");
    }

    @Test
    void loginWithInvalidCredentialsShowsError() {
        driver().get(IsotopeTestEnvironment.getLoginUrl(IsotopeTestEnvironment.USER1));
        newWait().until(d -> d.findElement(By.id("serverHost")).isDisplayed());

        // Enter wrong password
        final WebElement passwordField = driver().findElement(By.id("password"));
        passwordField.clear();
        passwordField.sendKeys("wrongpassword");

        // Submit the form
        driver().findElement(By.cssSelector("form button[type='submit']")).click();

        // Should show an error (snackbar or stay on login page)
        newWait(Duration.ofSeconds(10)).until(d ->
                !d.findElements(By.cssSelector("[class*='snackbar']")).isEmpty()
                        || d.getCurrentUrl().contains("/login")
        );

        // Should still be on login page or show an error
        assertThat(driver().getCurrentUrl()).contains("/login");
    }

    @Test
    void loginFormShowsAdvancedSettings() {
        driver().get(IsotopeTestEnvironment.getFrontendUrl() + "/login");
        newWait().until(d -> d.findElement(By.id("serverHost")).isDisplayed());

        // Click Advanced button
        driver().findElement(By.cssSelector("button[class*='advanced']")).click();

        // SMTP fields should now be visible
        newWait().until(d -> d.findElement(By.id("smtpPort")).isDisplayed());

        assertThat(driver().findElement(By.id("smtpPort")).isDisplayed()).isTrue();
    }
}
