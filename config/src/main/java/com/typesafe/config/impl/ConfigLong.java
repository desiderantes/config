/**
 * Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;
import com.typesafe.config.parser.ConfigNodeLong;
import com.typesafe.config.parser.ConfigNodeVisitor;

import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;

final class ConfigLong extends ConfigNumber implements Serializable, ConfigNodeLong {

    @Serial
    private static final long serialVersionUID = 2L;

    final private long value;

    ConfigLong(ConfigOrigin origin, long value, String originalText) {
        super(origin, originalText);
        this.value = value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.NUMBER;
    }

    @Override
    public Long unwrapped() {
        return value;
    }

    @Override
    String transformToString() {
        String s = super.transformToString();
        if (s == null)
            return Long.toString(value);
        else
            return s;
    }

    @Override
    protected long longValue() {
        return value;
    }

    @Override
    protected double doubleValue() {
        return value;
    }

    @Override
    protected ConfigLong newCopy(ConfigOrigin origin) {
        return new ConfigLong(origin, value, originalText);
    }

    // serialization all goes through SerializedConfigValue
    @Serial
    private Object writeReplace() throws ObjectStreamException {
        return new SerializedConfigValue(this);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitLong(this);
    }

    @Override
    public long getValue() {
        return value;
    }
}
