/**
 * Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.parser.ConfigNodeVisitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

final class ConfigNodeParsedPath extends AbstractConfigNode {
    final ArrayList<Token> tokens;
    final private Path path;

    ConfigNodeParsedPath(Path path, Collection<Token> tokens) {
        this.path = path;
        this.tokens = new ArrayList<>(tokens);
    }

    @Override
    protected Collection<Token> tokens() {
        return tokens;
    }

    protected Path value() {
        return path;
    }

    protected ConfigNodeParsedPath subPath(int toRemove) {
        int periodCount = 0;
        ArrayList<Token> tokensCopy = new ArrayList<>(tokens);
        for (int i = 0; i < tokensCopy.size(); i++) {
            if (tokensCopy.get(i) instanceof TokenWithOrigin.UnquotedText &&
                    tokensCopy.get(i).tokenText().equals("."))
                periodCount++;

            if (periodCount == toRemove) {
                return new ConfigNodeParsedPath(path.subPath(toRemove), tokensCopy.subList(i + 1, tokensCopy.size()));
            }
        }
        throw new ConfigException.BugOrBroken("Tried to remove too many elements from a Path node");
    }

    protected ConfigNodeParsedPath first() {
        ArrayList<Token> tokensCopy = new ArrayList<>(tokens);
        for (int i = 0; i < tokensCopy.size(); i++) {
            if (tokensCopy.get(i) instanceof TokenWithOrigin.UnquotedText &&
                    tokensCopy.get(i).tokenText().equals("."))
                return new ConfigNodeParsedPath(path.subPath(0, 1), tokensCopy.subList(0, i));
        }
        return this;
    }

    public ConfigNodeUnparsedPath toUnparsed(ConfigOrigin origin) {
        return new ConfigNodeUnparsedPath(
                Collections.unmodifiableList(tokens.stream().map(ConfigNodeSingleToken::new).collect(Collectors.toList())),
                origin);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitPath(toUnparsed(null));
    }
}
