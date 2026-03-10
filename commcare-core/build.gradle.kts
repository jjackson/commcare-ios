import org.gradle.api.tasks.testing.logging.TestExceptionFormat

buildscript {
    repositories {
        maven { url = uri("https://plugins.gradle.org/m2/") }
        mavenCentral()
    }
}

plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    jvm {
        withJava()
    }

    // iOS targets — empty for now, code will move to commonMain incrementally
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "CommCareCore"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                api(files("lib/kxml2-2.4.1.jar"))
                implementation("javax.ws.rs:javax.ws.rs-api:2.0.1")
                implementation("org.json:json:20250517")
                implementation("commons-cli:commons-cli:1.3.1")
                implementation("com.carrotsearch:hppc:0.9.1")
                api("com.squareup.retrofit2:retrofit:2.9.0")
                api("com.squareup.okhttp3:okhttp:4.11.0")
                implementation("com.google.code.findbugs:jsr305:3.0.2")
                implementation("io.reactivex.rxjava2:rxjava:2.2.21")
                implementation("io.opentracing:opentracing-api:0.33.0")
                implementation("io.opentracing:opentracing-util:0.33.0")
                implementation("com.datadoghq:dd-trace-api:1.10.0")
                implementation("org.gavaghan:geodesy:1.1.3")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("junit:junit:4.13.2")
                implementation("org.json:json:20250517")
                implementation("org.xerial:sqlite-jdbc:3.40.0.0")
                implementation("joda-time:joda-time:2.9.4")
                implementation("com.squareup.retrofit2:retrofit:2.9.0")
                implementation("com.squareup.okhttp3:okhttp:4.11.0")
                implementation("com.google.guava:guava:31.1-jre")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        // Create a shared iOS source set
        val iosMain by creating {
            dependsOn(commonMain)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }

        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }
}

// Configure Java source directories for KMP's withJava().
// KMP withJava() creates Java compilations looking at src/jvmMain/java by default,
// but our files are in src/main/java and src/test/java.
sourceSets["main"].java.srcDir("src/main/java")
sourceSets["main"].resources.srcDir("src/main/resources")
sourceSets["test"].java.srcDir("src/test/java")
sourceSets["test"].resources.srcDir("src/test/resources")

// Extra source sets for CLI, CCAPI, and translate JARs
// These are JVM-only Java source sets that depend on the main (jvmMain) output.
// In KMP, Kotlin classes are in kotlin/main/ while Java classes are in java/main/,
// so we need both the Java source set output AND the KMP Kotlin compilation output.
val kotlinMainClasses = files(layout.buildDirectory.dir("classes/kotlin/main"))

sourceSets {
    create("translate") {
        java.srcDir("src/translate/java")
        compileClasspath += sourceSets["main"].output + kotlinMainClasses
    }

    create("cli") {
        java.srcDir("src/cli/java")
        compileClasspath += sourceSets["main"].output + kotlinMainClasses
        compileClasspath += sourceSets["translate"].output
    }

    create("ccapi") {
        java.srcDir("src/cli/java")
        java.exclude("org/commcare/util/cli/**")
        java.exclude("org/commcare/util/mocks/**")
        compileClasspath += sourceSets["main"].output + kotlinMainClasses
    }

    named("test") {
        compileClasspath += sourceSets["cli"].output
        compileClasspath += sourceSets["ccapi"].output
        compileClasspath += sourceSets["translate"].output
    }
}

// Configurations for extra source sets
configurations {
    val ccapi by creating { extendsFrom(configurations["ccapiImplementation"]) }
    val cliOutput by creating { extendsFrom(configurations["cliImplementation"]) }
    val harness by creating
    val translate by creating { extendsFrom(configurations["translateImplementation"]) }
    val testsAsJar by creating
}

