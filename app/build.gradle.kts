import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.spacetototo.onesecond"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spacetototo.onesecond"
        minSdk = 29
        targetSdk = 34
        versionCode = 10
        versionName = "0.3.4"
    }

    // 모든 버전을 같은 키로 서명해야 덮어쓰기 설치가 된다.
    // 키와 비밀번호는 저장소에 두지 않는다: 서버 빌드는 환경변수(GitHub Secrets),
    // 내 PC 빌드는 keystore.properties(gitignore 됨)에서 읽는다.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun secret(env: String, key: String): String? = System.getenv(env) ?: keystoreProps.getProperty(key)
    val storePath = secret("KEYSTORE_PATH", "storeFile")

    signingConfigs {
        if (storePath != null) {
            create("shared") {
                storeFile = rootProject.file(storePath)
                storePassword = secret("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = secret("KEY_ALIAS", "keyAlias")
                keyPassword = secret("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("shared")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("shared")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
