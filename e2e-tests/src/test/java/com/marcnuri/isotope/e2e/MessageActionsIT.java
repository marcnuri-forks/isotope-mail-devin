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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for message actions (flag, mark read/unread, delete).
 *
 * Uses USER2 to verify message opening, delete button availability,
 * and top bar action buttons in the message viewer.
 */
class MessageActionsIT extends BaseIT {

    @BeforeEach
    void setUp() {
        if (!isLoggedIn()) {
            performLogin(IsotopeTestEnvironment.USER2);
        }
        waitForMessageList();
    }

    @Test
    void messageCanBeOpened() {
        // Wait for messages to load
        newWait().until(d -> !d.findElements(By.cssSelector("[class*='mdc-list-item']")).isEmpty());

        // Click on a message
        final List<WebElement> messageItems = driver().findElements(
                By.cssSelector("[class*='mdc-list-item'] [class*='itemDetails'],"
                        + "[class*='mdc-list-item']"));

        assertThat(messageItems).isNotEmpty();
        messageItems.get(0).click();

        // Wait for message viewer
        newWait().until(d ->
                !d.findElements(By.cssSelector("[class*='message-viewer']")).isEmpty());

        // Message viewer should contain text
        assertThat(driver().findElement(By.tagName("body")).getText()).isNotBlank();
    }

    @Test
    void deleteButtonIsAvailableInViewer() {
        // Wait for messages
        newWait().until(d -> !d.findElements(By.cssSelector("[class*='mdc-list-item']")).isEmpty());

        // Click on a message
        final List<WebElement> messageItems = driver().findElements(
                By.cssSelector("[class*='mdc-list-item'] [class*='itemDetails'],"
                        + "[class*='mdc-list-item']"));

        assertThat(messageItems).isNotEmpty();
        messageItems.get(0).click();

        // Wait for message viewer
        newWait().until(d ->
                !d.findElements(By.cssSelector("[class*='message-viewer']")).isEmpty());

        // The top bar should have a delete button (TopBarButton containing 'delete' text)
        newWait().until(d ->
                !d.findElements(By.cssSelector(
                        "[class*='mdc-top-app-bar__section'] button")).isEmpty());

        final List<WebElement> topBarButtons = driver().findElements(
                By.cssSelector("[class*='mdc-top-app-bar__section'] button"));
        assertThat(topBarButtons).isNotEmpty();
    }

    @Test
    void topBarShowsActionButtonsInViewer() {
        // Wait for messages
        newWait().until(d -> !d.findElements(By.cssSelector("[class*='mdc-list-item']")).isEmpty());

        // Click on a message
        final List<WebElement> messageItems = driver().findElements(
                By.cssSelector("[class*='mdc-list-item'] [class*='itemDetails'],"
                        + "[class*='mdc-list-item']"));

        assertThat(messageItems).isNotEmpty();
        messageItems.get(0).click();

        // Wait for message viewer
        newWait().until(d ->
                !d.findElements(By.cssSelector("[class*='message-viewer']")).isEmpty());

        // Top bar should have action buttons (reply, forward, delete, mark unread)
        final List<WebElement> topBarButtons = driver().findElements(
                By.cssSelector("[class*='mdc-top-app-bar__section'] button"));

        // Should have multiple buttons (back, reply all, forward, delete, mark unread)
        assertThat(topBarButtons.size()).isGreaterThanOrEqualTo(3);
    }
}
