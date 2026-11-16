package com.arisassistant.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantPresentationDirectiveTest {
    @Test
    fun acceptsOnlyKnownPresentationValues() {
        val validated = UntrustedPresentationDirective(
            emotion = " HAPPY ",
            expression = "smile",
            motion = "nod",
            intensity = 0.7f
        ).validate()

        assertEquals(AssistantEmotion.HAPPY, validated.emotion)
        assertEquals(CharacterExpression.SMILE, validated.expression)
        assertEquals(CharacterMotion.NOD, validated.motion)
        assertEquals(0.7f, validated.intensity, 0.0001f)
    }

    @Test
    fun unknownValuesFailClosedToNeutralAndNoMotion() {
        val validated = UntrustedPresentationDirective(
            emotion = "launch_app",
            expression = "custom_expression",
            motion = "open_settings",
            intensity = 0.8f
        ).validate()

        assertEquals(AssistantEmotion.NEUTRAL, validated.emotion)
        assertEquals(CharacterExpression.NEUTRAL, validated.expression)
        assertEquals(CharacterMotion.NONE, validated.motion)
        assertEquals(0.8f, validated.intensity, 0.0001f)
    }

    @Test
    fun intensityIsClampedToTheSupportedRange() {
        assertEquals(
            1f,
            UntrustedPresentationDirective(intensity = 3f).validate().intensity,
            0.0001f
        )
        assertEquals(
            0f,
            UntrustedPresentationDirective(intensity = -2f).validate().intensity,
            0.0001f
        )
    }

    @Test
    fun nonFiniteIntensityFallsBackToZero() {
        assertEquals(
            0f,
            UntrustedPresentationDirective(intensity = Float.NaN).validate().intensity,
            0.0001f
        )
        assertEquals(
            0f,
            UntrustedPresentationDirective(intensity = Float.POSITIVE_INFINITY).validate().intensity,
            0.0001f
        )
    }
}
