from com.github.catvod import Proxy as CatVodProxy

class Proxy:
    def getUrl(self, local):
        return CatVodProxy.getUrl(local).replace('/proxy', '')

    def getPort(self):
        return CatVodProxy.getPort()

# 站点级代理:由 Java 侧 SiteProxy.syncPython() 调用 setSiteProxy 写入
# py 脚本用 requests 自行发请求,不经过 OkHttp,故只能在这里单独同步
_SITE_PROXY = ''


def setSiteProxy(url):
    global _SITE_PROXY
    _SITE_PROXY = (url or '').strip()
    try:
        import os
        if _SITE_PROXY:
            os.environ['HTTP_PROXY'] = _SITE_PROXY
            os.environ['HTTPS_PROXY'] = _SITE_PROXY
            os.environ['http_proxy'] = _SITE_PROXY
            os.environ['https_proxy'] = _SITE_PROXY
        else:
            for k in ('HTTP_PROXY', 'HTTPS_PROXY', 'http_proxy', 'https_proxy'):
                os.environ.pop(k, None)
    except Exception:
        pass
    _install_requests_patch()
    _install_urllib_patch()
    return _SITE_PROXY


# 光写环境变量不够:py 脚本常自建 requests.Session(),或显式传 proxies=None /
# 设 trust_env=False —— trust_env=False 时 requests 根本不读环境变量,代理就被绕过了。
# 这里直接兜在 Session.request 上,只要站点开了代理且调用方没给可用代理,就强制注入。
_PATCHED = False


def _install_requests_patch():
    global _PATCHED
    if _PATCHED:
        return
    try:
        import requests

        session_cls = requests.Session
        if getattr(session_cls, '_site_proxy_patched', False):
            _PATCHED = True
            return

        original_request = session_cls.request

        def patched_request(self, method, url, *args, **kwargs):
            try:
                if _SITE_PROXY and not kwargs.get('proxies'):
                    kwargs['proxies'] = {'http': _SITE_PROXY, 'https': _SITE_PROXY}
            except Exception:
                pass
            return original_request(self, method, url, *args, **kwargs)

        session_cls.request = patched_request
        session_cls._site_proxy_patched = True
        _PATCHED = True
    except Exception:
        pass


# urllib 走的是另一套(opener),环境变量对已 install 过的 opener 无效,
# 而不少 py 爬虫用 urllib.request.urlopen 取图/取流 —— 单独装一个带代理的 opener。
_URLLIB_PATCHED = False


def _install_urllib_patch():
    global _URLLIB_PATCHED
    try:
        import urllib.request
        if not _SITE_PROXY:
            urllib.request.install_opener(urllib.request.build_opener())
            _URLLIB_PATCHED = False
            return
        handler = urllib.request.ProxyHandler(
            {'http': _SITE_PROXY, 'https': _SITE_PROXY})
        # 不缓存 BasicAuth,避免 407 时旧凭据反复重试
        urllib.request.install_opener(urllib.request.build_opener(handler))
        _URLLIB_PATCHED = True
    except Exception:
        pass


def getSiteProxy():
    return _SITE_PROXY


def proxies():
    if not _SITE_PROXY:
        return None
    return {'http': _SITE_PROXY, 'https': _SITE_PROXY}
