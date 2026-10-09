import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-arm64:0.8.18")
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "fyi.slot.ui.res"
}

android {
    namespace = "fyi.slot.ui"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Headless screenshot renderer: ./gradlew :ui:renderPreviews -Pout=build/previews
tasks.register<JavaExec>("renderPreviews") {
    group = "verification"
    val desktop = kotlin.targets.getByName("desktop") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
    val main = desktop.compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("fyi.slot.ui.preview.PreviewKt")
    args(providers.gradleProperty("out").getOrElse(layout.buildDirectory.dir("previews").get().asFile.absolutePath), providers.gradleProperty("scene").getOrElse("all"))
    jvmArgs("-Dskiko.renderApi=SOFTWARE")
}
