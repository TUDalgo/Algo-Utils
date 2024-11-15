package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.Type;

public interface Header {

    Type getType();

    Type[] getConstructorParameterTypes();

    Object getValue(String name);
}
