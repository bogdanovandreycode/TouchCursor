import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("com.android.application")
    jacoco
}

jacoco { toolVersion = "0.8.14" }

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        includes = listOf("com.example.touchcursor.*")
    }
}

val unitCoverageReport = tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    group = "verification"
    description = "Reports local unit-test coverage of application Kotlin/Java classes."
    dependsOn("testDebugUnitTest")
    executionData(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    classDirectories.setFrom(files(
        fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
            exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.*")
        },
        fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")),
    ))
    sourceDirectories.setFrom(files("src/main/java"))
    reports {
        html.required = true
        xml.required = true
    }
}

val unitCoverageVerification = tasks.register<JacocoCoverageVerification>("jacocoDebugUnitTestCoverageVerification") {
    group = "verification"
    description = "Requires at least 90% line and 70% branch coverage from local unit tests."
    dependsOn(unitCoverageReport)
    executionData(unitCoverageReport.map { it.executionData })
    classDirectories.setFrom(unitCoverageReport.map { it.classDirectories })
    sourceDirectories.setFrom(unitCoverageReport.map { it.sourceDirectories })
    violationRules {
        rule {
            limit { counter = "LINE"; minimum = "0.90".toBigDecimal() }
            limit { counter = "BRANCH"; minimum = "0.70".toBigDecimal() }
        }
    }
}

tasks.named("check") { dependsOn(unitCoverageVerification) }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("junit:junit:4.13.2")
}

android {
    namespace = "com.example.touchcursor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.touchcursor"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "0.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
