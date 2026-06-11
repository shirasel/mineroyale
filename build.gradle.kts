plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "me.shirasemaru"
version = property("pluginVersion").toString()

val paperApiVersion = property("paperApiVersion").toString()
val testPaperApiVersion = property("testPaperApiVersion").toString()
val runMinecraftVersion = property("runMinecraftVersion").toString()
val targetJavaVersion = property("targetJavaVersion").toString().toInt()
val junitVersion = property("junitVersion").toString()
val mockkVersion = property("mockkVersion").toString()
val byteBuddyVersion = property("byteBuddyVersion").toString()
val coroutinesVersion = property("coroutinesVersion").toString()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("net.bytebuddy:byte-buddy:$byteBuddyVersion")
    testImplementation("net.bytebuddy:byte-buddy-agent:$byteBuddyVersion")
    testImplementation("io.papermc.paper:paper-api:$testPaperApiVersion")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion(runMinecraftVersion)
    }

    shadowJar {
        archiveClassifier.set("")
    }

    jar {
        archiveClassifier.set("plain")
    }
}

kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.assemble {
    dependsOn("shadowJar")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading"
    )
    val testRunId = providers.gradleProperty("testRunId").orNull ?: "default"
    binaryResultsDirectory.set(layout.buildDirectory.dir("test-results-jvm/$testRunId/binary"))
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
