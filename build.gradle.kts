import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.gumbocoin"
version = "v0.0.1"

plugins {
    java
    kotlin("jvm") version "1.3.40"
}



repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("commons-codec","commons-codec","1.12")
    implementation("com.codahale","shamir","0.7.0")
    implementation("com.google.code.gson","gson","2.8.5")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}