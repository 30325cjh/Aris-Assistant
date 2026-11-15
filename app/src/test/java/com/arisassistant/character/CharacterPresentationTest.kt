package com.arisassistant.character

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterPresentationTest {
    @Test
    fun compactLauncherUsesLauncherPresentation() {
        assertEquals(
            CharacterPresentationState(CharacterDisplayMode.LAUNCHER, "기본표정"),
            characterPresentationFor(expanded = false, expressionLabel = "기본표정")
        )
    }

    @Test
    fun expandedCharacterCarriesCurrentExpression() {
        assertEquals(
            CharacterPresentationState(CharacterDisplayMode.EXPANDED, "웃는표정"),
            characterPresentationFor(expanded = true, expressionLabel = "웃는표정")
        )
    }
}
