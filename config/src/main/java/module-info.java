module com.typesafe.config {
    //Beans stuff
    requires java.desktop;
    requires transitive org.jetbrains.annotations;
    exports com.typesafe.config;
    exports com.typesafe.config.parser;
}