/**
 * Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValueType;
import com.typesafe.config.parser.ConfigNodeInt;
import com.typesafe.config.parser.ConfigNodeVisitor;

import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;

final class ConfigInt extends ConfigNumber implements Serializable, ConfigNodeInt {

    @Serial
    private static final long serialVersionUID = 2L;

    final private int value;

    ConfigInt(ConfigOrigin origin, int value, String originalText) {
        super(origin, originalText);
        this.value = value;
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.NUMBER;
    }

    @Override
    public Integer unwrapped() {
        return value;
    }

    @Override
    String transformToString() {
        String s = super.transformToString();
        if (s == null)
            return Integer.toString(value);
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
    protected ConfigInt newCopy(ConfigOrigin origin) {
        return new ConfigInt(origin, value, originalText);
    }

    // serialization all goes through SerializedConfigValue
    @Serial
    private Object writeReplace() throws ObjectStreamException {
        return new SerializedConfigValue(this);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitInt(this);
    }

    @Override
    public int getValue() {
        return value;
    }
}
