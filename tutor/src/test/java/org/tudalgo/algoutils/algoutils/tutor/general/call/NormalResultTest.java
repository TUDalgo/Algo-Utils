package org.tudalgo.algoutils.algoutils.tutor.general.call;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

class NormalResultTest {

    @Test
    void assertExceptional() {
        var result = new NormalResult<>(1);
        Throwable actual;
        actual = assertThrows(AssertionFailedError.class, () -> result.assertThrows(RuntimeException.class));
        assertEquals("expected throwable of type RuntimeException, but no throwable was thrown", actual.getMessage());
        actual = assertThrows(AssertionFailedError.class, () -> result.assertThrows(RuntimeException.class, "test"));
        assertEquals("test: expected throwable of type RuntimeException, but no throwable was thrown", actual.getMessage());
        actual = assertThrows(AssertionFailedError.class, () -> result.assertThrows(RuntimeException.class, () -> "test"));
        assertEquals("test: expected throwable of type RuntimeException, but no throwable was thrown", actual.getMessage());
    }

    @Test
    void assertNormal() {
        var result = new NormalResult<>(1);
        assertEquals(1, result.assertNormal());
        assertEquals(1, result.assertNormal("test"));
        assertEquals(1, result.assertNormal(() -> "test"));
    }
}
