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
                if (n.endsWith(".py") || n.endsWith(".js")) out.add(f)
            }
        }
    }

    fun count(dir: String): Int = scripts(dir).size

    fun buildJson(apiUrl: String): String {
        val dir = path(apiUrl)
        val files = scripts(dir)
        val sites = JSONArray()
        for (f in files) {
            val item = JSONObject()
            item.put("key", f.name)
            item.put("name", f.nameWithoutExtension)
            item.put("type", 3)
            item.put("api", "file://" + f.absolutePath)
            item.put("ext", "")
            item.put("proxy", "")
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
