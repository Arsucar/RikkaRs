import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.ai"

    defaultConfig {
//        externalNativeBuild {
//            cmake {
//                cppFlags += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
//                abiFilters += listOf("arm64-v8a", "x86_64")
//            }
//        }
    }

//    externalNativeBuild {
//        cmake {
//            path = file("src/main/cpp/CMakeLists.txt")
//            version = "3.22.1"
//        }
//    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":common"))

    // Compose
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)

    // okhttp — Provider surfaces take/return OkHttpClient, Request, Response, EventSource
    api(libs.okhttp)
    api(libs.okhttp.sse)

    // kotlinx — Message / provider models expose serialization + datetime types
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    // StringEscapeUtils used only inside GoogleProvider (not part of public surface)
    implementation(libs.commons.text)

    // tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
