plugins {
    id("com.android.application")
}

android {
    enableKotlin = false
    namespace = "io.github.raspberrykan.fastssrsrp"
    defaultConfig {
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs["debug"]
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "**"
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    dependenciesInfo {
        includeInApk = false
    }
}

dependencies {
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.api)
    implementation(libs.hiddenapibypass)
}
