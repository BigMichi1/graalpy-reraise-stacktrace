plugins {
    application
}

repositories {
    mavenCentral()
}

// ./gradlew run -Pgraalpy=25.0.4 tries another release.
val graalpy = providers.gradleProperty("graalpy").getOrElse("25.4.4.1.1")

dependencies {
    implementation("org.graalvm.polyglot:polyglot:$graalpy")
    implementation("org.graalvm.polyglot:python-community:$graalpy")
}

application {
    // ./gradlew run -Pmain=StackTraceRepro runs the second reproducer.
    mainClass = providers.gradleProperty("main").getOrElse("ReraiseRepro")
    applicationDefaultJvmArgs = buildList {
        add("--enable-native-access=ALL-UNNAMED")
        add("--sun-misc-unsafe-memory-access=allow")
        add("-Dpolyglot.engine.WarnInterpreterOnly=false")
        // ./gradlew run -Pea enables Java assertions, as every JUnit run does.
        if (providers.gradleProperty("ea").isPresent) add("-ea")
    }
}
