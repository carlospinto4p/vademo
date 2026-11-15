package com.cpinto.vademo

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/**
 * Opens the knowledge base [name] from the home screen. Its row can be tapped
 * only once the app has found its download, which happens after launch, so a
 * tap straight away can land on a row that does nothing yet.
 */
fun ComposeTestRule.openKnowledgeBase(name: String = "kb-distributed-systems") {
    waitUntil(5_000) { onAllNodes(hasText(name) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    onNodeWithText(name).performClick()
}
