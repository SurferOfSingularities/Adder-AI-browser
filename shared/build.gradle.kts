plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinCompose)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            // Material icons are no longer bundled with material3 as of Compose
            // Multiplatform 1.8, so the icon set is added explicitly (pinned to the
            // last published JetBrains Compose icons version). Not using the
            // `compose.materialIconsExtended` accessor because it resolves to the
            // compose version (1.8.x), which was never published for icons.
            implementation(libs.compose.material.icons.extended)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            // Kotest Property supplies the generators + checkAll for the
            // property-based tests. The test framework itself is kotlin.test, which
            // maps to JUnit4 on the Android/JVM target and needs no extra runner
            // wiring, so `./gradlew :shared:testDebugUnitTest` runs commonTest.
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            // checkAll is a suspend function; runTest provides the coroutine scope.
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.mlkit.genai.prompt)
            implementation(compose.uiTooling)
            // App Startup: supplies the application Context to AppContextHolder so
            // the shared module can back PersistentStore with SharedPreferences.
            implementation(libs.androidx.startup.runtime)
        }

        val iosMain by creating {
            dependsOn(commonMain.get())
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
    }
}

android {
    namespace = "com.adder.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 34
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
