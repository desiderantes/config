/**
 * Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The issue here is that we want to first merge our stack of config files, and
 * then we want to evaluate substitutions. But if two substitutions both expand
 * to an object, we might need to merge those two objects. Thus, we can't ever
 * "override" a substitution when we do a merge; instead we have to save the
 * stack of values that should be merged, and resolve the merge when we evaluate
 * substitutions.
 */
final class ConfigDelayedMerge extends AbstractConfigValue implements Unmergeable,
        ReplaceableMergeStack {

    // earlier items in the stack win
    final private List<AbstractConfigValue> stack;

    ConfigDelayedMerge(ConfigOrigin origin, List<AbstractConfigValue> stack) {
        super(origin);
        this.stack = stack;
        if (stack.isEmpty())
            throw new ConfigException.BugOrBroken(
                    "creating empty delayed merge value");

        for (AbstractConfigValue v : stack) {
            if (v instanceof ConfigDelayedMerge || v instanceof ConfigDelayedMergeObject)
                throw new ConfigException.BugOrBroken(
                        "placed nested DelayedMerge in a ConfigDelayedMerge, should have consolidated stack");
        }
    }

    /**
     * Optimize loading of large configs with many += array builders:
     * 1. Parsing time in one case down from 14sec to 40ms...
     * 2. non-recursive - no StackOverflowError
     */
    static SimpleConfigList tryResolveSubstitutionsNonRecursiveOptimisticConfigList(
            List<AbstractConfigValue> stack,
            ConfigOrigin origin
    ) {
        Object prevRef = null;
        SimpleConfigList acc = null;
        for (int i = stack.size() - 1; i >= 0; i--) {
            AbstractConfigValue end = stack.get(i);
            if (end instanceof ConfigConcatenation) {
                List<AbstractConfigValue> pieces = ((ConfigConcatenation) end).pieces;
                if (pieces.size() == 2) {
                    AbstractConfigValue left = pieces.get(0);
                    AbstractConfigValue right = pieces.get(1);
                    if (left instanceof ConfigReference && right instanceof SimpleConfigList) {
                        if (prevRef == null || prevRef.equals(left)) {
                            if (acc == null) {
                                acc = new SimpleConfigList(origin, Collections.emptyList());
                            }
                            acc = acc.concatenate(((SimpleConfigList) right));
                            prevRef = left;
                            continue;
                        }
                    }
                }
            }
            return null;
        }

        return acc;
    }

    // static method also used by ConfigDelayedMergeObject
    static ResolveResult<? extends AbstractConfigValue> resolveSubstitutions(ReplaceableMergeStack replaceable,
                                                                             List<AbstractConfigValue> stack,
                                                                             ResolveContext context, ResolveSource source) throws NotPossibleToResolve {
        if (ConfigImpl.traceSubstitutionsEnabled()) {
            ConfigImpl.trace(context.depth(), "delayed merge stack has " + stack.size() + " items:");
            int count = 0;
            for (AbstractConfigValue v : stack) {
                ConfigImpl.trace(context.depth() + 1, count + ": " + v);
                count += 1;
            }
        }

        // to resolve substitutions, we need to recursively resolve
        // the stack of stuff to merge, and merge the stack so
        // we won't be a delayed merge anymore. If restrictToChildOrNull
        // is non-null, or resolve options allow partial resolves,
        // we may remain a delayed merge though.

        ResolveContext newContext = context;
        int count = 0;
        AbstractConfigValue merged = null;
        for (AbstractConfigValue end : stack) {
            // the end value may or may not be resolved already

            ResolveSource sourceForEnd;

            if (end instanceof ReplaceableMergeStack)
                throw new ConfigException.BugOrBroken("A delayed merge should not contain another one: " + replaceable);
            else if (end instanceof Unmergeable) {
                // the remainder could be any kind of value, including another
                // ConfigDelayedMerge
                AbstractConfigValue remainder = replaceable.makeReplacement(context, count + 1);

                if (ConfigImpl.traceSubstitutionsEnabled())
                    ConfigImpl.trace(newContext.depth(), "remainder portion: " + remainder);

                // If, while resolving 'end' we come back to the same
                // merge stack, we only want to look _below_ 'end'
                // in the stack. So we arrange to replace the
                // ConfigDelayedMerge with a value that is only
                // the remainder of the stack below this one.

                if (ConfigImpl.traceSubstitutionsEnabled())
                    ConfigImpl.trace(newContext.depth(), "building sourceForEnd");

                // we resetParents() here because we'll be resolving "end"
                // against a root which does NOT contain "end"
                sourceForEnd = source.replaceWithinCurrentParent((AbstractConfigValue) replaceable, remainder);

                if (ConfigImpl.traceSubstitutionsEnabled())
                    ConfigImpl.trace(newContext.depth(), "  sourceForEnd before reset parents but after replace: "
                            + sourceForEnd);

                sourceForEnd = sourceForEnd.resetParents();
            } else {
                if (ConfigImpl.traceSubstitutionsEnabled())
                    ConfigImpl.trace(newContext.depth(),
                            "will resolve end against the original source with parent pushed");

                sourceForEnd = source.pushParent(replaceable);
            }

            if (ConfigImpl.traceSubstitutionsEnabled()) {
                ConfigImpl.trace(newContext.depth(), "sourceForEnd      =" + sourceForEnd);
            }

            if (ConfigImpl.traceSubstitutionsEnabled())
                ConfigImpl.trace(newContext.depth(), "Resolving highest-priority item in delayed merge " + end
                        + " against " + sourceForEnd + " endWasRemoved=" + (source != sourceForEnd));
            ResolveResult<? extends AbstractConfigValue> result = newContext.resolve(end, sourceForEnd);
            AbstractConfigValue resolvedEnd = result.value();
            newContext = result.context();

            if (resolvedEnd != null) {
                if (merged == null) {
                    merged = resolvedEnd;
                } else {
                    if (ConfigImpl.traceSubstitutionsEnabled())
                        ConfigImpl.trace(newContext.depth() + 1, "merging " + merged + " with fallback " + resolvedEnd);
                    merged = merged.withFallback(resolvedEnd);
                }
            }

            count += 1;

            if (ConfigImpl.traceSubstitutionsEnabled())
                ConfigImpl.trace(newContext.depth(), "stack merged, yielding: " + merged);
        }

        return ResolveResult.make(newContext, merged);
    }

    // static method also used by ConfigDelayedMergeObject; end may be null
    static AbstractConfigValue makeReplacement(ResolveContext context, List<AbstractConfigValue> stack, int skipping) {
        List<AbstractConfigValue> subStack = stack.subList(skipping, stack.size());

        if (subStack.isEmpty()) {
            if (ConfigImpl.traceSubstitutionsEnabled())
                ConfigImpl.trace(context.depth(), "Nothing else in the merge stack, replacing with null");
            return null;
        } else {
            // generate a new merge stack from only the remaining items
            AbstractConfigValue merged = null;
            for (AbstractConfigValue v : subStack) {
                if (merged == null)
                    merged = v;
                else
                    merged = merged.withFallback(v);
            }
            return merged;
        }
    }

    // static utility shared with ConfigDelayedMergeObject
    static boolean stackIgnoresFallbacks(List<AbstractConfigValue> stack) {
        AbstractConfigValue last = stack.getLast();
        return last.ignoresFallbacks();
    }

    // static method also used by ConfigDelayedMergeObject.
    static void render(List<AbstractConfigValue> stack, StringBuilder sb, int indent, boolean atRoot, String atKey,
                       ConfigRenderOptions options) {
        boolean commentMerge = options.getComments();
        if (commentMerge) {
            sb.append("# unresolved merge of ").append(stack.size()).append(" values follows (\n");
            if (atKey == null) {
                indent(sb, indent, options);
                sb.append("# this unresolved merge will not be parseable because it's at the root of the object\n");
                indent(sb, indent, options);
                sb.append("# the HOCON format has no way to list multiple root objects in a single file\n");
            }
        }

        List<AbstractConfigValue> reversed = new ArrayList<>(stack);
        Collections.reverse(reversed);

        int i = 0;
        for (AbstractConfigValue v : reversed) {
            if (commentMerge) {
                indent(sb, indent, options);
                if (atKey != null) {
                    sb.append("#     unmerged value ").append(i).append(" for key ").append(ConfigImplUtil.renderJsonString(atKey)).append(" from ");
                } else {
                    sb.append("#     unmerged value ").append(i).append(" from ");
                }
                i += 1;
                sb.append(v.origin().description());
                sb.append("\n");

                for (String comment : v.origin().comments()) {
                    indent(sb, indent, options);
                    sb.append("# ");
                    sb.append(comment);
                    sb.append("\n");
                }
            }
            indent(sb, indent, options);

            if (atKey != null) {
                sb.append(ConfigImplUtil.renderJsonString(atKey));
                if (options.getFormatted())
                    sb.append(" : ");
                else
                    sb.append(":");
            }
            v.render(sb, indent, atRoot, options);
            sb.append(",");
            if (options.getFormatted())
                sb.append('\n');
        }
        // chop comma or newline
        sb.setLength(sb.length() - 1);
        if (options.getFormatted()) {
            sb.setLength(sb.length() - 1); // also chop comma
            sb.append("\n"); // put a newline back
        }
        if (commentMerge) {
            indent(sb, indent, options);
            sb.append("# ) end of unresolved merge\n");
        }
    }

    @Override
    public ConfigValueType valueType() {
        throw new ConfigException.NotResolved(
                "called valueType() on value with unresolved substitutions, need to Config#resolve() first, see API docs");
    }

    @Override
    public Object unwrapped() {
        throw new ConfigException.NotResolved(
                "called unwrapped() on value with unresolved substitutions, need to Config#resolve() first, see API docs");
    }

    @Override
    ResolveResult<? extends AbstractConfigValue> resolveSubstitutions(ResolveContext context, ResolveSource source)
            throws NotPossibleToResolve {
        SimpleConfigList nonRecursiveList = tryResolveSubstitutionsNonRecursiveOptimisticConfigList(stack, origin());
        if (nonRecursiveList != null) {
            return ResolveResult.make(context, nonRecursiveList);
        }
        return resolveSubstitutions(this, stack, context, source);
    }

    @Override
    public AbstractConfigValue makeReplacement(ResolveContext context, int skipping) {
        return ConfigDelayedMerge.makeReplacement(context, stack, skipping);
    }

    @Override
    ResolveStatus resolveStatus() {
        return ResolveStatus.UNRESOLVED;
    }

    @Override
    public AbstractConfigValue replaceChild(AbstractConfigValue child, AbstractConfigValue replacement) {
        List<AbstractConfigValue> newStack = replaceChildInList(stack, child, replacement);
        if (newStack == null)
            return null;
        else
            return new ConfigDelayedMerge(origin(), newStack);
    }

    @Override
    public boolean hasDescendant(AbstractConfigValue descendant) {
        return hasDescendantInList(stack, descendant);
    }

    @Override
    ConfigDelayedMerge relativized(Path prefix) {
        List<AbstractConfigValue> newStack = new ArrayList<>();
        for (AbstractConfigValue o : stack) {
            newStack.add(o.relativized(prefix));
        }
        return new ConfigDelayedMerge(origin(), newStack);
    }

    @Override
    protected boolean ignoresFallbacks() {
        return stackIgnoresFallbacks(stack);
    }

    @Override
    protected AbstractConfigValue newCopy(ConfigOrigin newOrigin) {
        return new ConfigDelayedMerge(newOrigin, stack);
    }

    @Override
    protected ConfigDelayedMerge mergedWithTheUnmergeable(Unmergeable fallback) {
        return (ConfigDelayedMerge) mergedWithTheUnmergeable(stack, fallback);
    }

    @Override
    protected ConfigDelayedMerge mergedWithObject(AbstractConfigObject fallback) {
        return (ConfigDelayedMerge) mergedWithObject(stack, fallback);
    }

    @Override
    protected ConfigDelayedMerge mergedWithNonObject(AbstractConfigValue fallback) {
        return (ConfigDelayedMerge) mergedWithNonObject(stack, fallback);
    }

    @Override
    public Collection<AbstractConfigValue> unmergedValues() {
        return stack;
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof ConfigDelayedMerge;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof ConfigDelayedMerge) {
            return canEqual(other)
                    && (this.stack == ((ConfigDelayedMerge) other).stack || this.stack
                    .equals(((ConfigDelayedMerge) other).stack));
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return stack.hashCode();
    }

    @Override
    protected void render(StringBuilder sb, int indent, boolean atRoot, String atKey, ConfigRenderOptions options) {
        render(stack, sb, indent, atRoot, atKey, options);
    }

    @Override
    protected void render(StringBuilder sb, int indent, boolean atRoot, ConfigRenderOptions options) {
        render(sb, indent, atRoot, null, options);
    }
}
