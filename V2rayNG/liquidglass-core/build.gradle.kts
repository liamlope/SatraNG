plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

group = "dev.liquidglass"
version = "1.0.0-satra"

android {
    namespace = "dev.liquidglass.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    sourceSets {
        getByName("main") {
            java.srcDir(
                rootProject.file(
                    "../third_party/Abdullajon-LiquidGlass/liquidglass-core/src/main/kotlin"
                )
            )
            manifest.srcFile(
                rootProject.file("liquidglass-core/src/main/AndroidManifest.xml")
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
