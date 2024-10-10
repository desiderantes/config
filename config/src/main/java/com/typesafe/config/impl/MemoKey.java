package com.typesafe.config.impl;

/**
 * The key used to memoize already-traversed nodes when resolving substitutions
 */
record MemoKey(AbstractConfigValue value, Path restrictToChildOrNull) {

    @Override
    public boolean equals(Object other) {
        if (other instanceof MemoKey o) {
            if (o.value != this.value)
                return false;
            else if (o.restrictToChildOrNull == this.restrictToChildOrNull)
                return true;
            else if (o.restrictToChildOrNull == null || this.restrictToChildOrNull == null)
                return false;
            else
                return o.restrictToChildOrNull.equals(this.restrictToChildOrNull);
        } else {
            return false;
        }
    }
}
