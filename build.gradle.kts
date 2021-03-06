import org.gradle.jvm.tasks.Jar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

val burrowVersion by project
val kaleVersion by project
val kotlinVersion by project

val projectTitle = "Burrow"

apply {
    plugin("maven")
    plugin("maven-publish")
    plugin("jacoco")
    plugin("idea")
}

plugins {
    java
    kotlin("jvm") version "1.2.20"
    kotlin("kapt") version "1.2.20"
    id("com.github.johnrengelman.shadow") version "2.0.2"
}

jacoco {
    toolVersion = "0.7.9"
}

val jacocoTestReport = project.tasks.getByName("jacocoTestReport")

jacocoTestReport.doFirst {
    (jacocoTestReport as JacocoReport).classDirectories = fileTree("build/classes/kotlin/main").apply {
        // Exclude well known data classes that should contain no logic
        // Remember to change values in codecov.yml too
        exclude("**/*Event.*")
        exclude("**/*State.*")
        exclude("**/*Configuration.*")
        exclude("**/*Runner.*")
        exclude("**/*Factory.*")
        exclude("**/*Sleeper.*")
        exclude("**/*Wrapper.*")
        exclude("**/Burrow.kt")
    }

    jacocoTestReport.reports.xml.isEnabled = true
    jacocoTestReport.reports.html.isEnabled = true
}

jar {
    manifest {
        this.attributes += "Main-Class" to "chat.willow.burrow.Burrow"
    }
}

compileJava {
    sourceCompatibility = JavaVersion.VERSION_1_7.toString()
    targetCompatibility = JavaVersion.VERSION_1_7.toString()
}

repositories {
    mavenCentral()
    maven { setUrl("https://maven.ci.carrot.codes") }
}

dependencies {
    implementation(kotlin("stdlib", kotlinVersion as String))
    implementation(kotlin("reflect", kotlinVersion as String))

    implementation("ch.qos.logback:logback-classic:1.2.3")

    implementation("chat.willow.kale:Kale:$kaleVersion")
    kapt("chat.willow.kale:Kale:$kaleVersion")
    implementation("com.squareup.okio:okio:1.11.0")
    implementation("io.reactivex.rxjava2:rxjava:2.1.6")
    implementation("io.reactivex.rxjava2:rxkotlin:2.1.0")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.3")

    testImplementation("junit:junit:4.12")
    testImplementation("org.mockito:mockito-core:2.2.9")
    testImplementation("com.nhaarman:mockito-kotlin:1.5.0")
}

test {
    testLogging.setEvents(listOf("passed", "skipped", "failed", "standardError"))

    outputs.upToDateWhen { false }

    workingDir = File("testRun")
}

task<Test>("unitTest") {
    testLogging.setEvents(listOf("passed", "skipped", "failed", "standardError"))

    filter {
        includeTestsMatching("unit.*")
    }

    outputs.upToDateWhen { false }
}

task<Test>("functionalTest") {
    testLogging.setEvents(listOf("passed", "skipped", "failed", "standardError"))

    filter {
        includeTestsMatching("functional.*")
    }

    outputs.upToDateWhen { false }

    workingDir = File("testRun")
}

task<Test>("fuzzTest") {
    testLogging.setEvents(listOf("passed", "skipped", "failed", "standardError"))

    filter {
        includeTestsMatching("fuzz.*")
    }

    outputs.upToDateWhen { false }
}

val buildNumberAddition = if(project.hasProperty("BUILD_NUMBER")) { ".${project.property("BUILD_NUMBER")}" } else { "" }
val branchAddition = if(project.hasProperty("BRANCH")) {
    val safeBranchName = project.property("BRANCH")
            .toString()
            .map { if(Character.isJavaIdentifierPart(it)) it else '_' }
            .joinToString(separator = "")

    when (safeBranchName) {
        "develop" -> ""
        else -> "-$safeBranchName"
    }
} else {
    ""
}

version = "$burrowVersion$buildNumberAddition$branchAddition"
group = "chat.willow.burrow"
project.setProperty("archivesBaseName", projectTitle)

shadowJar {
    mergeServiceFiles()
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

val sourcesTask = task<Jar>("sourcesJar") {
    dependsOn("classes")

    from(sourceSets("main").allSource)
    classifier = "sources"
}

project.artifacts.add("archives", sourcesTask)
project.artifacts.add("archives", shadowJarTask())

configure<PublishingExtension> {
    val deployUrl = if (project.hasProperty("DEPLOY_URL")) { project.property("DEPLOY_URL") } else { project.buildDir.absolutePath }
    this.repositories.maven({ setUrl("$deployUrl") })

    publications {
        create<MavenPublication>("mavenJava") {
            from(components.getByName("java"))

            artifact(shadowJarTask())
            artifact(sourcesTask)

            artifactId = projectTitle
        }
    }
}

fun Project.jar(setup: Jar.() -> Unit) = (project.tasks.getByName("jar") as Jar).setup()
fun jacoco(setup: JacocoPluginExtension.() -> Unit) = the<JacocoPluginExtension>().setup()
fun shadowJar(setup: ShadowJar.() -> Unit) = shadowJarTask().setup()
fun Project.test(setup: Test.() -> Unit) = (project.tasks.getByName("test") as Test).setup()
fun Project.compileJava(setup: JavaCompile.() -> Unit) = (project.tasks.getByName("compileJava") as JavaCompile).setup()
fun shadowJarTask() = (project.tasks.findByName("shadowJar") as ShadowJar)
fun sourceSets(name: String) = (project.property("sourceSets") as SourceSetContainer).getByName(name)
