package com.ice.iwaramanager.data.model

enum class IwaraLoginStatus {
    Unknown,
    Checking,
    LoggedIn,
    LoggedOut,
    Expired,
    Error
}

fun IwaraLoginStatus.label(): String {
    return when (this) {
        IwaraLoginStatus.Unknown -> "未检查"
        IwaraLoginStatus.Checking -> "检查中"
        IwaraLoginStatus.LoggedIn -> "已登录"
        IwaraLoginStatus.LoggedOut -> "未登录"
        IwaraLoginStatus.Expired -> "疑似过期"
        IwaraLoginStatus.Error -> "检查失败"
    }
}
