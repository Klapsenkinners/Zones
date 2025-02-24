import xyz.jpenilla.runtask.task.AbstractRun

plugins {
    id("java")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.0.0-beta8"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("java-library") // Add this line for multi-project support
}

group = "de.t14d3"
version = "0.2.1"

repositories {
    mavenCentral()
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

}

dependencies {
    implementation(project(":api"))
    implementation(project(":bukkit"))

    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:23.0.0")

    implementation("org.yaml:snakeyaml:2.5-SNAPSHOT")
    implementation("net.kyori:adventure-api:4.19.0")
    implementation("net.kyori:adventure-text-minimessage:4.19.0")
}

val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}
tasks.withType<AbstractRun>().configureEach {
    javaLauncher = javaToolchains.launcherFor {
        vendor.set(JvmVendorSpec.JETBRAINS)
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("dev.jorel.commandapi", "de.t14d3.zones.commandapi")
        relocate("org.h2", "de.t14d3.zones.db.h2")
        relocate("org.postgresql", "de.t14d3.zones.db.postgresql")

        dependencies {
            exclude(dependency("org.checkerframework:checker-qual"))
        }
    }
    build {
        dependsOn(shadowJar)
    }
    runServer {
        minecraftVersion("1.21.4")
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/t14d3/zones")
            credentials {
                username = project.findProperty("gpr.user").toString() ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key").toString() ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("gpr") {
            artifactId = project.name.lowercase()
            groupId = group.toString().lowercase()
            version = version.toString()
            from(components["java"])
        }
    }
}
