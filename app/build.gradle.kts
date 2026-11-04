import java.util.Properties
plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
val privateDir = providers.gradleProperty("shuttlerefPrivateDir").orNull
    ?.let { rootProject.file(it) } ?: rootProject.file("config/local")
val privateConfig = Properties().apply {
    val file = privateDir.resolve("android.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun config(name: String) = privateConfig.getProperty(name, "")
android {
    namespace = "com.yahabib.shuttleref"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.yahabib.shuttleref"
        minSdk = 26; targetSdk = 35
        versionCode = 5; versionName = "0.3.0-scoring"
    }
    flavorDimensions += "integration"
    productFlavors {
        create("demo") { dimension = "integration"; applicationIdSuffix = ".demo"; resValue("string", "app_name", "羽小裁·演示") }
        create("tuya") {
            dimension = "integration"
            resValue("string", "app_name", "羽小裁")
            manifestPlaceholders["tuyaAppKey"] = config("TUYA_APP_KEY")
            manifestPlaceholders["tuyaAppSecret"] = config("TUYA_APP_SECRET")
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { jniLibs.pickFirsts += "lib/*/libc++_shared.so" }
}
configurations.configureEach { exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno") }
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    add("tuyaImplementation", "com.thingclips.smart:thingsmart:7.11.0")
    add("tuyaImplementation", "com.alibaba:fastjson:1.1.67.android")
    add("tuyaImplementation", "com.squareup.okhttp3:okhttp-urlconnection:3.14.9")
    add("tuyaImplementation", files(privateDir.resolve("android-sdk/security-algorithm-1.0.0-beta.aar")))
}
