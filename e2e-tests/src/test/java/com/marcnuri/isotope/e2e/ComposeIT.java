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

import com.icegreen.greenmail.util.GreenMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for composing and sending messages.
 *
 * Verifies the compose button opens the editor, and that sending a message
 * delivers it to the recipient's mailbox (verified via GreenMail).
 */
class ComposeIT extends BaseIT {

    @BeforeEach
    void setUp() {
        if (!isLoggedIn()) {
            performLogin(IsotopeTestEnvironment.USER1);
        }
        waitForMessageList();
    }

    @Test
    void composeButtonOpensEditor() {
        // Click the compose FAB button (button with mdc-fab and compose-fab-button classes)
        final WebElement composeButton = newWait().until(d -> {
            final List<WebElement> fabs = d.findElements(
                    By.cssSelector("button[class*='mdc-fab']"));
            return fabs.isEmpty() ? null : fabs.get(0);
        });

        composeButton.click();

        // Wait for the editor to appear
        newWait().until(d ->
                !d.findElements(By.cssSelector("[class*='message-editor'],"
                        + "[class*='messageEditor']")).isEmpty()
                        || d.getCurrentUrl().contains("/edit")
        );

        // Editor should be visible
        final String bodyText = driver().findElement(By.tagName("body")).getText();
        assertThat(bodyText).isNotBlank();
    }

    @Test
    void sendMessageAndVerifyDelivery() throws MessagingException {
        final GreenMail greenMail = IsotopeTestEnvironment.getGreenMail();
        final int initialMessageCount = greenMail.getReceivedMessages().length;

        // Click compose FAB
        final WebElement composeButton = newWait().until(d -> {
            final List<WebElement> fabs = d.findElements(
                    By.cssSelector("button[class*='mdc-fab']"));
            return fabs.isEmpty() ? null : fabs.get(0);
        });
        composeButton.click();

        // Wait for editor
        newWait().until(d ->
                !d.findElements(By.cssSelector("[class*='message-editor'],"
                        + "[class*='messageEditor']")).isEmpty()
                        || d.getCurrentUrl().contains("/edit")
        );

        // Fill in recipient (To field) - HeaderAddress component with id='to'
        final List<WebElement> toFields = driver().findElements(
                By.cssSelector("[class*='header-address'] input"));
        if (!toFields.isEmpty()) {
            toFields.get(0).sendKeys(IsotopeTestEnvironment.USER2);
            toFields.get(0).sendKeys(Keys.ENTER);
        }

        // Fill in subject (input inside header-subject div)
        final List<WebElement> subjectFields = driver().findElements(
                By.cssSelector("[class*='header-subject'] input"));
        if (!subjectFields.isEmpty()) {
            subjectFields.get(0).clear();
            subjectFields.get(0).sendKeys("E2E Test Message");
        }

        // Click send button (button with message-editor__send class)
        final List<WebElement> sendButtons = driver().findElements(
                By.cssSelector("button[class*='message-editor__send']"));

        if (!sendButtons.isEmpty()) {
            sendButtons.get(0).click();

            // Wait for the message to be sent (editor should close)
            newWait(Duration.ofSeconds(15)).until(d ->
                    d.findElements(By.cssSelector("[class*='message-editor'],"
                            + "[class*='messageEditor']")).isEmpty()
                            || !d.getCurrentUrl().contains("/edit")
            );

            // Verify the message was delivered via GreenMail
            // Give some time for SMTP delivery
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            final MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
            assertThat(receivedMessages.length).isGreaterThan(initialMessageCount);

            // Find our test message
            boolean found = false;
            for (MimeMessage msg : receivedMessages) {
                if ("E2E Test Message".equals(msg.getSubject())) {
                    found = true;
                    break;
                }
            }
            assertThat(found)
                    .as("Sent message should be found in GreenMail received messages")
                    .isTrue();
        }
    }
}
