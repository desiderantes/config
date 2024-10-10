/**
 * Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import com.typesafe.config.parser.ConfigNode;
import com.typesafe.config.parser.ConfigNodePath;
import com.typesafe.config.parser.ConfigNodeVisitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class ConfigNodeField extends AbstractConfigNode implements com.typesafe.config.parser.ConfigNodeField {
    final private ArrayList<AbstractConfigNode> children;

    public ConfigNodeField(Collection<AbstractConfigNode> children) {
        this.children = new ArrayList<>(children);
    }

    @Override
    protected Collection<Token> tokens() {
        ArrayList<Token> tokens = new ArrayList<>();
        for (AbstractConfigNode child : children) {
            tokens.addAll(child.tokens());
        }
        return tokens;
    }

    public ConfigNodeField replaceValue(AbstractConfigNodeValue newValue) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<>(children);
        for (int i = 0; i < childrenCopy.size(); i++) {
            if (childrenCopy.get(i) instanceof AbstractConfigNodeValue) {
                childrenCopy.set(i, newValue);
                return new ConfigNodeField(childrenCopy);
            }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a value");
    }

    public AbstractConfigNodeValue value() {
        for (AbstractConfigNode child : children) {
            if (child instanceof AbstractConfigNodeValue) {
                return (AbstractConfigNodeValue) child;
            }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a value");
    }

    public ConfigNodeParsedPath path() {
        for (AbstractConfigNode child : children) {
            if (child instanceof ConfigNodeParsedPath) {
                return (ConfigNodeParsedPath) child;
            }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a path");
    }

    Token separator() {
        for (AbstractConfigNode child : children) {
            if (child instanceof ConfigNodeSingleToken) {
                Token t = ((ConfigNodeSingleToken) child).token();
                if (t == StaticToken.PLUS_EQUALS || t == StaticToken.COLON || t == StaticToken.EQUALS) {
                    return t;
                }
            }
        }
        return null;
    }

    List<String> comments() {
        List<String> comments = new ArrayList<>();
        for (AbstractConfigNode child : children) {
            if (child instanceof ConfigNodeComment) {
                comments.add(((ConfigNodeComment) child).commentText());
            }
        }
        return comments;
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitField(this);
    }

    @Override
    public ConfigNodePath getPath() {
        return path().toUnparsed(origin());
    }

    @Override
    public ConfigNode getValue() {
        return value();
    }
}
