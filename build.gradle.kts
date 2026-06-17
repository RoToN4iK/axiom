plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "me.roton.axiom"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }
}