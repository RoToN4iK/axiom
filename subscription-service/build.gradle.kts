plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.protobuf)
    kotlin("plugin.spring") version libs.versions.kotlin.get()
    kotlin("plugin.jpa") version libs.versions.kotlin.get()
}

group = "me.roton.axiom"
version = "0.0.1-SNAPSHOT"
description = "subscription-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":axiom-common"))
    implementation(project(":axiom-contracts"))

    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-grpc-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")

    testImplementation(libs.kotlinx.coroutines.test)

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("user.timezone", "UTC")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("user.timezone", "UTC")
}