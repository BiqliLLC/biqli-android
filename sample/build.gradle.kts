plugins {
    id("com.android.application")
}

val biqliAppId = providers.gradleProperty("BIQLI_SAMPLE_APP_ID")
    .orElse("biq_mapp_replace_me")
val biqliPublishableKey = providers.gradleProperty("BIQLI_SAMPLE_PUBLISHABLE_KEY")
    .orElse("biqli_mobile_pk_replace_me")
val biqliDomain = providers.gradleProperty("BIQLI_SAMPLE_DOMAIN")
    .orElse("go.example.com")
val biqliApplicationId = providers.gradleProperty("BIQLI_SAMPLE_APPLICATION_ID")
    .orElse("li.biq.sdk.sample")

android {
    namespace = "li.biq.sdk.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = biqliApplicationId.get()
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["biqliDomain"] = biqliDomain.get()
        buildConfigField("String", "BIQLI_APP_ID", "\"${biqliAppId.get()}\"")
        buildConfigField("String", "BIQLI_PUBLISHABLE_KEY", "\"${biqliPublishableKey.get()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":biqli"))
}
