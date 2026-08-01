import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val encodedPersistentKeystore = rootProject.file("signing/file-editor-dev.jks.b64")
val decodedPersistentKeystore = layout.buildDirectory
    .file("persistent-signing/file-editor-dev.jks")
    .get()
    .asFile

check(encodedPersistentKeystore.isFile) {
    "Missing persistent signing key: ${encodedPersistentKeystore.path}"
}

val decodedKeystoreBytes = Base64.getMimeDecoder().decode(encodedPersistentKeystore.readText())
decodedPersistentKeystore.parentFile.mkdirs()
if (
    !decodedPersistentKeystore.isFile ||
    !decodedPersistentKeystore.readBytes().contentEquals(decodedKeystoreBytes)
) {
    decodedPersistentKeystore.writeBytes(decodedKeystoreBytes)
}

android {
    namespace = "com.alastorkaneki.fileeditor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alastorkaneki.fileeditor"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("persistentDev") {
            storeFile = decodedPersistentKeystore
            storePassword = "fileeditor-dev"
            keyAlias = "fileeditor-dev"
            keyPassword = "fileeditor-dev"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("persistentDev")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("persistentDev")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
        jniLibs.pickFirsts += setOf("**/libc++_shared.so")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-transformer:1.10.1")
    implementation("androidx.media3:media3-effect:1.10.1")
    implementation("androidx.media3:media3-common:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")

    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
    implementation("com.github.Adonai:jaudiotagger:2.3.15")
    implementation("com.squareup:gifencoder:0.10.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
