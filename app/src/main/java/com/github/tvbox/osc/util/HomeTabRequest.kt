package com.github.tvbox.osc.util

import androidx.compose.runtime.mutableStateOf

/**
 * 从其它页面（如配置管理）请求主界面切回首页宫格页。
 */
object HomeTabRequest {
    private var _token by mutableStateOf(0)

    val token: Int
        get() = _token

    fun request() {
        _token += 1
    }
}
