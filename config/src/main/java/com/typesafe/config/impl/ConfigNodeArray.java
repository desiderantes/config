package com.typesafe.config.impl;

import com.typesafe.config.parser.ConfigNodeVisitor;

import java.util.Collection;

final class ConfigNodeArray extends ConfigNodeComplexValue implements com.typesafe.config.parser.ConfigNodeArray {
    ConfigNodeArray(Collection<AbstractConfigNode> children) {
        super(children);
    }

    @Override
    protected ConfigNodeArray newNode(Collection<AbstractConfigNode> nodes) {
        return new ConfigNodeArray(nodes);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitArray(this);
    }
}
