plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.navigation.safeargs)
    alias(libs.plugins.hilt)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "dev.jdtech.jellyfin"
    compileSdk = Versions.compileSdk
    buildToolsVersion = Versions.buildTools

    defaultConfig {
        applicationId = "io.github.jcuhfehl.findroid"
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk

        versionCode = Versions.appCode
        versionName = Versions.appName

        testInstrumentationRunner = "dev.jdtech.jellyfin.HiltTestRunner"
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                if (variant.buildType.name == "release") {
                    val outputFileName = "findroid-${variant.flavorName}-${output.getFilter("ABI")}.apk"
                    output.outputFileName = outputFileName
                }
            }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
        named("debug") {
            applicationIdSuffix = ".debug"
        }
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        register("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
        }
    }

    flavorDimensions += "variant"
    productFlavors {
        register("libre") {
            dimension = "variant"
            isDefault = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = Versions.java
        targetCompatibility = Versions.java
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

ktlint {
    version.set(Versions.ktlint)
    android.set(true)
    ignoreFailures.set(false)
}

aboutLibraries {
    excludeFields = arrayOf("generated")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":preferences"))
    implementation(project(":player:core"))
    implementation(project(":player:video"))
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work)
    implementation(libs.coil)
    implementation(libs.coil.svg)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.jellyfin.core)
    compileOnly(libs.libmpv)
    implementation(libs.material)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.timber)

    coreLibraryDesugaring(libs.android.desugar.jdk)

    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
}

