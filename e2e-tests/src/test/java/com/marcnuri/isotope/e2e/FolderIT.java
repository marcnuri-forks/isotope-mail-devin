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
 * E2E tests for folder navigation.
 *
 * Verifies that the folder list is displayed in the sidebar after login,
 * that the INBOX folder is present, and that clicking a folder triggers message loading.
 */
class FolderIT extends BaseIT {

    @BeforeEach
    void setUp() {
        if (!isLoggedInAs(IsotopeTestEnvironment.USER1)) {
            performLogin(IsotopeTestEnvironment.USER1);
        }
    }

    @Test
    void sideBarIsDisplayedAfterLogin() {
        // The sidebar (aside with mdc-drawer) should be present
        newWait().until(d -> !d.findElements(By.cssSelector("aside[class*='mdc-drawer']")).isEmpty());
        final List<WebElement> sidebar = driver().findElements(By.cssSelector("aside[class*='mdc-drawer']"));
        assertThat(sidebar).isNotEmpty();
    }

    @Test
    void folderListContainsInbox() {
        // Wait for the sidebar drawer content with folders
        newWait().until(d -> !d.findElements(By.cssSelector("[class*='mdc-drawer__content']")).isEmpty());

        // The INBOX folder should be present (rendered as mdc-list-item)
        newWait().until(d -> {
            final List<WebElement> items = d.findElements(By.cssSelector("[class*='mdc-list-item']"));
            return items.stream().anyMatch(el -> el.getText().toLowerCase().contains("inbox"));
        });

        final String sidebarText = driver().findElement(
                By.cssSelector("[class*='mdc-drawer__content']")).getText();
        assertThat(sidebarText.toLowerCase()).contains("inbox");
    }

    @Test
    void clickingInboxFolderLoadsMessages() {
        // Wait for folder items
        newWait().until(d -> {
            final List<WebElement> items = d.findElements(By.cssSelector("[class*='mdc-list-item']"));
            return items.stream().anyMatch(el -> el.getText().toLowerCase().contains("inbox"));
        });

        // Find and click the INBOX folder using JavaScript to avoid overlay interception
        final List<WebElement> folderItems = driver().findElements(
                By.cssSelector("[class*='mdc-list-item']"));
        for (WebElement item : folderItems) {
            if (item.getText().toLowerCase().contains("inbox")) {
                jsClick(item);
                break;
            }
        }

        // Wait for message list to appear
        waitForMessageList();
        assertThat(driver().getPageSource()).isNotEmpty();
    }
}
