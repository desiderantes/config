/**
 * Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import com.typesafe.config.parser.ConfigNode;
import com.typesafe.config.parser.ConfigNodeVisitor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class ConfigNodeSimpleValue extends AbstractConfigNodeValue {
    final TokenWithOrigin token;

    ConfigNodeSimpleValue(TokenWithOrigin value) {
        token = value;
    }

    @Override
    protected Collection<Token> tokens() {
        return Collections.singletonList(token);
    }

    protected Token token() {
        return token;
    }

    protected AbstractConfigValue value() {
        if (token instanceof TokenWithOrigin.Value val) {
            return val.value();
        } else if (token instanceof TokenWithOrigin.UnquotedText ut) {
            return new ConfigString.Unquoted(ut.origin(), ut.value());
        } else if (token instanceof TokenWithOrigin.Substitution substitution) {
            List<Token> expression = substitution.value();
            Path path = PathParser.parsePathExpression(expression.iterator(), substitution.origin());
            boolean optional = substitution.optional();

            return new ConfigReference(substitution.origin(), new SubstitutionExpression(path, optional));
        }
        throw new ConfigException.BugOrBroken("ConfigNodeSimpleValue did not contain a valid value token");
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        if (token instanceof TokenWithOrigin.Value || token instanceof TokenWithOrigin.UnquotedText) {
            return ((ConfigNode) value()).accept(visitor);
        } else if (token instanceof TokenWithOrigin.Substitution substitution) {
            List<Token> expression = substitution.value();
            boolean optional = substitution.optional();

            return visitor.visitReference(new ConfigNodeReference(substitution.origin(),
                    Collections.unmodifiableList(expression.stream().map(ConfigNodeSingleToken::new).collect(Collectors.toList())),
                    optional));
        }
        throw new ConfigException.BugOrBroken("ConfigNodeSimpleValue did not contain a valid value token");
    }
}
