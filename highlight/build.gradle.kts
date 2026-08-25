plugins {
    id("rikkahub.android.library.compose")
}

android {
    namespace = "me.rerere.highlight"

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Upstream native highlight (#1614) — pure Kotlin engine, no QuickJS
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
