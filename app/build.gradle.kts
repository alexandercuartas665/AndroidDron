plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val remoteApiKey = (project.findProperty("remoteApiKey") as String?) ?: "dev-key-change-me"
val remotePort = (project.findProperty("remotePort") as String?)?.toIntOrNull() ?: 8791

android {
    namespace = "com.bitcode.webcommandapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bitcode.webcommandapp"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REMOTE_API_KEY", "\"$remoteApiKey\"")
        buildConfigField("int", "REMOTE_PORT", "$remotePort")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
