/**
 * Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.impl.SerializedConfigValue.SerializedField;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

// it would be cleaner to have a class hierarchy for various origin types,
// but was hoping this would be enough simpler to be a little messy. eh.
final class SimpleConfigOrigin implements ConfigOrigin, Serializable {

    static final String MERGE_OF_PREFIX = "merge of ";
    final private String description;
    final private int lineNumber;
    final private int endLineNumber;
    final private OriginType originType;
    final private String urlOrNull;
    final private String resourceOrNull;
    final private List<String> commentsOrNull;

    protected SimpleConfigOrigin(String description, int lineNumber, int endLineNumber, OriginType originType,
                                 String urlOrNull, String resourceOrNull, List<String> commentsOrNull) {
        if (description == null)
            throw new ConfigException.BugOrBroken("description may not be null");
        this.description = description;
        this.lineNumber = lineNumber;
        this.endLineNumber = endLineNumber;
        this.originType = originType;
        this.urlOrNull = urlOrNull;
        this.resourceOrNull = resourceOrNull;
        this.commentsOrNull = commentsOrNull;
    }

    static SimpleConfigOrigin newSimple(String description) {
        return new SimpleConfigOrigin(description, -1, -1, OriginType.GENERIC, null, null, null);
    }

    static SimpleConfigOrigin newFile(String filename) {
        String url = new File(filename).toURI().toASCIIString();
        return new SimpleConfigOrigin(filename, -1, -1, OriginType.FILE, url, null, null);
    }

    static SimpleConfigOrigin newURL(URI url) {
        String u = url.toASCIIString();
        return new SimpleConfigOrigin(u, -1, -1, OriginType.URI, u, null, null);
    }

    static SimpleConfigOrigin newResource(String resource, URI url) {
        String desc;
        if (url != null) {
            desc = resource + " @ " + url.toASCIIString();
        } else {
            desc = resource;
        }
        return new SimpleConfigOrigin(desc, -1, -1, OriginType.RESOURCE, url != null ? url.toASCIIString() : null,
                resource, null);
    }

    static SimpleConfigOrigin newResource(String resource) {
        return newResource(resource, null);
    }

    static SimpleConfigOrigin newEnvVariable(String description) {
        return new SimpleConfigOrigin(description, -1, -1, OriginType.ENV_VARIABLE, null, null, null);
    }

    private static SimpleConfigOrigin mergeTwo(SimpleConfigOrigin a, SimpleConfigOrigin b) {
        String mergedDesc;
        int mergedStartLine;
        int mergedEndLine;
        List<String> mergedComments;

        OriginType mergedType;
        if (a.originType == b.originType) {
            mergedType = a.originType;
        } else {
            mergedType = OriginType.GENERIC;
        }

        // first use the "description" field which has no line numbers
        // cluttering it.
        String aDesc = a.description;
        String bDesc = b.description;
        if (aDesc.startsWith(MERGE_OF_PREFIX))
            aDesc = aDesc.substring(MERGE_OF_PREFIX.length());
        if (bDesc.startsWith(MERGE_OF_PREFIX))
            bDesc = bDesc.substring(MERGE_OF_PREFIX.length());

        if (aDesc.equals(bDesc)) {
            mergedDesc = aDesc;

            if (a.lineNumber < 0)
                mergedStartLine = b.lineNumber;
            else if (b.lineNumber < 0)
                mergedStartLine = a.lineNumber;
            else
                mergedStartLine = Math.min(a.lineNumber, b.lineNumber);

            mergedEndLine = Math.max(a.endLineNumber, b.endLineNumber);
        } else {
            // this whole merge song-and-dance was intended to avoid this case
            // whenever possible, but we've lost. Now we have to lose some
            // structured information and cram into a string.

            // description() method includes line numbers, so use it instead
            // of description field.
            String aFull = a.description();
            String bFull = b.description();
            if (aFull.startsWith(MERGE_OF_PREFIX))
                aFull = aFull.substring(MERGE_OF_PREFIX.length());
            if (bFull.startsWith(MERGE_OF_PREFIX))
                bFull = bFull.substring(MERGE_OF_PREFIX.length());

            mergedDesc = MERGE_OF_PREFIX + aFull + "," + bFull;

            mergedStartLine = -1;
            mergedEndLine = -1;
        }

        String mergedURL;
        if (ConfigImplUtil.equalsHandlingNull(a.urlOrNull, b.urlOrNull)) {
            mergedURL = a.urlOrNull;
        } else {
            mergedURL = null;
        }

        String mergedResource;
        if (ConfigImplUtil.equalsHandlingNull(a.resourceOrNull, b.resourceOrNull)) {
            mergedResource = a.resourceOrNull;
        } else {
            mergedResource = null;
        }

        if (ConfigImplUtil.equalsHandlingNull(a.commentsOrNull, b.commentsOrNull)) {
            mergedComments = a.commentsOrNull;
        } else {
            mergedComments = new ArrayList<>();
            if (a.commentsOrNull != null)
                mergedComments.addAll(a.commentsOrNull);
            if (b.commentsOrNull != null)
                mergedComments.addAll(b.commentsOrNull);
        }

        return new SimpleConfigOrigin(mergedDesc, mergedStartLine, mergedEndLine, mergedType, mergedURL,
                mergedResource, mergedComments);
    }

    private static int similarity(SimpleConfigOrigin a, SimpleConfigOrigin b) {
        int count = 0;

        if (a.originType == b.originType)
            count += 1;

        if (a.description.equals(b.description)) {
            count += 1;

            // only count these if the description field (which is the file
            // or resource name) also matches.
            if (a.lineNumber == b.lineNumber)
                count += 1;
            if (a.endLineNumber == b.endLineNumber)
                count += 1;
            if (ConfigImplUtil.equalsHandlingNull(a.urlOrNull, b.urlOrNull))
                count += 1;
            if (ConfigImplUtil.equalsHandlingNull(a.resourceOrNull, b.resourceOrNull))
                count += 1;
        }

        return count;
    }

    // this picks the best pair to merge, because the pair has the most in
    // common. we want to merge two lines in the same file rather than something
    // else with one of the lines; because two lines in the same file can be
    // better consolidated.
    private static SimpleConfigOrigin mergeThree(SimpleConfigOrigin a, SimpleConfigOrigin b, SimpleConfigOrigin c) {
        if (similarity(a, b) >= similarity(b, c)) {
            return mergeTwo(mergeTwo(a, b), c);
        } else {
            return mergeTwo(a, mergeTwo(b, c));
        }
    }

    static ConfigOrigin mergeOrigins(ConfigOrigin a, ConfigOrigin b) {
        return mergeTwo((SimpleConfigOrigin) a, (SimpleConfigOrigin) b);
    }

    static ConfigOrigin mergeOrigins(List<? extends AbstractConfigValue> stack) {
        List<ConfigOrigin> origins = new ArrayList<>(stack.size());
        for (AbstractConfigValue v : stack) {
            origins.add(v.origin());
        }
        return mergeOrigins(origins);
    }

    static ConfigOrigin mergeOrigins(Collection<? extends ConfigOrigin> stack) {
        if (stack.isEmpty()) {
            throw new ConfigException.BugOrBroken("can't merge empty list of origins");
        } else if (stack.size() == 1) {
            return stack.iterator().next();
        } else if (stack.size() == 2) {
            Iterator<? extends ConfigOrigin> i = stack.iterator();
            return mergeTwo((SimpleConfigOrigin) i.next(), (SimpleConfigOrigin) i.next());
        } else {
            List<SimpleConfigOrigin> remaining = new ArrayList<>(stack.size());
            for (ConfigOrigin o : stack) {
                remaining.add((SimpleConfigOrigin) o);
            }
            while (remaining.size() > 2) {
                SimpleConfigOrigin c = remaining.getLast();
                remaining.removeLast();
                SimpleConfigOrigin b = remaining.getLast();
                remaining.removeLast();
                SimpleConfigOrigin a = remaining.getLast();
                remaining.removeLast();

                SimpleConfigOrigin merged = mergeThree(a, b, c);

                remaining.add(merged);
            }

            // should be down to either 1 or 2
            return mergeOrigins(remaining);
        }
    }

    // Here we're trying to avoid serializing the same info over and over
    // in the common case that child objects have the same origin fields
    // as their parent objects. e.g. we don't need to store the source
    // filename with every single value.
    static Map<SerializedField, Object> fieldsDelta(Map<SerializedField, Object> base,
                                                    Map<SerializedField, Object> child) {
        Map<SerializedField, Object> m = new EnumMap<>(child);

        for (Map.Entry<SerializedField, Object> baseEntry : base.entrySet()) {
            SerializedField f = baseEntry.getKey();
            if (m.containsKey(f) && ConfigImplUtil.equalsHandlingNull(baseEntry.getValue(), m.get(f))) {
                // if field is unchanged, just remove it so we inherit
                m.remove(f);
            } else if (!m.containsKey(f)) {
                // if field has been removed, we have to add a deletion entry
                switch (f) {
                    case ORIGIN_DESCRIPTION ->
                            throw new ConfigException.BugOrBroken("origin missing description field? " + child);
                    case ORIGIN_LINE_NUMBER -> m.put(SerializedField.ORIGIN_LINE_NUMBER, -1);
                    case ORIGIN_END_LINE_NUMBER -> m.put(SerializedField.ORIGIN_END_LINE_NUMBER, -1);
                    case ORIGIN_TYPE -> throw new ConfigException.BugOrBroken("should always be an ORIGIN_TYPE field");
                    case ORIGIN_URL -> m.put(SerializedField.ORIGIN_NULL_URL, "");
                    case ORIGIN_RESOURCE -> m.put(SerializedField.ORIGIN_NULL_RESOURCE, "");
                    case ORIGIN_COMMENTS -> m.put(SerializedField.ORIGIN_NULL_COMMENTS, "");
                    case ORIGIN_NULL_URL, ORIGIN_NULL_RESOURCE, ORIGIN_NULL_COMMENTS ->
                            throw new ConfigException.BugOrBroken("computing delta, base object should not contain " + f + " "
                                    + base);
                    case END_MARKER, ROOT_VALUE, ROOT_WAS_CONFIG, UNKNOWN, VALUE_DATA, VALUE_ORIGIN ->
                            throw new ConfigException.BugOrBroken("should not appear here: " + f);
                }
            } else {
                // field is in base and child, but differs, so leave it
            }
        }

        return m;
    }

    static SimpleConfigOrigin fromFields(Map<SerializedField, Object> m) throws IOException {
        // we represent a null origin as one with no fields at all
        if (m.isEmpty())
            return null;

        String description = (String) m.get(SerializedField.ORIGIN_DESCRIPTION);
        Integer lineNumber = (Integer) m.get(SerializedField.ORIGIN_LINE_NUMBER);
        Integer endLineNumber = (Integer) m.get(SerializedField.ORIGIN_END_LINE_NUMBER);
        Number originTypeOrdinal = (Number) m.get(SerializedField.ORIGIN_TYPE);
        if (originTypeOrdinal == null)
            throw new IOException("Missing ORIGIN_TYPE field");
        OriginType originType;
        if (originTypeOrdinal.byteValue() < OriginType.values().length)
            originType = OriginType.values()[originTypeOrdinal.byteValue()];
        else
            originType = OriginType.GENERIC; // ENV_VARIABLE was added in a later version
        String urlOrNull = (String) m.get(SerializedField.ORIGIN_URL);
        String resourceOrNull = (String) m.get(SerializedField.ORIGIN_RESOURCE);
        @SuppressWarnings("unchecked")
        List<String> commentsOrNull = (List<String>) m.get(SerializedField.ORIGIN_COMMENTS);
        // Older versions did not have a resource field, they stuffed it into
        // the description.
        if (originType == OriginType.RESOURCE && resourceOrNull == null) {
            resourceOrNull = description;
        }
        return new SimpleConfigOrigin(description, lineNumber != null ? lineNumber : -1,
                endLineNumber != null ? endLineNumber : -1, originType, urlOrNull, resourceOrNull, commentsOrNull);
    }

    static Map<SerializedField, Object> applyFieldsDelta(Map<SerializedField, Object> base,
                                                         Map<SerializedField, Object> delta) throws IOException {

        Map<SerializedField, Object> m = new EnumMap<>(delta);

        for (Map.Entry<SerializedField, Object> baseEntry : base.entrySet()) {
            SerializedField f = baseEntry.getKey();
            if (delta.containsKey(f)) {
                // delta overrides when keys are in both
                // "m" should already contain the right thing
            } else {
                // base has the key and delta does not.
                // we inherit from base unless a "NULL" key blocks.
                switch (f) {
                    case ORIGIN_DESCRIPTION -> m.put(f, base.get(f));
                    case ORIGIN_URL -> {
                        if (delta.containsKey(SerializedField.ORIGIN_NULL_URL)) {
                            m.remove(SerializedField.ORIGIN_NULL_URL);
                        } else {
                            m.put(f, base.get(f));
                        }
                    }
                    case ORIGIN_RESOURCE -> {
                        if (delta.containsKey(SerializedField.ORIGIN_NULL_RESOURCE)) {
                            m.remove(SerializedField.ORIGIN_NULL_RESOURCE);
                        } else {
                            m.put(f, base.get(f));
                        }
                    }
                    case ORIGIN_COMMENTS -> {
                        if (delta.containsKey(SerializedField.ORIGIN_NULL_COMMENTS)) {
                            m.remove(SerializedField.ORIGIN_NULL_COMMENTS);
                        } else {
                            m.put(f, base.get(f));
                        }
                    }
                    case ORIGIN_NULL_URL, ORIGIN_NULL_RESOURCE, ORIGIN_NULL_COMMENTS -> // FALL THRU
                        // base objects shouldn't contain these, should just
                        // lack the field. these are only in deltas.
                            throw new ConfigException.BugOrBroken("applying fields, base object should not contain " + f + " "
                                    + base);
                    case ORIGIN_END_LINE_NUMBER, ORIGIN_LINE_NUMBER, ORIGIN_TYPE -> m.put(f, base.get(f));
                    case END_MARKER, ROOT_VALUE, ROOT_WAS_CONFIG, UNKNOWN, VALUE_DATA, VALUE_ORIGIN ->
                            throw new ConfigException.BugOrBroken("should not appear here: " + f);
                }
            }
        }
        return m;
    }

    static SimpleConfigOrigin fromBase(SimpleConfigOrigin baseOrigin, Map<SerializedField, Object> delta)
            throws IOException {
        Map<SerializedField, Object> baseFields;
        if (baseOrigin != null)
            baseFields = baseOrigin.toFields();
        else
            baseFields = Collections.emptyMap();
        Map<SerializedField, Object> fields = applyFieldsDelta(baseFields, delta);
        return fromFields(fields);
    }

    @Override
    public SimpleConfigOrigin withLineNumber(int lineNumber) {
        if (lineNumber == this.lineNumber && lineNumber == this.endLineNumber) {
            return this;
        } else {
            return new SimpleConfigOrigin(this.description, lineNumber, lineNumber, this.originType, this.urlOrNull,
                    this.resourceOrNull, this.commentsOrNull);
        }
    }

    SimpleConfigOrigin addURI(URI url) {
        return new SimpleConfigOrigin(this.description, this.lineNumber, this.endLineNumber, this.originType,
                url != null ? url.toASCIIString() : null, this.resourceOrNull, this.commentsOrNull);
    }

    @Override
    public SimpleConfigOrigin withComments(List<String> comments) {
        if (ConfigImplUtil.equalsHandlingNull(comments, this.commentsOrNull)) {
            return this;
        } else {
            return new SimpleConfigOrigin(this.description, this.lineNumber, this.endLineNumber, this.originType,
                    this.urlOrNull, this.resourceOrNull, comments);
        }
    }

    SimpleConfigOrigin prependComments(List<String> comments) {
        if (ConfigImplUtil.equalsHandlingNull(comments, this.commentsOrNull) || comments == null) {
            return this;
        } else if (this.commentsOrNull == null) {
            return withComments(comments);
        } else {
            List<String> merged = new ArrayList<>(comments.size() + this.commentsOrNull.size());
            merged.addAll(comments);
            merged.addAll(this.commentsOrNull);
            return withComments(merged);
        }
    }

    SimpleConfigOrigin appendComments(List<String> comments) {
        if (ConfigImplUtil.equalsHandlingNull(comments, this.commentsOrNull) || comments == null) {
            return this;
        } else if (this.commentsOrNull == null) {
            return withComments(comments);
        } else {
            List<String> merged = new ArrayList<>(comments.size() + this.commentsOrNull.size());
            merged.addAll(this.commentsOrNull);
            merged.addAll(comments);
            return withComments(merged);
        }
    }

    @Override
    public String description() {
        if (lineNumber < 0) {
            return description;
        } else if (endLineNumber == lineNumber) {
            return description + ": " + lineNumber;
        } else {
            return description + ": " + lineNumber + "-" + endLineNumber;
        }
    }

    OriginType originType() {
        return originType;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SimpleConfigOrigin otherOrigin) {

            return this.description.equals(otherOrigin.description) && this.lineNumber == otherOrigin.lineNumber
                    && this.endLineNumber == otherOrigin.endLineNumber && this.originType == otherOrigin.originType
                    && ConfigImplUtil.equalsHandlingNull(this.urlOrNull, otherOrigin.urlOrNull)
                    && ConfigImplUtil.equalsHandlingNull(this.resourceOrNull, otherOrigin.resourceOrNull);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int h = 41 * (41 + description.hashCode());
        h = 41 * (h + lineNumber);
        h = 41 * (h + endLineNumber);
        h = 41 * (h + originType.hashCode());
        if (urlOrNull != null)
            h = 41 * (h + urlOrNull.hashCode());
        if (resourceOrNull != null)
            h = 41 * (h + resourceOrNull.hashCode());
        return h;
    }

    @Override
    public String toString() {
        return "ConfigOrigin(" + description + ")";
    }

    @Override
    public String filename() {
        if (originType == OriginType.FILE) {
            return description;
        } else if (urlOrNull != null) {
            URI url;
            try {
                url = new URL(urlOrNull).toURI();
            } catch (Exception e) {
                return null;
            }
            if (url.getScheme() != null && url.getScheme().equals("file")) {
                return url.getPath();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public URI uri() {
        if (urlOrNull == null) {
            return null;
        } else {
            try {
                return new URI(urlOrNull);
            } catch (URISyntaxException e) {
                return null;
            }
        }
    }

    @Override
    public String resource() {
        return resourceOrNull;
    }

    @Override
    public int lineNumber() {
        return lineNumber;
    }

    @Override
    public List<String> comments() {
        if (commentsOrNull != null) {
            return Collections.unmodifiableList(commentsOrNull);
        } else {
            return Collections.emptyList();
        }
    }

    Map<SerializedField, Object> toFields() {
        Map<SerializedField, Object> m = new EnumMap<>(SerializedField.class);

        m.put(SerializedField.ORIGIN_DESCRIPTION, description);

        if (lineNumber >= 0)
            m.put(SerializedField.ORIGIN_LINE_NUMBER, lineNumber);
        if (endLineNumber >= 0)
            m.put(SerializedField.ORIGIN_END_LINE_NUMBER, endLineNumber);

        m.put(SerializedField.ORIGIN_TYPE, originType.ordinal());

        if (urlOrNull != null)
            m.put(SerializedField.ORIGIN_URL, urlOrNull);
        if (resourceOrNull != null)
            m.put(SerializedField.ORIGIN_RESOURCE, resourceOrNull);
        if (commentsOrNull != null)
            m.put(SerializedField.ORIGIN_COMMENTS, commentsOrNull);

        return m;
    }

    Map<SerializedField, Object> toFieldsDelta(SimpleConfigOrigin baseOrigin) {
        Map<SerializedField, Object> baseFields;
        if (baseOrigin != null)
            baseFields = baseOrigin.toFields();
        else
            baseFields = Collections.emptyMap();
        return fieldsDelta(baseFields, toFields());
    }
}
