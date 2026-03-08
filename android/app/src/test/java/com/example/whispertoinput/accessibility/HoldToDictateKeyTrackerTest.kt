package com.example.whispertoinput.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TEST_KEY_CODE = 204

class HoldToDictateKeyTrackerTest {
    @Test
    fun longPressStartsOnlyAfterTimeout() {
        val tracker = HoldToDictateKeyTracker(TEST_KEY_CODE)

        assertTrue(tracker.onKeyDown(TEST_KEY_CODE))
        assertTrue(tracker.onLongPress())
        assertTrue(tracker.onKeyUp(TEST_KEY_CODE))
    }

    @Test
    fun shortPressDoesNotTriggerStop() {
        val tracker = HoldToDictateKeyTracker(TEST_KEY_CODE)

        assertTrue(tracker.onKeyDown(TEST_KEY_CODE))
        assertFalse(tracker.onKeyUp(TEST_KEY_CODE))
    }

    @Test
    fun cancelClearsTrackedPress() {
        val tracker = HoldToDictateKeyTracker(TEST_KEY_CODE)

        assertTrue(tracker.onKeyDown(TEST_KEY_CODE))
        tracker.cancel()

        assertFalse(tracker.onKeyUp(TEST_KEY_CODE))
        assertTrue(tracker.onKeyDown(TEST_KEY_CODE))
    }
}
