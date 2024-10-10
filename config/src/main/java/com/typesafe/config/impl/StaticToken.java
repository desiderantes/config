package com.typesafe.config.impl;

public enum StaticToken implements Token {

    START("", "beginning of file"),
    END("", "end of file"),
    COMMA(",", "','"),
    EQUALS("=", "'='"),
    COLON(":", "':'"),
    OPEN_CURLY("{", "'{'"),
    CLOSE_CURLY("}", "'}'"),
    OPEN_SQUARE("[", "'['"),
    CLOSE_SQUARE("]", "']'"),
    PLUS_EQUALS("+=", "'+='");

    private final String debugString;
    private final String tokenText;

    StaticToken(String tokenText, String debugString) {
        this.tokenText = tokenText;
        this.debugString = debugString;
    }

    @Override
    public String tokenText() {
        return tokenText;
    }

    @Override
    public int lineNumber() {
        return -1;
    }

    @Override
    public String toString() {
        return debugString;
    }
}
