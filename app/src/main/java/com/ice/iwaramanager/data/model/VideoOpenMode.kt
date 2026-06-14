package com.ice.iwaramanager.data.model

enum class VideoOpenMode {
    InApp,
    ExternalPlayer
}

data class VideoPlayerApp(
    val label: String,
    val packageName: String,
    val activityName: String
) {
    val componentName: String
        get() = "$packageName/$activityName"
}
