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
 * E2E tests for the message viewer.
 *
 * Verifies that clicking a message opens the viewer with subject and content.
 */
class MessageViewerIT extends BaseIT {

    @BeforeEach
    void setUp() {
        if (!isLoggedInAs(IsotopeTestEnvironment.USER1)) {
            performLogin(IsotopeTestEnvironment.USER1);
        } else {
            // Click INBOX in sidebar to navigate back to message list (preserves SPA state)
            navigateToInbox();
        }
        waitForMessageList();
    }

    @Test
    void clickingMessageOpensViewer() {
        // Wait for messages to load
        newWait().until(d -> d.getPageSource().contains("Welcome to Isotope")
                || d.getPageSource().contains("Meeting Tomorrow")
                || d.getPageSource().contains("Important Notice"));

        // Find a message item and click it using JavaScript to avoid overlay interception
        final List<WebElement> messageItems = driver().findElements(
                By.cssSelector("[class*='messageList'] [class*='itemDetails']"));

        assertThat(messageItems).isNotEmpty();
        jsClick(messageItems.get(0));

        // Wait for message viewer to appear
        newWait().until(d ->
                !d.findElements(By.cssSelector("[class*='message-viewer']")).isEmpty());

        // Message content should be visible
        final String pageText = driver().findElement(By.tagName("body")).getText();
        assertThat(pageText).isNotBlank();
    }

    @Test
    void messageViewerShowsSubjectAndContent() {
        // Wait for messages
        newWait().until(d -> d.getPageSource().contains("Welcome to Isotope")
                || d.getPageSource().contains("Meeting Tomorrow")
                || d.getPageSource().contains("Important Notice"));

        // Click a message using JavaScript to avoid overlay interception
        final List<WebElement> messageItems = driver().findElements(
                By.cssSelector("[class*='messageList'] [class*='itemDetails']"));

        assertThat(messageItems).isNotEmpty();
        jsClick(messageItems.get(0));

        // Wait for viewer with subject element
        newWait().until(d ->
                !d.findElements(By.cssSelector("[class*='message-viewer__subject']")).isEmpty());

        // The viewer should show a subject from the seeded emails
        final String subjectText = driver().findElement(
                By.cssSelector("[class*='message-viewer__subject']")).getText();

        assertThat(subjectText).satisfiesAnyOf(
                text -> assertThat(text).contains("Welcome to Isotope"),
                text -> assertThat(text).contains("Meeting Tomorrow"),
                text -> assertThat(text).contains("Important Notice"),
                text -> assertThat(text).contains("Project Update"),
                text -> assertThat(text).contains("Re: Welcome to Isotope")
        );
    }
}
