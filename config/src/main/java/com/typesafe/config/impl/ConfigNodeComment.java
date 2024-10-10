package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import com.typesafe.config.parser.ConfigNodeVisitor;

final class ConfigNodeComment extends ConfigNodeSingleToken implements com.typesafe.config.parser.ConfigNodeComment {
    ConfigNodeComment(TokenWithOrigin.Comment comment) {
        super(comment);
        if (!(super.token instanceof TokenWithOrigin.Comment)) {
            throw new ConfigException.BugOrBroken("Tried to create a ConfigNodeComment from a non-comment token");
        }
    }

    String commentText() {
        return ((TokenWithOrigin.Comment) token).text();
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitComment(this);
    }

    @Override
    public String getValue() {
        return commentText();
    }
}