dependencies {
    // CLI source set dependencies
    "cliImplementation"("org.json:json:20250517")
    "cliImplementation"(files("lib/kxml2-2.4.1.jar"))
    "cliImplementation"("commons-cli:commons-cli:1.3.1")
    "cliImplementation"("joda-time:joda-time:2.9.4")
    "cliImplementation"("io.reactivex.rxjava2:rxjava:2.1.1")
    "cliImplementation"("com.squareup.retrofit2:retrofit:2.9.0")
    "cliImplementation"("com.squareup.okhttp3:okhttp:4.11.0")
    "cliImplementation"("com.google.guava:guava:31.1-jre")
    "cliImplementation"("com.datadoghq:dd-trace-api:1.10.0")

    // CCAPI source set dependencies
    "ccapiImplementation"(sourceSets["main"].output)
    "ccapiImplementation"("org.json:json:20250517")
    "ccapiImplementation"("org.xerial:sqlite-jdbc:3.40.0.0")
    "ccapiImplementation"(files("lib/kxml2-2.4.1.jar"))
    "ccapiImplementation"("joda-time:joda-time:2.9.4")
    "ccapiImplementation"("com.carrotsearch:hppc:0.9.1")
    "ccapiImplementation"("io.reactivex.rxjava2:rxjava:2.1.1")
    "ccapiImplementation"("com.squareup.retrofit2:retrofit:2.9.0")
    "ccapiImplementation"("com.squareup.okhttp3:okhttp:4.11.0")
    "ccapiImplementation"("com.google.guava:guava:31.1-jre")
    "ccapiImplementation"("io.opentracing:opentracing-api:0.33.0")
    "ccapiImplementation"("io.opentracing:opentracing-util:0.33.0")
    "ccapiImplementation"("com.datadoghq:dd-trace-api:1.10.0")

    // Translate source set dependencies
    "translateImplementation"(files("lib/kxml2-2.4.1.jar"))
    "translateImplementation"("xpp3:xpp3:1.1.4c")
    "translateImplementation"("commons-cli:commons-cli:1.3.1")
    "translateImplementation"(files("lib/json-simple-1.1.1.jar"))
    "translateImplementation"("joda-time:joda-time:2.9.4")
    "translateImplementation"("org.json:json:20250517")
    "translateImplementation"("io.reactivex.rxjava2:rxjava:2.1.1")
    "translateImplementation"("com.squareup.retrofit2:retrofit:2.9.0")
    "translateImplementation"("com.squareup.okhttp3:okhttp:4.11.0")
    "translateImplementation"("com.google.guava:guava:31.1-jre")

    // Test dependencies on other source set outputs
    "testImplementation"(sourceSets["main"].output)
    "testImplementation"(sourceSets["ccapi"].output)
    "testImplementation"(sourceSets["cli"].output)
    "testImplementation"(sourceSets["translate"].output)
}

// Test configuration
tasks.withType<Test> {
    reports.junitXml.outputLocation.set(file("build/reports/tests"))
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
}

// Main JVM JAR
tasks.named<Jar>("jvmJar") {
    archiveBaseName.set("commcare-libraries")
}

// Run tests as part of the 'build' lifecycle
tasks.named("build") {
    dependsOn(tasks.named("jvmTest"))
}

// CLI fat JAR
val cliJar by tasks.registering(Jar::class) {
    dependsOn("cliClasses")
    archiveBaseName.set("commcare-cli")
    from(sourceSets["translate"].output)
    from(sourceSets["cli"].output)
    from(sourceSets["main"].output)
    from({
        configurations["cliOutput"].map { if (it.isDirectory) it else zipTree(it) }
    })
    manifest {
        attributes("Main-Class" to "org.commcare.util.cli.CliMain")
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    filesMatching(listOf("**/LICENSE.txt", "**/NOTICE.txt", "META-INF/**")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

// CCAPI JAR
val ccapiJar by tasks.registering(Jar::class) {
    dependsOn("ccapiClasses")
    archiveBaseName.set("commcare-api")
    from(sourceSets["ccapi"].output)
    from(sourceSets["main"].output)
    from({
        configurations["ccapi"].map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.FAIL
    filesMatching(listOf("**/LICENSE.txt", "**/NOTICE.txt", "META-INF/**")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

// Harness JAR
val harnessJar by tasks.registering(Jar::class) {
    dependsOn("translateClasses")
    archiveBaseName.set("javarosa-cli")
    from(sourceSets["translate"].output)
    from(sourceSets["main"].output)
    from({
        configurations["runtimeClasspath"].map { if (it.isDirectory) it else zipTree(it) }
    })
    manifest {
        attributes("Main-Class" to "org.javarosa.engine.Harness")
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    filesMatching(listOf("**/LICENSE.txt", "**/NOTICE.txt", "META-INF/**")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

// Test sources JAR
val testsrcJar by tasks.registering(Jar::class) {
    dependsOn("testClasses")
    archiveClassifier.set("tests")
    from(files(sourceSets["test"].output))
    from(files(sourceSets["cli"].output))
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

// Form translate JAR
val formTranslateJar by tasks.registering(Jar::class) {
    dependsOn("translateClasses")
    archiveBaseName.set("form_translate")
    from(sourceSets["translate"].output)
    from(sourceSets["main"].output)
    from({
        configurations["translate"].map { if (it.isDirectory) it else zipTree(it) }
    })
    manifest {
        attributes("Main-Class" to "org.javarosa.xform.schema.Harness")
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    filesMatching(listOf(
        "**/LICENSE.txt", "**/NOTICE.txt", "META-INF/**",
        "**/org.xmlpull.v1.XmlPullParserFactory", "org/xmlpull/v1/*"
    )) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

// Artifacts
artifacts {
    add("cliOutput", cliJar)
    add("ccapi", ccapiJar)
    add("harness", harnessJar)
    add("translate", formTranslateJar)
    add("testsAsJar", testsrcJar)
}

// Copy test resources
val copyTestResources by tasks.registering(Copy::class) {
    from(sourceSets["test"].resources)
    into(layout.buildDirectory.dir("classes/test"))
}

tasks.named("processTestResources") {
    dependsOn(copyTestResources)
}

// Extract external libs (for build server)
val buildDeps: String? = if (project.hasProperty("javarosaDeps")) {
    project.property("javarosaDeps") as String
} else null

val extractLibs by tasks.registering(Copy::class) {
    if (buildDeps != null) {
        from(zipTree(file(buildDeps)))
        into(file(projectDir))
    }
}

tasks.named("compileJava") {
    dependsOn(extractLibs)
}
