import xyz.jpenilla.runpaper.task.RunServer

plugins {
    `java-library`
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "studios.littlesquad"
version = "1.0.0-SNAPSHOT"
description = "Turn first joins into returning players."

// -----------------------------------------------------------------------
// Compatibility strategy (see PLAN.md "The 1.21 <-> 26.x compatibility
// finding"): the shipped jar is compiled once, against paper-api 1.21.4,
// targeting Java 21 bytecode. A second, output-discarded compile of the
// SAME sources against paper-api 26.1.2 on JDK 25 runs as part of `check`
// and fails the build the moment any code touches a method that doesn't
// exist in both API lines. Nothing here ships from that second compile;
// its only job is to make cross-version compatibility a build invariant.
// -----------------------------------------------------------------------
val paperApiVersion = "1.21.4-R0.1-SNAPSHOT"
val paperApi26Version = "26.1.2.build.69-stable"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi"
    }
    maven("https://jitpack.io") {
        name = "jitpack" // VaultAPI is only published here
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    // Optional soft-dependencies: present at compile time only, guarded at
    // runtime behind Bukkit.getPluginManager().isPluginEnabled(...).
    // VaultAPI's own POM drags in a legacy org.bukkit:bukkit artifact that
    // conflicts with paper-api's "org.bukkit:bukkit" capability; we only
    // need VaultAPI's own classes, so that transitive is excluded.
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Pinned deliberately: MockBukkit asserts at mock() time that the
    // paper-api on the classpath matches the version it was built against,
    // and 4.45.1 is the newest release built against 1.21.4 (4.46.0 moved to
    // 1.21.5). Bumping this requires bumping paperApiVersion in lockstep,
    // which would raise the minimum server version the jar supports.
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.45.1")
}

// compileOnly (paper-api and friends) is not visible to test sources by
// default -- test code needs the same Adventure/Bukkit types production
// code does, so pull it in explicitly rather than re-declaring paper-api
// a second time.
configurations {
    testImplementation.get().extendsFrom(compileOnly.get())
}

// -----------------------------------------------------------------------
// verify26: mirrors src/main/java against paper-api 26.1.2. Compile-only,
// never packaged. See tasks.check wiring below.
// -----------------------------------------------------------------------
sourceSets {
    create("verify26") {
        java.setSrcDirs(sourceSets.main.get().java.srcDirs)
    }
}

val verify26CompileOnly by configurations.getting

dependencies {
    verify26CompileOnly("io.papermc.paper:paper-api:$paperApi26Version")
    verify26CompileOnly("me.clip:placeholderapi:2.11.6")
    verify26CompileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}

tasks.named<JavaCompile>("compileVerify26Java") {
    // javac refuses a classpath entry whose class-file version exceeds
    // --release, so this task must target 25 (paper-api 26.x is built for
    // Java 25), not the 21 the shipped jar uses.
    javaCompiler.set(
        javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    )
    options.release.set(25)
    // Output is discarded -- this task only exists to fail the build.
    destinationDirectory.set(layout.buildDirectory.dir("verify26/discarded"))
}

tasks.check {
    dependsOn("compileVerify26Java")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// -----------------------------------------------------------------------
// Local dev server (1.21.x line). A second task targeting a 26.x build
// is added in M5, once the api-version acceptance question (see PLAN.md)
// is settled empirically.
// -----------------------------------------------------------------------
tasks.named<RunServer>("runServer") {
    minecraftVersion("1.21.4")
    runDirectory.set(layout.projectDirectory.dir("run"))
    // A dev/smoke server needs very little: an empty flat-ish world and one
    // plugin. Capping the heap keeps `runServer` usable on a machine that is
    // already running an IDE and a game client -- run-paper's default lets G1
    // reserve enough virtual space to fail outright on a loaded box.
    jvmArgs("-Xms512M", "-Xmx1G")
}
