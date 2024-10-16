package com.typesafe.config.impl;

import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.parser.ConfigNodeVisitor;

import java.util.ArrayList;
import java.util.Collection;

final class ConfigNodeObject extends ConfigNodeComplexValue implements com.typesafe.config.parser.ConfigNodeObject {
    ConfigNodeObject(Collection<AbstractConfigNode> children) {
        super(children);
    }

    @Override
    protected ConfigNodeObject newNode(Collection<AbstractConfigNode> nodes) {
        return new ConfigNodeObject(nodes);
    }

    public boolean hasValue(Path desiredPath) {
        for (AbstractConfigNode node : children) {
            if (node instanceof ConfigNodeField field) {
                Path key = field.path().value();
                if (key.equals(desiredPath) || key.startsWith(desiredPath)) {
                    return true;
                } else if (desiredPath.startsWith(key)) {
                    if (field.value() instanceof ConfigNodeObject obj) {
                        Path remainingPath = desiredPath.subPath(key.length());
                        if (obj.hasValue(remainingPath)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    protected ConfigNodeObject changeValueOnPath(Path desiredPath, AbstractConfigNodeValue value, ConfigSyntax flavor) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<>(super.children);
        boolean seenNonMatching = false;
        // Copy the value so we can change it to null but not modify the original parameter
        AbstractConfigNodeValue valueCopy = value;
        for (int i = childrenCopy.size() - 1; i >= 0; i--) {
            if (childrenCopy.get(i) instanceof ConfigNodeSingleToken) {
                Token t = ((ConfigNodeSingleToken) childrenCopy.get(i)).token();
                // Ensure that, when we are removing settings in JSON, we don't end up with a trailing comma
                if (flavor == ConfigSyntax.JSON && !seenNonMatching && t == StaticToken.COMMA) {
                    childrenCopy.remove(i);
                }
                continue;
            } else if (!(childrenCopy.get(i) instanceof ConfigNodeField)) {
                continue;
            }
            ConfigNodeField node = (ConfigNodeField) childrenCopy.get(i);
            Path key = node.path().value();

            // Delete all multi-element paths that start with the desired path, since technically they are duplicates
            if ((valueCopy == null && key.equals(desiredPath)) || (key.startsWith(desiredPath) && !key.equals(desiredPath))) {
                childrenCopy.remove(i);
                // Remove any whitespace or commas after the deleted setting
                for (int j = i; j < childrenCopy.size(); j++) {
                    if (childrenCopy.get(j) instanceof ConfigNodeSingleToken) {
                        Token t = ((ConfigNodeSingleToken) childrenCopy.get(j)).token();
                        if (t instanceof TokenWithOrigin.IgnoredWhitespace || t == StaticToken.COMMA) {
                            childrenCopy.remove(j);
                            j--;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            } else if (key.equals(desiredPath)) {
                seenNonMatching = true;
                AbstractConfigNodeValue indentedValue;
                AbstractConfigNode before = i - 1 > 0 ? childrenCopy.get(i - 1) : null;
                if (value instanceof ConfigNodeComplexValue &&
                        before instanceof ConfigNodeSingleToken cnst &&
                        cnst.token() instanceof TokenWithOrigin.IgnoredWhitespace) {
                    indentedValue = ((ConfigNodeComplexValue) value).indentText(cnst);
                } else {
                    indentedValue = value;
                }
                childrenCopy.set(i, node.replaceValue(indentedValue));
                valueCopy = null;
            } else if (desiredPath.startsWith(key)) {
                seenNonMatching = true;
                if (node.value() instanceof ConfigNodeObject) {
                    Path remainingPath = desiredPath.subPath(key.length());
                    childrenCopy.set(i, node.replaceValue(((ConfigNodeObject) node.value()).changeValueOnPath(remainingPath, valueCopy, flavor)));
                    if (valueCopy != null && !node.equals(super.children.get(i))) {
                        valueCopy = null;
                    }
                }
            } else {
                seenNonMatching = true;
            }
        }
        return new ConfigNodeObject(childrenCopy);
    }

    public ConfigNodeObject setValueOnPath(String desiredPath, AbstractConfigNodeValue value) {
        return setValueOnPath(desiredPath, value, ConfigSyntax.CONF);
    }

    public ConfigNodeObject setValueOnPath(String desiredPath, AbstractConfigNodeValue value, ConfigSyntax flavor) {
        ConfigNodeParsedPath path = PathParser.parsePathNode(desiredPath, flavor);
        return setValueOnPath(path, value, flavor);
    }

    private ConfigNodeObject setValueOnPath(ConfigNodeParsedPath desiredPath, AbstractConfigNodeValue value, ConfigSyntax flavor) {
        ConfigNodeObject node = changeValueOnPath(desiredPath.value(), value, flavor);

        // If the desired Path did not exist, add it
        if (!node.hasValue(desiredPath.value())) {
            return node.addValueOnPath(desiredPath, value, flavor);
        }
        return node;
    }

    private Collection<AbstractConfigNode> indentation() {
        boolean seenNewLine = false;
        ArrayList<AbstractConfigNode> indentation = new ArrayList<>();
        if (children.isEmpty()) {
            return indentation;
        }
        for (int i = 0; i < children.size(); i++) {
            if (!seenNewLine) {
                if (children.get(i) instanceof ConfigNodeSingleToken cnst &&
                        cnst.token() instanceof TokenWithOrigin.Line) {
                    seenNewLine = true;
                    indentation.add(new ConfigNodeSingleToken(new TokenWithOrigin.Line(null)));
                }
            } else {
                if (children.get(i) instanceof ConfigNodeSingleToken cnst &&
                        cnst.token() instanceof TokenWithOrigin.Line &&
                        i + 1 < children.size() && (children.get(i + 1) instanceof ConfigNodeField ||
                        children.get(i + 1) instanceof ConfigNodeInclude)) {
                    // Return the indentation of the first setting on its own line
                    indentation.add(children.get(i));
                    return indentation;
                }
            }
        }
        if (indentation.isEmpty()) {
            indentation.add(new ConfigNodeSingleToken(new TokenWithOrigin.IgnoredWhitespace(null, " ")));
        } else {
            // Calculate the indentation of the ending curly-brace to get the indentation of the root object
            AbstractConfigNode last = children.getLast();
            if (last instanceof ConfigNodeSingleToken && ((ConfigNodeSingleToken) last).token() == StaticToken.CLOSE_CURLY) {
                AbstractConfigNode beforeLast = children.get(children.size() - 2);
                String indent = "";
                if (beforeLast instanceof ConfigNodeSingleToken) {
                    Token token = ((ConfigNodeSingleToken) beforeLast).token();
                    if (token instanceof TokenWithOrigin.IgnoredWhitespace)
                        indent = ((ConfigNodeSingleToken) beforeLast).token().tokenText();
                }
                indent += "  ";
                indentation.add(new ConfigNodeSingleToken(new TokenWithOrigin.IgnoredWhitespace(null, indent)));
                return indentation;
            }
        }

        // The object has no curly braces and is at the root level, so don't indent
        return indentation;
    }

    protected ConfigNodeObject addValueOnPath(ConfigNodeParsedPath desiredPath, AbstractConfigNodeValue value, ConfigSyntax flavor) {
        Path path = desiredPath.value();
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<>(super.children);
        ArrayList<AbstractConfigNode> indentation = new ArrayList<>(indentation());

        // If the value we're inserting is a complex value, we'll need to indent it for insertion
        AbstractConfigNodeValue indentedValue;
        if (value instanceof ConfigNodeComplexValue cncv && !indentation.isEmpty()) {
            indentedValue = cncv.indentText(indentation.getLast());
        } else {
            indentedValue = value;
        }
        boolean sameLine = !(!indentation.isEmpty() && indentation.getFirst() instanceof ConfigNodeSingleToken cnst &&
                cnst.token() instanceof TokenWithOrigin.Line);

        // If the path is of length greater than one, see if the value needs to be added further down
        if (path.length() > 1) {
            for (int i = super.children.size() - 1; i >= 0; i--) {
                if (!(super.children.get(i) instanceof ConfigNodeField node)) {
                    continue;
                }
                Path key = node.path().value();
                if (path.startsWith(key) && node.value() instanceof ConfigNodeObject newValue) {
                    ConfigNodeParsedPath remainingPath = desiredPath.subPath(key.length());
                    childrenCopy.set(i, node.replaceValue(newValue.addValueOnPath(remainingPath, value, flavor)));
                    return new ConfigNodeObject(childrenCopy);
                }
            }
        }

        // Otherwise, construct the new setting
        boolean startsWithBrace = !super.children.isEmpty() && super.children.getFirst() instanceof ConfigNodeSingleToken cnst &&
                cnst.token() == StaticToken.OPEN_CURLY;
        ArrayList<AbstractConfigNode> newNodes = new ArrayList<>(indentation);
        newNodes.add(desiredPath.first());
        newNodes.add(new ConfigNodeSingleToken(new TokenWithOrigin.IgnoredWhitespace(null, " ")));
        newNodes.add(new ConfigNodeSingleToken(StaticToken.COLON));
        newNodes.add(new ConfigNodeSingleToken(new TokenWithOrigin.IgnoredWhitespace(null, " ")));

        if (path.length() == 1) {
            newNodes.add(indentedValue);
        } else {
            // If the path is of length greater than one add the required new objects along the path
            ArrayList<AbstractConfigNode> newObjectNodes = new ArrayList<>();
            newObjectNodes.add(new ConfigNodeSingleToken(StaticToken.OPEN_CURLY));
            if (indentation.isEmpty()) {
                newObjectNodes.add(new ConfigNodeSingleToken(new TokenWithOrigin.Line(null)));
            }
            newObjectNodes.addAll(indentation);
            newObjectNodes.add(new ConfigNodeSingleToken(StaticToken.CLOSE_CURLY));
            ConfigNodeObject newObject = new ConfigNodeObject(newObjectNodes);
            newNodes.add(newObject.addValueOnPath(desiredPath.subPath(1), indentedValue, flavor));
        }

        // Combine these two cases so that we only have to iterate once
        if (flavor == ConfigSyntax.JSON || startsWithBrace || sameLine) {
            for (int i = childrenCopy.size() - 1; i >= 0; i--) {

                // If we are in JSON or are adding a setting on the same line, we need to add a comma to the
                // last setting
                if ((flavor == ConfigSyntax.JSON || sameLine) && childrenCopy.get(i) instanceof ConfigNodeField) {
                    if (i + 1 >= childrenCopy.size() ||
                            !(childrenCopy.get(i + 1) instanceof ConfigNodeSingleToken cnst
                                    && cnst.token() == StaticToken.COMMA)) {
                        childrenCopy.add(i + 1, new ConfigNodeSingleToken(StaticToken.COMMA));
                    }
                    break;
                }

                // Add the value into the copy of the children map, keeping any whitespace/newlines
                // before the close curly brace
                if (startsWithBrace && childrenCopy.get(i) instanceof ConfigNodeSingleToken cnst &&
                        cnst.token == StaticToken.CLOSE_CURLY) {
                    AbstractConfigNode previous = childrenCopy.get(i - 1);
                    if (previous instanceof ConfigNodeSingleToken prevcnst &&
                            prevcnst.token() instanceof TokenWithOrigin.Line) {
                        childrenCopy.add(i - 1, new ConfigNodeField(newNodes));
                        i--;
                    } else if (previous instanceof ConfigNodeSingleToken prevnst &&
                            prevnst.token() instanceof TokenWithOrigin.IgnoredWhitespace) {
                        AbstractConfigNode beforePrevious = childrenCopy.get(i - 2);
                        if (sameLine) {
                            childrenCopy.add(i - 1, new ConfigNodeField(newNodes));
                            i--;
                        } else if (beforePrevious instanceof ConfigNodeSingleToken befPrevnst &&
                                befPrevnst.token() instanceof TokenWithOrigin.Line) {
                            childrenCopy.add(i - 2, new ConfigNodeField(newNodes));
                            i -= 2;
                        } else {
                            childrenCopy.add(i, new ConfigNodeField(newNodes));
                        }

                    } else
                        childrenCopy.add(i, new ConfigNodeField(newNodes));
                }
            }
        }
        if (!startsWithBrace) {
            if (!childrenCopy.isEmpty() && childrenCopy.getLast() instanceof ConfigNodeSingleToken cnst &&
                    cnst.token() instanceof TokenWithOrigin.Line)
                childrenCopy.add(childrenCopy.size() - 1, new ConfigNodeField(newNodes));
            else
                childrenCopy.add(new ConfigNodeField(newNodes));
        }
        return new ConfigNodeObject(childrenCopy);
    }

    public ConfigNodeObject removeValueOnPath(String desiredPath, ConfigSyntax flavor) {
        Path path = PathParser.parsePathNode(desiredPath, flavor).value();
        return changeValueOnPath(path, null, flavor);
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitObject(this);
    }
}
