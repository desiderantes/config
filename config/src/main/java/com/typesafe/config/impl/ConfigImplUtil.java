/**
 * Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigSyntax;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal implementation detail, not ABI stable, do not touch.
 * For use only by the {@link com.typesafe.config} package.
 */
final public class ConfigImplUtil {
    static boolean equalsHandlingNull(Object a, Object b) {
        if (a == null && b != null)
            return false;
        else if (a != null && b == null)
            return false;
        else if (a == b) // catches null == null plus optimizes identity case
            return true;
        else
            return a.equals(b);
    }

    static boolean isC0Control(int codepoint) {
        return (codepoint >= 0x0000 && codepoint <= 0x001F);
    }

    public static String renderJsonString(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (isC0Control(c))
                        sb.append(String.format("\\u%04x", (int) c));
                    else
                        sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    static String renderStringUnquotedIfPossible(String s) {
        // this can quote unnecessarily as long as it never fails to quote when
        // necessary
        if (s.isEmpty())
            return renderJsonString(s);

        // if it starts with a hyphen or number, we have to quote
        // to ensure we end up with a string and not a number
        int first = s.codePointAt(0);
        if (Character.isDigit(first) || first == '-')
            return renderJsonString(s);

        if (s.startsWith("include") || s.startsWith("true") || s.startsWith("false")
                || s.startsWith("null") || s.contains("//"))
            return renderJsonString(s);

        // only unquote if it's pure alphanumeric
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (!(Character.isLetter(c) || Character.isDigit(c) || c == '-'))
                return renderJsonString(s);
        }

        return s;
    }

    static boolean isWhitespace(int codepoint) {
        return switch (codepoint) {
            // try to hit the most common ASCII ones first, then the nonbreaking
            // spaces that Java brokenly leaves out of isWhitespace.
            // this one is the BOM, see
            // http://www.unicode.org/faq/utf_bom.html#BOM
            // we just accept it as a zero-width nonbreaking space.
            case ' ', '\n', '\u00A0', '\u2007', '\u202F', '\uFEFF' -> true;
            default -> Character.isWhitespace(codepoint);
        };
    }

    public static String unicodeTrim(String s) {
        // this is dumb because it looks like there aren't any whitespace
        // characters that need surrogate encoding. But, points for
        // pedantic correctness! It's future-proof or something.
        // String.trim() actually is broken, since there are plenty of
        // non-ASCII whitespace characters.
        final int length = s.length();
        if (length == 0)
            return s;

        int start = 0;
        while (start < length) {
            char c = s.charAt(start);
            if (c == ' ' || c == '\n') {
                start += 1;
            } else {
                int cp = s.codePointAt(start);
                if (isWhitespace(cp))
                    start += Character.charCount(cp);
                else
                    break;
            }
        }

        int end = length;
        while (end > start) {
            char c = s.charAt(end - 1);
            if (c == ' ' || c == '\n') {
                --end;
            } else {
                int cp;
                int delta;
                if (Character.isLowSurrogate(c)) {
                    cp = s.codePointAt(end - 2);
                    delta = 2;
                } else {
                    cp = s.codePointAt(end - 1);
                    delta = 1;
                }
                if (isWhitespace(cp))
                    end -= delta;
                else
                    break;
            }
        }
        return s.substring(start, end);
    }


    public static ConfigException extractInitializerError(ExceptionInInitializerError e) {
        Throwable cause = e.getCause();
        if (cause instanceof ConfigException configCause) {
            return configCause;
        } else {
            throw e;
        }
    }

    static File uriToFile(URI uri) {
        // this isn't really right, clearly, but not sure what to do.
        try {
            // this will properly handle hex escapes, etc.
            return new File(uri);
        } catch (IllegalArgumentException e) {
            // file://foo with double slash causes
            // IllegalArgumentException "url has an authority component"
            return new File(uri.getPath());
        }
    }

    static File urlToFile(URL url) {
        // this isn't really right, clearly, but not sure what to do.
        try {
            // this will properly handle hex escapes, etc.
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            // this handles some stuff like file:///c:/Whatever/
            // apparently but mangles handling of hex escapes
            return new File(url.getPath());
        } catch (IllegalArgumentException e) {
            // file://foo with double slash causes
            // IllegalArgumentException "url has an authority component"
            return new File(url.getPath());
        }
    }

    public static String joinPath(String... elements) {
        return (Path.of(elements)).render();
    }

    public static String joinPath(List<String> elements) {
        return joinPath(elements.toArray(new String[0]));
    }

    public static List<String> splitPath(String path) {
        Path p = Path.newPath(path);
        List<String> elements = new ArrayList<>();
        while (p != null) {
            elements.add(p.first());
            p = p.remainder();
        }
        return elements;
    }

    public static ConfigOrigin readOrigin(ObjectInputStream in) throws IOException {
        return SerializedConfigValue.readOrigin(in, null);
    }

    public static void writeOrigin(ObjectOutputStream out, ConfigOrigin origin) throws IOException {
        SerializedConfigValue.writeOrigin(new DataOutputStream(out), (SimpleConfigOrigin) origin,
                null);
    }

    static String toCamelCase(String originalName) {
        String[] words = originalName.split("-+");
        StringBuilder nameBuilder = new StringBuilder(originalName.length());
        for (String word : words) {
            if (nameBuilder.isEmpty()) {
                nameBuilder.append(word);
            } else {
                nameBuilder.append(word.substring(0, 1).toUpperCase());
                nameBuilder.append(word.substring(1));
            }
        }
        return nameBuilder.toString();
    }

    private static char underscoreMappings(int num) {
        // Rationale on name mangling:
        //
        // Most shells (e.g. bash, sh, etc.) doesn't support any character other
        // than alphanumeric and `_` in environment variables names.
        // In HOCON the default separator is `.` so it is directly translated to a
        // single `_` for convenience; `-` and `_` are less often present in config
        // keys but they have to be representable and the only possible mapping is
        // `_` repeated.
        return switch (num) {
            case 1 -> '.';
            case 2 -> '-';
            case 3 -> '_';
            default -> 0;
        };
    }

    static String envVariableAsProperty(String variable, String prefix) throws ConfigException {
        StringBuilder builder = new StringBuilder();

        String strippedPrefix = variable.substring(prefix.length());

        int underscores = 0;
        for (char c : strippedPrefix.toCharArray()) {
            if (c == '_') {
                underscores++;
            } else {
                if (underscores > 0 && underscores < 4) {
                    builder.append(underscoreMappings(underscores));
                } else if (underscores > 3) {
                    throw new ConfigException.BadPath(variable, "Environment variable contains an un-mapped number of underscores.");
                }
                underscores = 0;
                builder.append(c);
            }
        }

        if (underscores > 0 && underscores < 4) {
            builder.append(underscoreMappings(underscores));
        } else if (underscores > 3) {
            throw new ConfigException.BadPath(variable, "Environment variable contains an un-mapped number of underscores.");
        }

        return builder.toString();
    }

    /**
     * Guess configuration syntax from given filename.
     *
     * @param filename configuration filename
     * @return configuration syntax if a match is found. Otherwise, null.
     */
    public static ConfigSyntax syntaxFromExtension(String filename) {
        if (filename == null) {
            return null;
        } else if (filename.endsWith(".json")) {
            return ConfigSyntax.JSON;
        } else if (filename.endsWith(".conf")) {
            return ConfigSyntax.CONF;
        } else if (filename.endsWith(".properties")) {
            return ConfigSyntax.PROPERTIES;
        } else
            return null;
    }
}
