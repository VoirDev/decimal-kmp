plugins {
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
    id("com.vanniktech.maven.publish") version "0.36.0"
}

kotlin {
    jvmToolchain(21)
    // Library code must declare visibility explicitly so nothing becomes public by accident.
    explicitApi()

    compilerOptions {
        // Decimal is an expect/actual class; opt in to the Beta feature instead of warning.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    jvm {
        compilerOptions {
            // Build with JDK 21 but emit bytecode that consumers on Java 11 and newer can load.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xjdk-release=11")
        }
    }
    android {
        namespace = "dev.voir.decimal"
        compileSdk = 37
        minSdk = 23

        // Run the common tests on the Android runtime too, not only on the JVM.
        withHostTest {}
    }
    iosX64()
    iosArm64()
    macosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain = getByName("commonMain")
        val commonTest = getByName("commonTest")
        val jvmMain = getByName("jvmMain")
        val androidMain = getByName("androidMain")
        val iosX64Main = getByName("iosX64Main")
        val iosArm64Main = getByName("iosArm64Main")
        val iosSimulatorArm64Main = getByName("iosSimulatorArm64Main")
        val macosArm64Main = getByName("macosArm64Main")

        val jvmAndroidMain = create("jvmAndroidMain") {
            dependsOn(commonMain)
        }
        val appleMain = findByName("appleMain") ?: create("appleMain") {
            dependsOn(commonMain)
        }

        jvmMain.dependsOn(jvmAndroidMain)
        androidMain.dependsOn(jvmAndroidMain)
        iosX64Main.dependsOn(appleMain)
        iosArm64Main.dependsOn(appleMain)
        iosSimulatorArm64Main.dependsOn(appleMain)
        macosArm64Main.dependsOn(appleMain)

        commonMain.dependencies {
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}


val isLocalPublish = gradle.startParameter.taskNames.any {
    it.contains("publishToMavenLocal", ignoreCase = true)
}

mavenPublishing {
    publishToMavenCentral()
    if (!isLocalPublish) {
        signAllPublications()
    }

    coordinates(
        groupId = "dev.voir",
        artifactId = "decimal",
        version = project.version.toString()
    )

    pom {
        name.set("Decimal - Kotlin Multiplatform decimal numbers")
        description.set("Kotlin Multiplatform decimal numbers backed by BigDecimal on JVM/Android and NSDecimalNumber on Apple targets.")
        url.set("https://github.com/VoirDev/decimal-kmp/")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("checksanity")
                name.set("Gary Bezruchko")
                email.set("hello@voir.dev")
                organization.set("VOIR")
                organizationUrl.set("https://voir.dev")
            }
        }

        scm {
            url.set("https://github.com/VoirDev/decimal-kmp/")
            connection.set("scm:git:git://github.com/VoirDev/decimal-kmp.git")
            developerConnection.set("scm:git:ssh://git@github.com/VoirDev/decimal-kmp.git")
        }
    }
}
