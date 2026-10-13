plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.hza.ledger"
    compileSdk = 36
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.hza.ledger"
        minSdk = 23
        targetSdk = 36
        versionCode = 21
        versionName = "1.9.1"

        // 自动更新：版本信息 JSON 的地址。
        // 留空则应用内「检查更新」会提示"未配置更新地址"，功能自动跳过。
        // 换成你自己的服务器或网盘直链即可生效。
        // 内置更新地址。填了之后 App 装上就能点「检查更新」，
        // 用户无需在设置页手动粘贴。
        //
        // 这个地址是**固定的**（配置发布在 latest 版本下），
        // 只有内容会随发版更新，地址永不变动。
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://github.com/vitness7/suishouji-releases/releases/download/latest/update.txt\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lunar.calendar)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (name.contains("ScreenshotTest", ignoreCase = true)) {
        maxParallelForks = 1
        forkEvery = 1
    }
}
