dependencies {
    // AndroidX & 核心
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Google Play Services 定位
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // 網路請求與 JSON 解析 (用於方案 C: Nominatim API)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")

    // 協程 (處理非同步請求)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}