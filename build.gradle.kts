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

// The matching *server* build for runServer26. Kept next to the API version
// so the compile target and the server we prove it on stay in step.
val paper26ServerVersion = "26.1.2"

// Single source of truth for the one runtime library we need. It is NOT
// shaded: WelcomeFlowLoader hands the coordinate to Paper's library loader
// at startup, and reads the version from the generated
// libraries.properties so this declaration and that download can never
// drift apart. compileOnly here because the jar ships without it.
val sqliteJdbcVersion = "3.46.1.3"

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

    // Provided at runtime by Paper's library loader, so compileOnly. Lets
    // SqliteStorageProvider use SQLiteDataSource directly instead of going
    // through DriverManager, whose ServiceLoader-based driver discovery is
    // unreliable for a library injected into an isolated plugin classloader.
    compileOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Pinned deliberately: MockBukkit asserts at mock() time that the
    // paper-api on the classpath matches the version it was built against,
    // and 4.45.1 is the newest release built against 1.21.4 (4.46.0 moved to
    // 1.21.5). Bumping this requires bumping paperApiVersion in lockstep,
    // which would raise the minimum server version the jar supports.
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.45.1")

    // compileOnly in production (Paper's library loader provides it at
    // runtime), but the storage tests run a real SQLite database against a
    // temp file, so they need the driver on the test classpath.
    testImplementation("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
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
    verify26CompileOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
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
    val props = mapOf(
        "version" to project.version,
        "sqliteJdbcVersion" to sqliteJdbcVersion,
    )
    inputs.properties(props)
    // libraries.properties is read by WelcomeFlowLoader so the runtime
    // download and the compileOnly declaration above share one version.
    filesMatching(listOf("paper-plugin.yml", "libraries.properties")) {
        expand(props)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// -----------------------------------------------------------------------
// Local dev servers. Two of them, because the compatibility claim on the
// resource page is "one jar, 1.21.4 through 26.x" and the only honest way
// to back that is to boot the same jar on both lines.
//
// runServer   -> 1.21.4, the oldest supported server (Java 21)
// runServer26 -> 26.1.2, the newest        (Java 25 -- Paper 26.x requires it)
//
// scripts/smoke-test.sh drives either one.
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

tasks.register<RunServer>("runServer26") {
    group = "welcomeflow"
    description = "Runs the SAME jar on a Paper 26.x server, to verify cross-version compatibility."

    minecraftVersion(paper26ServerVersion)
    // Separate run directory: the two servers have incompatible world data,
    // and sharing one would make each run corrupt the other's.
    runDirectory.set(layout.projectDirectory.dir("run26"))
    jvmArgs("-Xms512M", "-Xmx1G")

    // The auto-configured runServer task gets this from run-paper's
    // convention; a task we register ourselves does not, and silently boots
    // with zero plugins if we forget. This is deliberately the SAME artifact
    // runServer uses -- building a separate 26.x jar would defeat the point.
    pluginJars(tasks.jar.flatMap { it.archiveFile })

    // Paper 26.x requires Java 25. Our jar is Java 21 bytecode, which a Java
    // 25 JVM loads happily -- that asymmetry is exactly what this task exists
    // to demonstrate.
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    )
}
