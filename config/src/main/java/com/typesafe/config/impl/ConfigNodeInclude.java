package com.typesafe.config.impl;

import com.typesafe.config.parser.ConfigNode;
import com.typesafe.config.parser.ConfigNodeVisitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class ConfigNodeInclude extends AbstractConfigNode implements com.typesafe.config.parser.ConfigNodeInclude {
    final private ArrayList<AbstractConfigNode> children;
    final private ConfigIncludeKind kind;
    final private boolean isRequired;

    ConfigNodeInclude(Collection<AbstractConfigNode> children, ConfigIncludeKind kind, boolean isRequired) {
        this.children = new ArrayList<>(children);
        this.kind = kind;
        this.isRequired = isRequired;
    }

    public Collection<AbstractConfigNode> children() {
        return children;
    }

    @Override
    protected Collection<Token> tokens() {
        ArrayList<Token> tokens = new ArrayList<>();
        for (AbstractConfigNode child : children) {
            tokens.addAll(child.tokens());
        }
        return tokens;
    }

    ConfigIncludeKind kind() {
        return kind;
    }

    boolean isRequired() {
        return isRequired;
    }

    String name() {
        for (AbstractConfigNode n : children) {
            if (n instanceof ConfigNodeSimpleValue cnsv && cnsv.token() instanceof TokenWithOrigin.Value val) {
                return val.value().unwrapped().toString();
            }
        }
        return null;
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitInclude(this);
    }

    @Override
    public List<ConfigNode> getChildren() {
        return Collections.unmodifiableList(children);
    }
}
