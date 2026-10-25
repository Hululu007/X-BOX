package com.github.tvbox.osc.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.tvbox.osc.ui.activity.DetailActivity

/**
 * 详情页唤起兼容层：对齐影视TV / TVBox 系扩展脚本的调用约定。
 *
 * 第三方 jar、py 脚本里"跳详情页"的写法不统一，常见的有三类，这里全部认下：
 *   ① 五参  start(activity, key, id, name, pic)
 *   ② 三参  start(activity, key, id)
 *   ③ 纯 Intent extra —— 且字段名各家不同，见下面的别名表
 *
 * 本 App 自己是宿主，不需要像寄生方案那样去反射猜方法名；
 * 这里做的是反过来：把宿主入口的"接收口径"放宽，让第三方脚本不改一行就能命中。
 */
object DetailIntent {

    // 各字段在影视TV 系里的常见别名，按顺序取第一个非空的
    // 注意别把 "url" 列进 id 别名：详情页本身也可能带 url 字段，误当成 id 会取错内容
    private val ID_KEYS = arrayOf("id", "vod_id", "vodId", "ids")
    private val SOURCE_KEYS = arrayOf("sourceKey", "key", "siteKey", "site_key", "source", "site")
    private val TITLE_KEYS = arrayOf("title", "name", "vod_name", "vodName")
    private val PIC_KEYS = arrayOf("picture", "pic", "img", "vod_pic", "vodPic", "cover", "thumb")

    fun id(bundle: Bundle?): String = first(bundle, ID_KEYS)
    fun sourceKey(bundle: Bundle?): String = first(bundle, SOURCE_KEYS)
    fun title(bundle: Bundle?): String = first(bundle, TITLE_KEYS)
    fun picture(bundle: Bundle?): String = first(bundle, PIC_KEYS)

    private fun first(bundle: Bundle?, keys: Array<String>): String {
        if (bundle == null) return ""
        for (k in keys) {
            val v = bundle.getString(k)
            if (!v.isNullOrEmpty()) return v
        }
        return ""
    }

    fun build(context: Context, key: String?, id: String?, name: String? = null, pic: String? = null): Intent =
        Intent(context, DetailActivity::class.java)
            .putExtra("id", id.orEmpty())
            .putExtra("sourceKey", key.orEmpty())
            .putExtra("title", name.orEmpty())
            .putExtra("picture", pic.orEmpty())

    // ---- 供 jar / py 脚本直接调用 ----
    // 只留一个 Context 入口：Activity 本身也是 Context，
    // 两个重载在 Chaquopy 侧容易解析歧义，收敛成一个最稳。

    /** 五参：影视TV 系最常见的写法 */
    @JvmStatic
    fun start(context: Context?, key: String?, id: String?, name: String?, pic: String?) {
        val ctx = context ?: return
        val intent = build(ctx, key, id, name, pic)
        // 从非 Activity 上下文（Application）启动必须加这个标记
        if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    /** 三参变体 */
    @JvmStatic
    fun start(context: Context?, key: String?, id: String?) {
        start(context, key, id, null, null)
    }
}
