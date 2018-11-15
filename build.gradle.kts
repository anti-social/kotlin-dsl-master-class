import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    val kotlinVersion = "1.3.0"

    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    }
}

apply {
    plugin("kotlin")
    plugin("kotlinx-serialization")
}

plugins {
    java
}

group = "company.evo"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven { setUrl("https://kotlin.bintray.com/kotlinx") }
}

dependencies {
    val serializationVersion = "0.9.0"

    compile(kotlin("stdlib-jdk8"))
    compile("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xnew-inference")
    }
}