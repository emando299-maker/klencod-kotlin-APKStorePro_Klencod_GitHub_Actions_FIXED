package com.apkstore.pro

data class StoreApp(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val icon: String,
    val categories: List<String>,
    val versionName: String,
    val versionCode: Long,
    val apkName: String,
    val hash: String,
    val hashType: String,
    val size: Long,
    val minSdk: Int,
    val sourceUrl: String = "https://f-droid.org/repo/"
)
