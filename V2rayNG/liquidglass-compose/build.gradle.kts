plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

group = "dev.liquidglass"
version = "1.0.0-satra"

android {
    namespace = "dev.liquidglass.compose"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles(
            rootProject.file(
                "../third_party/Abdullajon-LiquidGlass/liquidglass-compose/consumer-rules.pro"
            )
        )
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(
                rootProject.file(
                    "../third_party/Abdullajon-LiquidGlass/liquidglass-compose/src/main/AndroidManifest.xml"
                )
            )
            java.srcDir(
                rootProject.file(
                    "../third_party/Abdullajon-LiquidGlass/liquidglass-compose/src/main/kotlin"
                )
            )
        }
    }

    buildFeatures {
        compose = true
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

dependencies {
    api(project(":liquidglass-core"))
    implementation(libs.androidx.annotation)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
}
