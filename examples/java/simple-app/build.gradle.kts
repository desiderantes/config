plugins {
    `java-library`
    application
}

application {
    mainClass.set("SimpleApp")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

dependencies {
    implementation(project(":examples:java:simple-lib"))
}