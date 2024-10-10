package com.typesafe.config.impl;

record SubstitutionExpression(Path path, boolean optional) {

    SubstitutionExpression changePath(Path newPath) {
        if (newPath == path) {
            return this;
        } else {
            return new SubstitutionExpression(newPath, optional);
        }
    }

    @Override
    public String toString() {
        return "${" + (optional ? "?" : "") + path.render() + "}";
    }

}
