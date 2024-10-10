package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// value is allowed to be null
record ResolveResult<V extends AbstractConfigValue>(@NotNull ResolveContext context, @Nullable V value) {

    static <V extends AbstractConfigValue> ResolveResult<V> make(ResolveContext context, V value) {
        return new ResolveResult<>(context, value);
    }

    // better option? we don't have variance
    @SuppressWarnings("unchecked")
    ResolveResult<AbstractConfigObject> asObjectResult() {
        if (!(value instanceof AbstractConfigObject))
            throw new ConfigException.BugOrBroken("Expecting a resolve result to be an object, but it was " + value);
        Object o = this;
        return (ResolveResult<AbstractConfigObject>) o;
    }

    // better option? we don't have variance
    @SuppressWarnings("unchecked")
    ResolveResult<AbstractConfigValue> asValueResult() {
        Object o = this;
        return (ResolveResult<AbstractConfigValue>) o;
    }

    ResolveResult<V> popTrace() {
        return new ResolveResult<>(context.popTrace(), value);
    }

    @Override
    public String toString() {
        return "ResolveResult(" + value + ")";
    }
}
