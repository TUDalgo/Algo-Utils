package org.tudalgo.algoutils.tutor.general.stringifier;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.tudalgo.algoutils.tutor.general.assertions.Assertions2;

import static org.junit.jupiter.api.Assertions.*;

public class PrimitiveArraysTest {

    @Test
    public void testStringifyDoubleArray() {
        double[] testArray = new double[4];
        for (int i = 0; i < testArray.length; i++) {
            testArray[i] = i;
        }
        var context = Assertions2.contextBuilder().
            add("array", testArray)
            .build();
        assertThrows(AssertionFailedError.class,
            () -> Assertions2.assertEquals(0.5, 1.0, context, result -> "Failed"),
            "Expected AssertionFailedError"
        );
    }

    @Test
    public void testCreationOfInvalidContext() {
        byte[] testArray = new byte[0];
        assertThrows(IllegalArgumentException.class,
            () -> Assertions2.contextBuilder().add("faulty", testArray),
            "Expected IllegalArgumentException"
        );
    }

}
