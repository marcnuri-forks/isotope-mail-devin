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
 * E2E tests for the message list view.
 *
 * Verifies that seeded messages appear in the list with subject and sender visible.
 */
class MessageListIT extends BaseIT {

    @BeforeEach
    void setUp() {
        if (!isLoggedIn()) {
            performLogin(IsotopeTestEnvironment.USER1);
        }
        waitForMessageList();
    }

    @Test
    void messageListShowsSeededMessages() {
        // Wait for messages to load
        waitForMessageList();

        // The page should contain some of our seeded message subjects
        newWait().until(d -> d.getPageSource().contains("Welcome to Isotope")
                || d.getPageSource().contains("Meeting Tomorrow")
                || d.getPageSource().contains("Important Notice"));

        final String pageSource = driver().getPageSource();
        // At least one of the seeded messages should appear
        assertThat(pageSource).containsAnyOf(
                "Welcome to Isotope",
                "Meeting Tomorrow",
                "Important Notice"
        );
    }

    @Test
    void messageListItemsContainSubjectAndSender() {
        // Wait for list items (message items have mdc-list-item class)
        newWait().until(d -> !d.findElements(By.cssSelector("[class*='mdc-list-item']")).isEmpty());

        final List<WebElement> messageItems = driver().findElements(
                By.cssSelector("[class*='mdc-list-item']"));
        assertThat(messageItems).isNotEmpty();

        // Each item should have subject and from spans
        final WebElement firstItem = messageItems.get(0);
        assertThat(firstItem.findElements(By.cssSelector("[class*='subject']"))).isNotEmpty();
        assertThat(firstItem.findElements(By.cssSelector("[class*='from']"))).isNotEmpty();
        assertThat(firstItem.getText()).isNotBlank();
    }
}
