/**
 * Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.parser.ConfigNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

abstract class ConfigNodeComplexValue extends AbstractConfigNodeValue {
    final protected ArrayList<AbstractConfigNode> children;

    ConfigNodeComplexValue(Collection<AbstractConfigNode> children) {
        this.children = new ArrayList<>(children);
    }

    final public Collection<AbstractConfigNode> children() {
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

    protected ConfigNodeComplexValue indentText(AbstractConfigNode indentation) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<>(children);
        for (int i = 0; i < childrenCopy.size(); i++) {
            AbstractConfigNode child = childrenCopy.get(i);
            if (child instanceof ConfigNodeSingleToken cnst && cnst.token() instanceof TokenWithOrigin.Line) {
                childrenCopy.add(i + 1, indentation);
                i++;
            } else if (child instanceof ConfigNodeField) {
                AbstractConfigNode value = ((ConfigNodeField) child).value();
                if (value instanceof ConfigNodeComplexValue) {
                    childrenCopy.set(i, ((ConfigNodeField) child).replaceValue(((ConfigNodeComplexValue) value).indentText(indentation)));
                }
            } else if (child instanceof ConfigNodeComplexValue) {
                childrenCopy.set(i, ((ConfigNodeComplexValue) child).indentText(indentation));
            }
        }
        return newNode(childrenCopy);
    }

    // This method will just call into the object's constructor, but it's needed
    // for use in the indentText() method so we can avoid a gross if/else statement
    // checking the type of this
    abstract ConfigNodeComplexValue newNode(Collection<AbstractConfigNode> nodes);

    public List<ConfigNode> getChildren() {
        return Collections.unmodifiableList(children);
    }
}
