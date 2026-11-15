package me.rerere.rikkahub.ui.context

import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    private fun navigator(vararg screens: Screen) = Navigator(screens.toMutableList())

    @Test
    fun resetToHomePutsMessagesAtTheBottom() {
        val stack = mutableListOf<androidx.navigation3.runtime.NavKey>(Screen.Chat("old"))
        Navigator(stack).resetToHome(Screen.Chat("new"))
        assertEquals(listOf(Screen.CompanionMessages, Screen.Chat("new")), stack)
    }

    @Test
    fun resetToHomeWithMessagesDoesNotStackTwice() {
        val stack = mutableListOf<androidx.navigation3.runtime.NavKey>()
        Navigator(stack).resetToHome(Screen.CompanionMessages)
        assertEquals(listOf(Screen.CompanionMessages), stack)
    }

    @Test
    fun popBackStackStopsAtTheLastPage() {
        val nav = navigator(Screen.CompanionMessages, Screen.Chat("a"))
        assertTrue(nav.popBackStack())
        assertFalse(nav.popBackStack())
    }
}
