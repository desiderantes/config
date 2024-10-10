/**
 * Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config;

import java.math.BigInteger;

/**
 * An immutable class representing an amount of memory.
 *
 * @since 1.3.0
 */
public record ConfigMemorySize(BigInteger bytes) {

    public ConfigMemorySize {
        if (bytes.signum() < 0)
            throw new IllegalArgumentException("Attempt to construct ConfigMemorySize with negative number: " + bytes);
    }

    public ConfigMemorySize(long bytes) {
        this(BigInteger.valueOf(bytes));
    }

    /**
     * Gets the size in bytes.
     *
     * @return how many bytes
     * @throws IllegalArgumentException when memory value
     *                                  in bytes doesn't fit in a long value. Consider using
     *                                  {@link #bytes()} in this case.
     * @since 1.3.0
     */
    public long toLongBytes() {
        if (bytes.bitLength() < 64)
            return bytes.longValue();
        else
            throw new IllegalArgumentException(
                    "size-in-bytes value is out of range for a 64-bit long: '" + bytes + "'");
    }

}

