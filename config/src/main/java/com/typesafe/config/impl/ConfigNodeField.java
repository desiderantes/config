/**
 *   Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.typesafe.config.ConfigException;
import com.typesafe.config.parser.ConfigNode;
import com.typesafe.config.parser.ConfigNodePath;
import com.typesafe.config.parser.ConfigNodeVisitor;

final class ConfigNodeField extends AbstractConfigNode implements com.typesafe.config.parser.ConfigNodeField {
    final private ArrayList<AbstractConfigNode> children;

    public ConfigNodeField(Collection<AbstractConfigNode> children) {
        this.children = new ArrayList<AbstractConfigNode>(children);
    }

    @Override
    protected Collection<Token> tokens() {
        ArrayList<Token> tokens = new ArrayList<Token>();
        for (AbstractConfigNode child : children) {
            tokens.addAll(child.tokens());
        }
        return tokens;
    }

    public ConfigNodeField replaceValue(AbstractConfigNodeValue newValue) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<AbstractConfigNode>(children);
        for (int i = 0; i < childrenCopy.size(); i++) {
            if (childrenCopy.get(i) instanceof AbstractConfigNodeValue) {
                childrenCopy.set(i, newValue);
                return new ConfigNodeField(childrenCopy);
            }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a value");
    }

    public AbstractConfigNodeValue value() {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof AbstractConfigNodeValue) {
                return (AbstractConfigNodeValue)children.get(i);
            }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a value");
    }

    public ConfigNodeParsedPath path() {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof ConfigNodeParsedPath) {
                return (ConfigNodeParsedPath)children.get(i);
            }
        }
        throw new ConfigException.BugOrBroken("Field node doesn't have a path");
    }

    protected Token separator() {
        for (AbstractConfigNode child : children) {
            if (child instanceof ConfigNodeSingleToken) {
                Token t = ((ConfigNodeSingleToken) child).token();
                if (t == Tokens.PLUS_EQUALS || t == Tokens.COLON || t == Tokens.EQUALS) {
                    return t;
                }
            }
        }
        return null;
    }

    protected List<String> comments() {
        List<String> comments = new ArrayList<String>();
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
