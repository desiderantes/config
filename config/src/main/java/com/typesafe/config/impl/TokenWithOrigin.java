/**
 * Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;

import java.util.List;
import java.util.Objects;

abstract sealed class TokenWithOrigin implements Token {
    final private String debugString;
    final private ConfigOrigin origin;
    final private String tokenText;

    TokenWithOrigin(ConfigOrigin origin) {
        this(origin, null);
    }

    TokenWithOrigin(ConfigOrigin origin, String tokenText) {
        this(origin, tokenText, null);
    }

    TokenWithOrigin(ConfigOrigin origin, String tokenText, String debugString) {
        this.origin = origin;
        this.debugString = debugString;
        this.tokenText = tokenText;
    }

    public String tokenText() {
        return tokenText;
    }

    // this is final because we don't always use the origin() accessor,
    // and we don't because it throws if origin is null
    final ConfigOrigin origin() {
        // code is only supposed to call origin() on token types that are
        // expected to have an origin.
        if (origin == null)
            throw new ConfigException.BugOrBroken(
                    "tried to get origin from token that doesn't have one: " + this);
        return origin;
    }

    public final int lineNumber() {
        if (origin != null)
            return origin.lineNumber();
        else
            return -1;
    }

    @Override
    public String toString() {
        return Objects.requireNonNullElseGet(debugString, () -> this.getClass().getSimpleName());
    }

    protected boolean canEqual(Object other) {
        return other instanceof Token;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Token token) {
            // origin is deliberately left out
            return canEqual(other)
                    && this.getClass().getSimpleName().equals(token.getClass().getSimpleName());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // origin is deliberately left out
        return this.getClass().getSimpleName().hashCode();
    }

    static final class Value extends TokenWithOrigin {

        final private AbstractConfigValue value;

        Value(AbstractConfigValue value) {
            this(value, null);
        }

        Value(AbstractConfigValue value, String origText) {
            super(value.origin(), origText);
            this.value = value;
        }

        static Value newString(ConfigOrigin origin, String value, String origText) {
            return new Value(new ConfigString.Quoted(origin, value), origText);
        }

        static Value newInt(ConfigOrigin origin, int value, String origText) {
            return new Value(ConfigNumber.newNumber(origin, value,
                    origText), origText);
        }

        static Value newDouble(ConfigOrigin origin, double value,
                               String origText) {
            return new Value(ConfigNumber.newNumber(origin, value,
                    origText), origText);
        }

        static Value newLong(ConfigOrigin origin, long value, String origText) {
            return new Value(ConfigNumber.newNumber(origin, value,
                    origText), origText);
        }

        static Value newNull(ConfigOrigin origin) {
            return new Value(new ConfigNull(origin), "null");
        }

        static Value newBoolean(ConfigOrigin origin, boolean value) {
            return new Value(new ConfigBoolean(origin, value), "" + value);
        }

        AbstractConfigValue value() {
            return value;
        }

        @Override
        public String toString() {
            if (value().resolveStatus() == ResolveStatus.RESOLVED)
                return "'" + value().unwrapped() + "' (" + value.valueType().name() + ")";
            else
                return "'<unresolved value>' (" + value.valueType().name() + ")";
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof Value;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other) && ((Value) other).value.equals(value);
        }

        @Override
        public int hashCode() {
            return 41 * (41 + super.hashCode()) + value.hashCode();
        }
    }

    static final class Line extends TokenWithOrigin {
        Line(ConfigOrigin origin) {
            super(origin);
        }

        @Override
        public String toString() {
            return "'\\n'@" + lineNumber();
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof Line;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other) && ((Line) other).lineNumber() == lineNumber();
        }

        @Override
        public int hashCode() {
            return 41 * (41 + super.hashCode()) + lineNumber();
        }

        @Override
        public String tokenText() {
            return "\n";
        }
    }

    // This is not a Value, because it requires special processing
    static final class UnquotedText extends TokenWithOrigin {
        final private String value;

        UnquotedText(ConfigOrigin origin, String s) {
            super(origin);
            this.value = s;
        }

        String value() {
            return value;
        }

        @Override
        public String toString() {
            return "'" + value + "'";
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof UnquotedText;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other)
                    && ((UnquotedText) other).value.equals(value);
        }

        @Override
        public int hashCode() {
            return 41 * (41 + super.hashCode()) + value.hashCode();
        }

        @Override
        public String tokenText() {
            return value;
        }
    }

    static final class IgnoredWhitespace extends TokenWithOrigin {
        final private String value;

        IgnoredWhitespace(ConfigOrigin origin, String s) {
            super(origin);
            this.value = s;
        }

        @Override
        public String toString() {
            return "'" + value + "' (WHITESPACE)";
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof IgnoredWhitespace;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other)
                    && ((IgnoredWhitespace) other).value.equals(value);
        }

        @Override
        public int hashCode() {
            return 41 * (41 + super.hashCode()) + value.hashCode();
        }

        @Override
        public String tokenText() {
            return value;
        }
    }

    static final class Problem extends TokenWithOrigin {
        final private String what;
        final private String message;
        final private boolean suggestQuotes;
        final private Throwable cause;

        Problem(ConfigOrigin origin, String what, String message, boolean suggestQuotes,
                Throwable cause) {
            super(origin);
            this.what = what;
            this.message = message;
            this.suggestQuotes = suggestQuotes;
            this.cause = cause;
        }

        String what() {
            return what;
        }

        String message() {
            return message;
        }

        boolean suggestQuotes() {
            return suggestQuotes;
        }

        Throwable cause() {
            return cause;
        }

        @Override
        public String toString() {
            String sb = '\'' +
                    what +
                    '\'' +
                    " (" +
                    message +
                    ")";
            return sb;
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof Problem;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other) && ((Problem) other).what.equals(what)
                    && ((Problem) other).message.equals(message)
                    && ((Problem) other).suggestQuotes == suggestQuotes
                    && ConfigImplUtil.equalsHandlingNull(((Problem) other).cause, cause);
        }

        @Override
        public int hashCode() {
            int h = 41 * (41 + super.hashCode());
            h = 41 * (h + what.hashCode());
            h = 41 * (h + message.hashCode());
            h = 41 * (h + Boolean.valueOf(suggestQuotes).hashCode());
            if (cause != null)
                h = 41 * (h + cause.hashCode());
            return h;
        }
    }

    static abstract sealed class Comment extends TokenWithOrigin {
        final private String text;

        Comment(ConfigOrigin origin, String text) {
            super(origin);
            this.text = text;
        }

        String text() {
            return text;
        }

        @Override
        public String toString() {
            return "'#" +
                    text +
                    "' (COMMENT)";
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof Comment;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other) && ((Comment) other).text.equals(text);
        }

        @Override
        public int hashCode() {
            int h = 41 * (41 + super.hashCode());
            h = 41 * (h + text.hashCode());
            return h;
        }

        final static class DoubleSlashComment extends Comment {
            DoubleSlashComment(ConfigOrigin origin, String text) {
                super(origin, text);
            }

            @Override
            public String tokenText() {
                return "//" + super.text;
            }
        }

        final static class HashComment extends Comment {
            HashComment(ConfigOrigin origin, String text) {
                super(origin, text);
            }

            @Override
            public String tokenText() {
                return "#" + super.text;
            }
        }
    }

    // This is not a Value, because it requires special processing
    static final class Substitution extends TokenWithOrigin {
        final private boolean optional;
        final private List<Token> value;

        Substitution(ConfigOrigin origin, boolean optional, List<Token> expression) {
            super(origin);
            this.optional = optional;
            this.value = expression;
        }

        boolean optional() {
            return optional;
        }

        List<Token> value() {
            return value;
        }

        @Override
        public String tokenText() {
            return "${" + (this.optional ? "?" : "") + Tokenizer.render(this.value.iterator()) + "}";
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Token t : value) {
                sb.append(t.toString());
            }
            return "'${" + sb + "}'";
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof Substitution;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other)
                    && ((Substitution) other).value.equals(value);
        }

        @Override
        public int hashCode() {
            return 41 * (41 + super.hashCode()) + value.hashCode();
        }
    }


}
