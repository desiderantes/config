package com.typesafe.config;

import com.typesafe.config.impl.ConfigImpl;

import java.net.URI;

/**
 * This class contains some static factory methods for building a {@link
 * ConfigOrigin}. {@code ConfigOrigin}s are automatically created when you
 * call other API methods to get a {@code ConfigValue} or {@code Config}.
 * But you can also set the origin of an existing {@code ConfigValue}, using
 * {@link ConfigValue#withOrigin(ConfigOrigin)}.
 *
 * @since 1.3.0
 */
public final class ConfigOriginFactory {
    private ConfigOriginFactory() {
    }

    /**
     * Returns the default origin for values when no other information is
     * provided. This is the origin used in {@link ConfigValueFactory
     * #fromAnyRef(Object)}.
     *
     * @return the default origin
     * @since 1.3.0
     */
    public static ConfigOrigin newSimple() {
        return newSimple(null);
    }

    /**
     * Returns an origin with the given description.
     *
     * @param description brief description of what the origin is
     * @return a new origin
     * @since 1.3.0
     */
    public static ConfigOrigin newSimple(String description) {
        return ConfigImpl.newSimpleOrigin(description);
    }

    /**
     * Creates a file origin with the given filename.
     *
     * @param filename the filename of this origin
     * @return a new origin
     * @since 1.3.0
     */
    public static ConfigOrigin newFile(String filename) {
        return ConfigImpl.newFileOrigin(filename);
    }

    /**
     * Creates a url origin with the given URL object.
     *
     * @param url the url of this origin
     * @return a new origin
     * @since 1.3.0
     */
    public static ConfigOrigin newURL(URI url) {
        return ConfigImpl.newURLOrigin(url);
    }
}
