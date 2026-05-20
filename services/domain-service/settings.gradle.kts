pluginManagement {
    // plugins.gradle.org недоступен из этой сети (TLS handshake режется).
    // Берём плагины из Maven Central — он доступен.
    repositories {
        maven("https://cache-redirector.jetbrains.com/maven-central")
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.jvm" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                "org.jetbrains.kotlin.plugin.serialization" ->
                    useModule("org.jetbrains.kotlin:kotlin-serialization:${requested.version}")
            }
        }
    }
}

rootProject.name = "domain-service"
