package com.github.tvbox.osc.util

import org.json.JSONArray
import org.json.JSONObject

import java.io.File

/**
 * 本地脚本目录当订阅源：把目录里所有 .py / .js 扫出来，生成一份站点配置。
 *
 * 订阅源地址写成 lscript://<绝对路径>，加载时不再走网络，直接按目录内容生成 json。
 */
object LocalScriptSource {

    const val SCHEME = "lscript://"

    private const val MAX_FILES = 2000

    fun path(apiUrl: String): String = apiUrl.removePrefix(SCHEME)

    fun isLocalScript(apiUrl: String?): Boolean = apiUrl != null && apiUrl.startsWith(SCHEME)

    fun scripts(dir: String): List<File> {
        val root = File(dir)
        if (!root.isDirectory) return emptyList()
        val out = ArrayList<File>()
        collect(root, out)
        out.sortBy { it.name }
        return out
    }

    private fun collect(dir: File, out: MutableList<File>) {
        if (out.size >= MAX_FILES) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (out.size >= MAX_FILES) return
            if (f.isDirectory) {
                collect(f, out)
            } else {
                val n = f.name.lowercase()
                // type 6 的 html 脚本同样收进来（与 py/js 并列，各自走自己的引擎）
                if (n.endsWith(".py") || n.endsWith(".js") || n.endsWith(".html")) out.add(f)
            }
        }
    }

    fun count(dir: String): Int = scripts(dir).size

    /**
     * 站点 key 会直接参与两处，不能带扩展名：
     * ① js：JsSpider 用 "globalThis.<key>" 拼出变量名，key 里带 "."（如 "视频.js"）
     *    会变成 "globalThis.视频.js = ..." —— JS 语法错误，脚本静默失效、分类宫格全空；
     * ② py：key 只是 PythonLoader 的缓存键，但同样要求同目录同名不同后缀不撞车。
     * 所以统一取主文件名 + 类型后缀，并过滤掉不能进标识符的字符。
     */
    private fun safeKey(base: String): String {
        var s = base.replace(Regex("[^0-9A-Za-z_\\u4e00-\\u9fa5]"), "_")
        if (s.isEmpty()) s = "s"
        if (s[0].isDigit()) s = "_" + s
        return s
    }

    /** 目录名或文件名含「代理」即等同 "proxy":"proxy"（沿用 appmenu 的 KEY="代理" 约定） */
    const val PROXY_KEYWORD = "代理"

    private fun proxyFlag(f: File): String {
        val name = f.name
        val parent = f.parentFile?.name.orEmpty()
        return if (name.contains(PROXY_KEYWORD) || parent.contains(PROXY_KEYWORD)) "proxy" else ""
    }

    fun buildJson(apiUrl: String): String {
        val dir = path(apiUrl)
        val files = scripts(dir)
        val sites = JSONArray()
        for (f in files) {
            val lower = f.name.lowercase()
            val kind = when {
                lower.endsWith(".py") -> "py"
                lower.endsWith(".html") -> "html"
                else -> "js"
            }
            val item = JSONObject()
            item.put("key", safeKey(f.nameWithoutExtension) + kind)
            item.put("name", f.nameWithoutExtension + "[" + kind + "]")
            // html 脚本是 type 6；py / js 沿用原来的 3
            item.put("type", if (kind == "html") 6 else 3)
            item.put("api", f.absolutePath)
            item.put("ext", "")
            item.put("proxy", proxyFlag(f))
            item.put("searchable", 1)
            item.put("changeable", 1)
            item.put("quickSearch", 1)
            sites.put(item)
        }
        val lives = JSONArray()
        lives.put(JSONObject())
        val root = JSONObject()
        root.put("warningText", "本地脚本目录：" + dir)
        root.put("sites", sites)
        root.put("lives", lives)
        return root.toString()
    }
}
