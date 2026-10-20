plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))

    // Compile-time only: provides the org.xmlpull API on the JVM.
    // On Android the platform supplies it (since API 1), so nothing is bundled.
    compileOnly(libs.kxml2)

    // Tests run on the JVM and need a real pull-parser implementation.
    testImplementation(libs.kxml2)
    testImplementation(kotlin("test"))
}
