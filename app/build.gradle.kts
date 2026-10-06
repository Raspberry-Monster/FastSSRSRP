import org.gradle.api.tasks.compile.JavaCompile

val hiddenApiJar = files("libs/android.jar")

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
        viewBinding = true
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
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.api)
    implementation(libs.hiddenapibypass)
    compileOnly(hiddenApiJar)
}

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        if (!name.contains("UnitTest") &&
            !name.contains("AndroidTest")) {
            classpath = hiddenApiJar + classpath
        }
    }
}
