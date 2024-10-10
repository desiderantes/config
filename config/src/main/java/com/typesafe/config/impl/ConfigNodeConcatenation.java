package com.typesafe.config.impl;

import com.typesafe.config.parser.ConfigNodeVisitor;

import java.util.Collection;

final class ConfigNodeConcatenation extends ConfigNodeComplexValue
        implements com.typesafe.config.parser.ConfigNodeConcatenation {
    ConfigNodeConcatenation(Collection<AbstractConfigNode> children) {
        super(children);
    }

    @Override
    protected ConfigNodeConcatenation newNode(Collection<AbstractConfigNode> nodes) {
        return new ConfigNodeConcatenation(nodes);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitConcatenation(this);
    }

}
