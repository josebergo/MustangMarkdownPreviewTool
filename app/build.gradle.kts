plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.mustang.mdview"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mustang.mdview"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.documentfile)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.commonmark.tasks)
    implementation(libs.commonmark.autolink)
    testImplementation(libs.junit)
    testImplementation(libs.jsoup)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.test.manifest)
}
