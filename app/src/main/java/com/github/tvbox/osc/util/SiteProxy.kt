package com.github.tvbox.osc.util

import com.github.tvbox.osc.bean.ProxyRule
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.net.OkGoHelper
import com.google.gson.JsonParser

import java.util.HashMap

/**
 * 站点级代理。
 *
 * 规则（与站点配置 "proxy" 字段一致）：
 *   - 字段缺省或为空字符串 -> 该站不走代理；
 *   - 字段填了任意非空值 -> 该站走代理；值是 http://、https://、socks 开头的合法地址时用该地址，
 *     否则（例如 "proxy" 这类占位标记）使用设置里填写的代理地址。
 *
 * 生效方式：
 *   - js / 原生爬虫：请求走 OkHttp，OkHttp 已挂 OkProxySelector，直接把通配规则塞进去即可；
 *   - py 爬虫：脚本用 requests 自己发请求，不经过 OkHttp，所以额外把地址同步给 Python 侧。
 *
 * 临时切换（首页代理图标）只覆盖当前判定的结果，不改站点配置本身。
 */
object SiteProxy {

    private const val TAG = "SiteProxy"

    const val DEFAULT_URL = "http://127.0.0.1:2080"

    private fun overrides(): MutableMap<String, Boolean> {
        return try {
            KV.get(HawkConfig.SITE_PROXY_OVERRIDE, HashMap<String, Boolean>())
        } catch (th: Throwable) {
            HashMap()
        }
    }

    /** 设置里填写的代理地址 */
    fun configuredUrl(): String {
        val url = KV.get(HawkConfig.PROXY_URL, DEFAULT_URL)
        val trim = url?.trim() ?: ""
        return if (trim.isEmpty()) DEFAULT_URL else trim
    }

    fun setConfiguredUrl(url: String) {
        KV.put(HawkConfig.PROXY_URL, url.trim())
    }

    /** 站点配置解析出的实际代理地址；空串表示配置上不走代理 */
    fun resolve(bean: SourceBean?): String {
        val raw = bean?.proxy?.trim() ?: return ""
        if (raw.isEmpty()) return ""
        if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("socks")) return raw
        return configuredUrl()
    }

    /** 只看站点配置的结论（忽略临时切换） */
    fun configuredEnabled(bean: SourceBean?): Boolean = resolve(bean).isNotEmpty()

    /** 当前是否使用代理（含临时切换） */
    fun isEnabled(bean: SourceBean?): Boolean {
        val key = bean?.key ?: return false
        if (key.isEmpty()) return false
        val ov = overrides()[key]
        if (ov != null) return ov
        return configuredEnabled(bean)
    }

    /** 是否处于临时切换状态 */
    fun isOverridden(bean: SourceBean?): Boolean {
        val key = bean?.key ?: return false
        return key.isNotEmpty() && overrides().containsKey(key)
    }

    fun setOverride(key: String, enabled: Boolean?) {
        if (key.isEmpty()) return
        val next = HashMap<String, Boolean>(overrides())
        if (enabled == null) next.remove(key) else next[key] = enabled
        KV.put(HawkConfig.SITE_PROXY_OVERRIDE, next)
    }

    /** 首页代理图标：临时切换并立即生效 */
    fun toggle(bean: SourceBean?): Boolean {
        val key = bean?.key ?: return false
        if (key.isEmpty()) return false
        val next = !isEnabled(bean)
        setOverride(key, next)
        apply(bean)
        return next
    }

    /** 按当前站点应用代理；未启用则关闭全局代理 */
    fun apply(bean: SourceBean?) {
        val url = if (isEnabled(bean)) resolve(bean).ifEmpty { configuredUrl() } else ""
        applyGlobal(url)
    }

    fun clear() {
        applyGlobal("")
    }

    private fun applyGlobal(url: String) {
        if (url.isEmpty()) {
            try {
                OkGoHelper.setProxyList(null)
            } catch (th: Throwable) {
                LOG.e(TAG, th)
            }
            syncPython("")
            LOG.i("$TAG: proxy off")
            return
        }
        try {
            val json = "[{\"name\":\"site_proxy\",\"hosts\":[\"*\"],\"urls\":[\"" + url + "\"]}]"
            val array = JsonParser.parseString(json).asJsonArray
            OkGoHelper.setProxyList(ProxyRule.arrayFrom(array))
        } catch (th: Throwable) {
            LOG.e(TAG, th)
        }
        syncPython(url)
        LOG.i("$TAG: proxy on -> $url")
    }

    /** py 脚本用 requests 自己发请求，不走 OkHttp，单独同步地址 */
    private fun syncPython(url: String) {
        try {
            val loader = com.undcover.freedom.pyramid.PythonLoader.getInstance()
            val app = loader?.pyApp ?: return
            app.callAttr("setSiteProxy", url)
        } catch (th: Throwable) {
            LOG.d(TAG, "python proxy sync skipped | " + th.message)
        }
    }
}
