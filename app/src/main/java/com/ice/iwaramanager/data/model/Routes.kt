package com.ice.iwaramanager.data.model

enum class AppRoute(val path: String) {
    Library("library"),
    Settings("settings"),
    Detail("detail"),
    Player("player"),
    Match("match"),
    MatchTaskDetail("match_task_detail"),
    IwaraLogin("iwara_login"),
    IwaraWebView("iwara_web_view")
}

enum class SettingsTab {
    Directory,
    Display,
    Playback,
    Match,
    Database
}
