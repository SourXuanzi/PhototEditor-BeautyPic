plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sour.photoeditorapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.sour.photoeditorapp"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // 1. ExoPlayer - 视频播放
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
//    // 视频剪辑库
//    implementation("com.googlecode.mp4parser:isoparser:1.1.22")
//    // 视频滤镜库
//    implementation("jp.co.cyberagent.android:gpuimage:2.1.0")
//    // 视频转码/处理库
//    implementation("com.otaliastudios:transcoder:0.10.5")
//    // 权限处理
//    implementation("com.guolindev.permissionx:permissionx:1.7.1")

}