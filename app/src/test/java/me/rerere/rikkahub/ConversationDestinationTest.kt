package me.rerere.rikkahub

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationDestinationTest {
    @Test
    fun companionConversationOpensMessagesPage() {
        assertEquals(Screen.CompanionMessages, conversationDestination("abc", isCompanion = true))
    }

    @Test
    fun ordinaryConversationOpensChat() {
        assertEquals(Screen.Chat("abc"), conversationDestination("abc", isCompanion = false))
    }
}
