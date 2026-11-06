import java.util.Properties

/*
 * Khoá ký bản phát hành KHÔNG nằm trong mã nguồn. Đọc theo thứ tự:
 *  1. file keystore.properties ở thư mục gốc (đã bị .gitignore bỏ qua) – dùng khi build trên máy bạn
 *  2. biến môi trường ZENVID_KEYSTORE / ZENVID_KEYSTORE_PASSWORD / ZENVID_KEY_ALIAS / ZENVID_KEY_PASSWORD – dùng trên GitHub Actions
 * Không có khoá thì bản release vẫn build được nhưng chưa ký (không cài được) – dùng bản debug để thử.
 */
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    // Đường dẫn trong keystore.properties tính từ thư mục gốc dự án; biến môi trường thì dùng đường dẫn tuyệt đối
    System.getenv("ZENVID_KEYSTORE")?.let { setProperty("storeFile", it) }
    System.getenv("ZENVID_KEYSTORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("ZENVID_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("ZENVID_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}
val hasSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { !signing.getProperty(it).isNullOrBlank() }

/** Kho GitHub để kiểm tra bản cập nhật, dạng "chủ/kho". Đặt trong gradle.properties: zenvid.updateRepo=... */
val updateRepo = (findProperty("zenvid.updateRepo") as String?).orEmpty()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "vn.zenvid"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.zenvid"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "1.9.2"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        if (hasSigning) create("release") {
            storeFile = rootProject.file(signing.getProperty("storeFile")) // rootProject.file chấp nhận cả đường dẫn tuyệt đối
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures { compose = true; buildConfig = true }

    // Không đưa thông tin phụ thuộc (dependency metadata) của Google vào APK
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // Trình trích xuất YouTube mã nguồn mở (dùng trong NewPipe) – không dùng API/SDK của Google
    implementation("com.github.TeamNewPipe:NewPipeExtractor:6cb36e982311dc1c529997bc329013afe75f37d1")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")

    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")

    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")

    val room = "2.8.3"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
