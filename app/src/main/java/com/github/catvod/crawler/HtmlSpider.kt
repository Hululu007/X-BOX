package com.github.catvod.crawler

import android.os.Handler
import android.os.Looper
import com.github.tvbox.osc.ui.activity.HtmlActivity
import com.github.tvbox.osc.util.AppContextHolder
import com.github.tvbox.osc.util.LOG
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * HTML 脚本爬虫（站点 type 6）。
 *
 * 与 js / py 走【同一套 Spider 接口】：分类与宫格数据由脚本自己返回，
 * 不是 WebView 里渲染出来的宫格 —— WebView 只负责跑脚本，不负责出 UI。
 *
 * 流程：每次被调用就拉起一次全屏 HtmlActivity 跑 html，脚本算完调
 * window.fm.done(result) 回传，Activity 随即关闭。所以：
 *   · 进站点（homeContent）自动弹一次，拿分类与宫格；
 *   · 点宫格（detailContent）再弹一次。
 *
 * Spider 接口是同步的，而 Activity + WebView 是异步的，这里用
 * CountDownLatch 做同步等待并带超时 —— 脚本不回调也不会把调用方卡死。
 */
class HtmlSpider(
    key: String?,
    private val api: String?,
    private val extend: String?,
) : Spider() {

    init {
        this.siteKey = key
    }

    override fun init(context: android.content.Context?) {
        // 无状态：每次调用现起 Activity，不需要预加载
    }

    override fun homeContent(filter: Boolean): String? =
        call("homeContent", JSONObject().put("filter", filter))

    override fun homeVideoContent(): String? =
        call("homeVideoContent", JSONObject())

    override fun categoryContent(
        tid: String?,
        pg: String,
        filter: Boolean,
        extend: HashMap<String, String>?,
    ): String? {
        val map: Map<String, String> = extend ?: emptyMap()
        return call(
            "categoryContent",
            JSONObject()
                .put("tid", tid ?: "")
                .put("pg", pg)
                .put("filter", filter)
                .put("extend", JSONObject(map).toString()),
        )
    }

    override fun detailContent(ids: List<String>?): String? {
        val list: List<String> = ids ?: emptyList()
        return call("detailContent", JSONObject().put("ids", org.json.JSONArray(list)))
    }

    override fun searchContent(key: String?, quick: Boolean): String? =
        call("searchContent", JSONObject().put("key", key ?: "").put("quick", quick))

    override fun searchContent(key: String?, quick: Boolean, pg: String?): String? =
        call(
            "searchContent",
            JSONObject().put("key", key ?: "").put("quick", quick).put("pg", pg ?: ""),
        )

    override fun playerContent(flag: String?, id: String, vipFlags: List<String>?): String? =
        call(
            "playerContent",
            JSONObject()
                .put("flag", flag ?: "")
                .put("id", id)
                .put("vipFlags", org.json.JSONArray(vipFlags ?: emptyList())),
        )

    override fun proxyLocal(params: Map<String, String>?): Array<Any?>? {
        val map: Map<String, String> = params ?: emptyMap()
        val raw = call("localProxy", JSONObject(map)) ?: return null
        return try {
            val o = JSONObject(raw)
            val code = o.optInt("code", 200)
            val type = o.optString("type", "application/octet-stream")
            val body = o.optString("body", "")
            arrayOf<Any?>(code, type, body.toByteArray())
        } catch (th: Throwable) {
            LOG.e(TAG, th)
            null
        }
    }

    override fun destroy() {
        HtmlActivity.finishIfRunning(siteKey)
    }

    private fun call(method: String, args: JSONObject): String? {
        val ctx = AppContextHolder.context() ?: return null
        val latch = CountDownLatch(1)
        val result = AtomicReference<String?>()
        val request = HtmlActivity.Request(
            siteKey = siteKey ?: "",
            api = api ?: "",
            ext = extend ?: "",
            method = method,
            args = args.toString(),
        ) { value ->
            result.set(value)
            latch.countDown()
        }
        Handler(Looper.getMainLooper()).post {
            try {
                HtmlActivity.pending = request
                ctx.startActivity(
                    android.content.Intent(ctx, HtmlActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            } catch (th: Throwable) {
                LOG.e(TAG, th)
                latch.countDown()
            }
        }
        val ok = try {
            latch.await(TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (th: Throwable) {
            false
        }
        if (!ok) LOG.i("$TAG: $method timeout after ${TIMEOUT_SEC}s")
        return result.get()
    }

    private companion object {
        const val TAG = "HtmlSpider"
        const val TIMEOUT_SEC = 30L
    }
}
