/**
 * Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

sealed interface Token permits StaticToken, TokenWithOrigin {
    String tokenText();

    int lineNumber();

    String toString();
}
