package me.rerere.rikkahub.ui.context

import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    private fun navigator(vararg screens: Screen) = Navigator(screens.toMutableList())

    @Test
    fun clearAndNavigateLeavesOnlyTheNewPage() {
        val stack = mutableListOf<androidx.navigation3.runtime.NavKey>(Screen.Chat("old"), Screen.History)
        Navigator(stack).clearAndNavigate(Screen.Chat("new"))
        assertEquals(listOf<androidx.navigation3.runtime.NavKey>(Screen.Chat("new")), stack)
    }

    @Test
    fun popBackStackStopsAtTheLastPage() {
        val nav = navigator(Screen.Chat("home"), Screen.Chat("a"))
        assertTrue(nav.popBackStack())
        assertFalse(nav.popBackStack())
    }
}
