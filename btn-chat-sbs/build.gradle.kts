import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val mainClass = "no.nav.btnchat.sbs.StartSbsKt"
val kotlinVersion = "1.3.70"
val ktorVersion = "1.3.1"
val prometheusVersion = "0.4.0"
val logbackVersion = "1.2.3"
val logstashVersion = "5.1"
val konfigVersion = "1.6.10.0"
val kafkaVersion = "2.3.0"

plugins {
    application
    kotlin("jvm") version "1.3.70"
}

buildscript {
    dependencies {
        classpath("org.junit.platform:junit-platform-gradle-plugin:1.2.0")
    }
}

application {
    mainClassName = mainClass
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-metrics:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.9.9")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_dropwizard:$prometheusVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")
//    implementation("no.nav:vault-jdbc:1.3.1")
//    implementation("org.flywaydb:flyway-core:6.3.1")
//    implementation("com.github.seratch:kotliquery:1.3.0")
//    implementation("com.natpryce:konfig:$konfigVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation(project(":btn-chat-common"))

    testRuntimeOnly("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
//    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
//    testImplementation("org.testcontainers:postgresql:1.14.1")
//    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
}

repositories {
    jcenter()
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/ktor")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.3.1"
}

task<Jar>("fatJar") {
    baseName = "app"

    manifest {
        attributes["Main-Class"] = mainClass
        configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

tasks.named<KotlinCompile>("compileKotlin") {
    kotlinOptions.jvmTarget = "11"
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    kotlinOptions.jvmTarget = "11"
}

tasks {
    "jar" {
        dependsOn("fatJar")
    }
}
