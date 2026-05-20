plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("io.ktor.plugin") version "2.3.10"
    application
}

group = "com.stocksim"
version = "1.0.0"

application {
    mainClass.set("com.stocksim.graphservice.ApplicationKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.10"
val kotlinVersion = "1.9.23"
val logbackVersion = "1.4.14"
val lettuce = "6.3.2.RELEASE"
val clickhouse = "0.6.0"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")

    // Redis (Lettuce)
    implementation("io.lettuce:lettuce-core:$lettuce")

    // ClickHouse JDBC
    implementation("com.clickhouse:clickhouse-jdbc:$clickhouse")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.8.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Config
    implementation("com.typesafe:config:1.4.3")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // DI (Koin)
    implementation("io.insert-koin:koin-ktor:3.5.3")
    implementation("io.insert-koin:koin-logger-slf4j:3.5.3")

    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
