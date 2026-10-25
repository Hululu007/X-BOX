(function () {
    function httpWith(sessionId, url, options = {}) {
        options = options || {};
        if (sessionId !== null && sessionId !== undefined) {
            options = Object.assign({}, options, {__session: sessionId});
        }
        if (options.async === false) return _http(url, options);
        return new Promise(resolve => {
            const request = Object.assign({}, options, {
                complete: res => resolve(res)
            });
            try {
                _http(url, request);
            } catch (e) {
                resolve({
                    code: "",
                    headers: {},
                    content: "",
                    error: String(e && e.message ? e.message : e)
                });
            }
        });
    }

    function http(url, options = {}) {
        return httpWith(null, url, options);
    }

    globalThis.req = (url, options) => httpWith(null, url, Object.assign({async: false}, options));
    globalThis.http = http;

    // ------------------------------------------------------------------
    // 兼容层：影视TV / CatVod 系脚本常用的 http 对象与运行时
    //
    // 之前 globalThis.http 只是一个函数，脚本里写 http.get(url) 会报
    // "http.get is not a function"，整段 js 直接失效。这里把对象风格的
    // 方法挂到同一个函数对象上——函数本身也是对象，两种写法都能跑。
    // ------------------------------------------------------------------
    function own(o, k) { return o && Object.prototype.hasOwnProperty.call(o, k); }

    function withQuery(url, params) {
        if (!params || typeof params !== 'object') return url;
        var parts = [];
        for (var k in params) {
            if (own(params, k)) parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(String(params[k])));
        }
        if (!parts.length) return url;
        return url + (url.indexOf('?') >= 0 ? '&' : '?') + parts.join('&');
    }

    function callHttp(url, method, params, headers, extra) {
        try {
            var o = { method: method, headers: headers || {} };
            if (params !== undefined && params !== null && params !== '') {
                if (typeof params === 'string') o.data = params;
                else if (method !== 'GET') o.data = params;
            }
            if (extra) { for (var ek in extra) if (own(extra, ek)) o[ek] = extra[ek]; }
            return _http(url, o);
        } catch (e) {
            return null;
        }
    }

    function textOf(res) {
        try {
            if (!res) return '';
            var c = res.content;
            return c === undefined || c === null ? '' : String(c);
        } catch (e) {
            return '';
        }
    }

    function headerOf(res, name) {
        try {
            var h = res && res.headers;
            if (!h) return '';
            var lower = String(name).toLowerCase();
            var value = '';
            for (var k in h) {
                if (String(k).toLowerCase() === lower) { value = h[k]; break; }
            }
            return value === undefined || value === null ? '' : String(value);
        } catch (e) {
            return '';
        }
    }

    // ---- 运行时补齐：影视TV 生态脚本常用的全局对象 ----
    var nativeSetTimeout = globalThis.setTimeout;
    var timerSeq = 1;
    var timerMap = {};

    function armTimer(fn, delay, repeat) {
        var id = timerSeq++;
        timerMap[id] = { cancelled: false };
        var fire = function () {
            var slot = timerMap[id];
            if (!slot || slot.cancelled) return;
            if (!repeat) delete timerMap[id];
            try { fn(); } catch (e) { /* 脚本回调里的异常不能炸掉引擎 */ }
            if (repeat && timerMap[id]) {
                try { nativeSetTimeout(fire, delay); } catch (e) { delete timerMap[id]; }
            }
        };
        try { nativeSetTimeout(fire, delay); } catch (e) { delete timerMap[id]; }
        return id;
    }

    function defineIfMissing(name, value) {
        try {
            if (globalThis[name] === undefined) {
                Object.defineProperty(globalThis, name, { value: value, writable: true, configurable: true, enumerable: false });
            }
        } catch (e) { /* 只读全局就跳过 */ }
    }

    globalThis.setTimeout = function (fn, delay) {
        if (typeof fn !== 'function') return 0;
        return armTimer(fn, delay > 0 ? delay : 0, false);
    };
    globalThis.clearTimeout = function (id) {
        var slot = timerMap[id];
        if (slot) { slot.cancelled = true; delete timerMap[id]; }
    };
    globalThis.setInterval = function (fn, delay) {
        if (typeof fn !== 'function') return 0;
        return armTimer(fn, delay > 0 ? delay : 0, true);
    };
    globalThis.clearInterval = function (id) {
        var slot = timerMap[id];
        if (slot) { slot.cancelled = true; delete timerMap[id]; }
    };

    // requestAnimationFrame：脚本里偶有用到，QuickJS 没有，退化为定时器实现
    defineIfMissing('requestAnimationFrame', function (fn) {
        if (typeof fn !== 'function') return 0;
        return globalThis.setTimeout(function () { fn(Date.now()); }, 16);
    });
    defineIfMissing('cancelAnimationFrame', function (id) {
        try { globalThis.clearTimeout(id); } catch (e) {}
    });

    defineIfMissing('console', {});
    try {
        if (typeof globalThis.console.log !== 'function') globalThis.console.log = function () {};
        if (typeof globalThis.console.info !== 'function') globalThis.console.info = globalThis.console.log;
        if (typeof globalThis.console.warn !== 'function') globalThis.console.warn = globalThis.console.log;
        if (typeof globalThis.console.error !== 'function') globalThis.console.error = globalThis.console.log;
        if (typeof globalThis.console.debug !== 'function') globalThis.console.debug = globalThis.console.log;
    } catch (e) {}

    defineIfMissing('alert', function (msg) { try { globalThis.console.log(String(msg)); } catch (e) {} });
    defineIfMissing('print', function (msg) { try { globalThis.console.log(String(msg)); } catch (e) {} });
    defineIfMissing('log', function (msg) { try { globalThis.console.log(String(msg)); } catch (e) {} });

    defineIfMissing('atob', function (text) {
        try { return base64Decode(String(text)); } catch (e) { return ''; }
    });
    defineIfMissing('btoa', function (text) {
        try { return base64Encode(String(text)); } catch (e) { return ''; }
    });
    defineIfMissing('md5', function (text) {
        try { return md5X(String(text)); } catch (e) { return ''; }
    });

    // localStorage：底层复用已注入的 local（get/set/delete）
    var localStore = {
        getItem: function (key) {
            try { return local.get(String(key), ''); } catch (e) { return ''; }
        },
        setItem: function (key, value) {
            try { local.set(String(key), String(value), ''); } catch (e) {}
        },
        removeItem: function (key) {
            try { local.delete(String(key), ''); } catch (e) {}
        },
        clear: function () {},
        key: function () { return null; },
        get length() { return 0; },
    };
    defineIfMissing('localStorage', localStore);
    defineIfMissing('localCache', localStore);

    // fetch：少量脚本用它，补一个够用的 Promise 版
    defineIfMissing('fetch', function (url, options) {
        options = options || {};
        return new Promise(function (resolve, reject) {
            var res = callHttp(
                withQuery(url, options.data),
                (options.method || 'GET').toUpperCase(),
                options.body !== undefined ? options.body : options.data,
                options.headers
            );
            var text = textOf(res);
            resolve({
                ok: true,
                status: res && res.code !== undefined ? Number(res.code) : 200,
                url: url,
                headers: (res && res.headers) || {},
                text: function () { return Promise.resolve(text); },
                json: function () {
                    return Promise.resolve((function () { try { return JSON.parse(text); } catch (e) { return null; } })());
                },
            });
        });
    });

    function defineGlobalAlias(name) {
        const descriptor = Object.getOwnPropertyDescriptor(globalThis, name);
        if (descriptor && !descriptor.configurable) return;
        Object.defineProperty(globalThis, name, {
            enumerable: true,
            configurable: true,
            get() {
                return globalThis;
            },
            set() {}
        });
    }

    ['global', 'window', 'self'].forEach(defineGlobalAlias);

    const NET_CACHE_MAX_VALUE = 262144;
    const NET_CACHE_MAX_KEYS = 64;
    const NET_CACHE_INDEX = "__index__";
    const netCache = new Map();

    async function jsonWith(sessionId, url, options = {}) {
        if (options.buffer !== undefined && options.buffer !== 0) {
            throw new TypeError("net.json only supports text mode");
        }
        const response = await httpWith(sessionId, url, Object.assign({}, options, {buffer: 0}));
        if (!response) throw new Error("empty response");
        if (response.error) throw new Error(response.error);
        if (!(response.code >= 200 && response.code < 300)) throw new Error("HTTP " + response.code);
        try {
            return JSON.parse(response.content);
        } catch (e) {
            throw new Error("invalid json: " + e.message);
        }
    }

    function netLimit(limit) {
        if (limit === undefined) return 4;
        if (!Number.isSafeInteger(limit) || limit <= 0) throw new TypeError("limit must be a positive integer");
        return limit;
    }

    function runLimited(items, limit, worker) {
        return new Promise((resolve, reject) => {
            const results = new Array(items.length);
            let next = 0;
            let active = 0;
            let done = 0;
            let failed = false;
            if (items.length === 0) return resolve(results);
            const launch = () => {
                while (!failed && active < limit && next < items.length) {
                    const index = next++;
                    active++;
                    Promise.resolve().then(() => worker(items[index], index)).then(value => {
                        results[index] = value;
                        active--;
                        done++;
                        if (done === items.length) resolve(results);
                        else launch();
                    }, error => {
                        failed = true;
                        reject(error);
                    });
                }
            };
            launch();
        });
    }

    async function batchWith(sessionId, requests, limit) {
        if (!Array.isArray(requests)) throw new TypeError("requests must be an array");
        const size = netLimit(limit);
        return runLimited(requests, size, request => {
            if (!request || typeof request.url !== "string") throw new TypeError("request.url must be a string");
            return httpWith(sessionId, request.url, request.options || {});
        });
    }

    async function netMap(items, callback, limit) {
        if (!Array.isArray(items)) throw new TypeError("items must be an array");
        if (typeof callback !== "function") throw new TypeError("callback must be a function");
        const size = netLimit(limit);
        return runLimited(items, size, (item, index) => callback(item, index));
    }

    function cacheNamespace() {
        const site = typeof globalThis.__jsSiteKey === "string" ? globalThis.__jsSiteKey : "default";
        return site + "_netcache";
    }

    function cacheParse(raw) {
        if (!raw) return null;
        try {
            const saved = JSON.parse(raw);
            if (!saved || typeof saved !== "object" || typeof saved.e !== "number") return null;
            return {value: saved.v, expireAt: saved.e, pending: null, hasValue: true};
        } catch (e) {
            return null;
        }
    }

    function cacheLoadPersist(key) {
        try {
            if (typeof local === "undefined") return null;
            return cacheParse(local.get(cacheNamespace(), key));
        } catch (e) {
            return null;
        }
    }

    function cacheIndex() {
        try {
            if (typeof local === "undefined") return [];
            const raw = local.get(cacheNamespace(), NET_CACHE_INDEX);
            const keys = raw ? JSON.parse(raw) : [];
            return Array.isArray(keys) ? keys.filter(key => typeof key === "string") : [];
        } catch (e) {
            return [];
        }
    }

    function cacheIndexWrite(keys) {
        try {
            if (typeof local === "undefined") return;
            local.set(cacheNamespace(), NET_CACHE_INDEX, JSON.stringify(keys));
        } catch (e) {
            console.error("net.cached index failed", e);
        }
    }

    function cacheWritePersist(key, entry) {
        try {
            if (typeof local === "undefined") return;
            const raw = JSON.stringify({v: entry.value, e: entry.expireAt});
            if (!raw || raw.length > NET_CACHE_MAX_VALUE) return;
            local.set(cacheNamespace(), key, raw);
            const keys = cacheIndex();
            if (keys.indexOf(key) >= 0) return;
            keys.push(key);
            while (keys.length > NET_CACHE_MAX_KEYS) {
                local.delete(cacheNamespace(), keys.shift());
            }
            cacheIndexWrite(keys);
        } catch (e) {
            console.error("net.cached persist failed", e);
        }
    }

    function cacheDeletePersist(key) {
        try {
            if (typeof local === "undefined") return;
            local.delete(cacheNamespace(), key);
            const keys = cacheIndex();
            const index = keys.indexOf(key);
            if (index >= 0) {
                keys.splice(index, 1);
                cacheIndexWrite(keys);
            }
        } catch (e) {
            console.error("net.cached delete failed", e);
        }
    }

    function cacheClearPersist() {
        try {
            if (typeof local === "undefined") return;
            const keys = cacheIndex();
            for (const key of keys) local.delete(cacheNamespace(), key);
            local.delete(cacheNamespace(), NET_CACHE_INDEX);
        } catch (e) {
            console.error("net.cached clear failed", e);
        }
    }

    function cacheClone(value) {
        return JSON.parse(JSON.stringify(value));
    }

    function cacheStart(store, key, ttl, persist, loader) {
        const entry = store.get(key) || {};
        const task = Promise.resolve().then(loader).then(value => {
            if (entry.cancelled) return value;
            entry.value = value;
            entry.expireAt = Date.now() + ttl;
            entry.pending = null;
            entry.hasValue = true;
            store.set(key, entry);
            if (persist) cacheWritePersist(key, entry);
            return value;
        }, error => {
            entry.pending = null;
            if (!entry.cancelled) store.set(key, entry);
            throw error;
        });
        entry.pending = task;
        store.set(key, entry);
        return task;
    }

    function cacheRefresh(store, key, ttl, persist, loader) {
        const entry = store.get(key);
        if (entry && (entry.pending || entry.expireAt > Date.now())) return;
        cacheStart(store, key, ttl, persist, loader).catch(error => {
            console.error("net.cached refresh failed", error && error.message ? error.message : error);
        });
    }

    function cacheLookup(store, key, persist) {
        let entry = store.get(key);
        if (!entry && persist) {
            entry = cacheLoadPersist(key);
            if (entry) store.set(key, entry);
        }
        return entry;
    }

    async function netCachedWith(store, allowPersist, key, options, loader) {
        if (typeof key !== "string" || !key) throw new TypeError("key must be a non-empty string");
        if (typeof loader !== "function") throw new TypeError("loader must be a function");
        const opts = options || {};
        const ttl = opts.ttl;
        if (!Number.isSafeInteger(ttl) || ttl < 0) throw new TypeError("ttl must be a non-negative integer");
        const stale = opts.stale !== false;
        const persist = allowPersist && opts.persist === true;
        if (ttl === 0) return loader();
        const entry = cacheLookup(store, key, persist);
        if (entry && entry.pending) return cacheClone(await entry.pending);
        if (entry && entry.hasValue && entry.expireAt > Date.now()) return cacheClone(entry.value);
        if (entry && entry.hasValue && stale) {
            cacheRefresh(store, key, ttl, persist, loader);
            return cacheClone(entry.value);
        }
        return cacheClone(await cacheStart(store, key, ttl, persist, loader));
    }

    function netPeekWith(store, allowPersist, key) {
        if (typeof key !== "string" || !key) return null;
        const entry = cacheLookup(store, key, allowPersist);
        if (!entry || !entry.hasValue) return null;
        return cacheClone(entry.value);
    }

    function netRefreshWith(store, allowPersist, key, options, loader) {
        if (typeof key !== "string" || !key) throw new TypeError("key must be a non-empty string");
        if (typeof loader !== "function") throw new TypeError("loader must be a function");
        const opts = options || {};
        const ttl = opts.ttl;
        if (!Number.isSafeInteger(ttl) || ttl < 0) throw new TypeError("ttl must be a non-negative integer");
        if (ttl === 0) return undefined;
        cacheRefresh(store, key, ttl, allowPersist && opts.persist === true, loader);
        return undefined;
    }

    function netClearCacheWith(store, allowPersist, key) {
        if (key === undefined) {
            for (const entry of store.values()) entry.cancelled = true;
            store.clear();
            if (allowPersist) cacheClearPersist();
            return undefined;
        }
        if (typeof key !== "string" || !key) throw new TypeError("key must be a non-empty string");
        const entry = store.get(key);
        if (entry) entry.cancelled = true;
        store.delete(key);
        if (allowPersist) cacheDeletePersist(key);
        return undefined;
    }

    function netSleep(ms) {
        if (!Number.isSafeInteger(ms) || ms < 0 || ms > 2147483647) {
            return Promise.reject(new TypeError("ms must be a non-negative integer"));
        }
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async function openStreamWith(sessionId, url, options = {}) {
        const request = Object.assign({}, options || {});
        if (sessionId !== null && sessionId !== undefined) request.__session = sessionId;
        const reader = _openStream(url, request);
        if (!reader) throw new Error("openStream failed");
        const read = reader.read;
        reader.read = size => {
            if (size === undefined) return read.call(reader);
            if (!Number.isSafeInteger(size) || size < 1 || size > 1048576) {
                throw new TypeError("size must be 1..1048576");
            }
            return read.call(reader, size);
        };
        return reader;
    }

    function streamWith(sessionId, url, options) {
        if (typeof url !== "string" || !url) {
            return Promise.reject(new TypeError("url must be a non-empty string"));
        }
        const request = Object.assign({}, options || {});
        if (sessionId !== null && sessionId !== undefined) request.__session = sessionId;
        return new Promise((resolve, reject) => {
            try {
                _stream(url, request, resolve, reject);
            } catch (e) {
                reject(e);
            }
        });
    }

    function createSession() {
        const id = _session();
        const store = new Map();
        let closed = false;
        const guard = () => {
            if (closed) throw new Error("session closed");
        };
        return {
            http: (url, options) => {
                guard();
                return httpWith(id, url, options);
            },
            req: (url, options) => {
                guard();
                return httpWith(id, url, Object.assign({}, options, {async: false}));
            },
            json: (url, options) => {
                guard();
                return jsonWith(id, url, options);
            },
            batch: (requests, limit) => {
                guard();
                return batchWith(id, requests, limit);
            },
            stream: (url, options) => {
                guard();
                return streamWith(id, url, options);
            },
            openStream: (url, options) => {
                guard();
                return openStreamWith(id, url, options);
            },
            cached: (key, options, loader) => {
                guard();
                return netCachedWith(store, false, key, options, loader);
            },
            peek: key => {
                guard();
                return netPeekWith(store, false, key);
            },
            refresh: (key, options, loader) => {
                guard();
                return netRefreshWith(store, false, key, options, loader);
            },
            clearCache: key => {
                guard();
                return netClearCacheWith(store, false, key);
            },
            getCookie: url => {
                guard();
                return _sessionGetCookie(id, url);
            },
            setCookie: (url, cookie) => {
                guard();
                return _sessionSetCookie(id, url, cookie);
            },
            sleep: ms => {
                guard();
                return netSleep(ms);
            },
            close: () => {
                if (closed) return undefined;
                closed = true;
                for (const entry of store.values()) entry.cancelled = true;
                store.clear();
                _sessionClose(id);
                return undefined;
            }
        };
    }

    const net = {
        http,
        req: (url, options) => httpWith(null, url, Object.assign({}, options, {async: false})),
        json: (url, options) => jsonWith(null, url, options),
        batch: (requests, limit) => batchWith(null, requests, limit),
        map: netMap,
        cached: (key, options, loader) => netCachedWith(netCache, true, key, options, loader),
        peek: key => netPeekWith(netCache, true, key),
        refresh: (key, options, loader) => netRefreshWith(netCache, true, key, options, loader),
        clearCache: key => netClearCacheWith(netCache, true, key),
        sleep: netSleep,
        getUserAgent: () => _getUserAgent(),
        stream: (url, options) => streamWith(null, url, options),
        openStream: (url, options) => openStreamWith(null, url, options),
        session: createSession,
        getCookie: url => _getCookie(url),
        setCookie: (url, cookie) => _setCookie(url, cookie)
    };

    globalThis.net = net;
    globalThis.htmlQuery = (html, plan) => {
        if (typeof html !== "string") throw new TypeError("html must be a string");
        if (!plan || typeof plan !== "object") throw new TypeError("plan must be an object");
        const result = _htmlQuery(html, plan);
        if (!result) throw new Error("htmlQuery failed");
        return result;
    };
})();

/* ===== 兼容层：影视TV / TVBox / FongMi 系脚本常用运行时 ===== */
(function () {
    var g = globalThis;

    if (typeof g.console === "undefined" || g.console === null) {
        g.console = {};
    }
    ["log", "info", "warn", "error", "debug"].forEach(function (key) {
        if (typeof g.console[key] !== "function") {
            g.console[key] = function () {};
        }
    });

    var timers = {};
    var seq = 1;
    var nativeTimeout = typeof g.setTimeout === "function" ? g.setTimeout : function (fn) { fn(); };
    g.setTimeout = function (fn, delay) {
        var id = seq++;
        timers[id] = true;
        nativeTimeout(function () {
            if (!timers[id]) return;
            delete timers[id];
            try { fn(); } catch (e) {}
        }, delay || 0);
        return id;
    };
    g.clearTimeout = function (id) { delete timers[id]; };
    g.setInterval = function (fn, delay) {
        var id = seq++;
        timers[id] = true;
        function step() {
            if (!timers[id]) return;
            try { fn(); } catch (e) {}
            nativeTimeout(step, delay || 0);
        }
        nativeTimeout(step, delay || 0);
        return id;
    };
    g.clearInterval = function (id) { delete timers[id]; };

    g.alert = function (msg) {
        try { g.console.log(String(msg)); } catch (e) {}
    };

    var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    function utf8Bytes(s) {
        var str = String(s), out = [], i, c;
        for (i = 0; i < str.length; i++) {
            c = str.charCodeAt(i);
            if (c < 0x80) {
                out.push(c);
            } else if (c < 0x800) {
                out.push(0xC0 | (c >> 6), 0x80 | (c & 63));
            } else {
                out.push(0xE0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
            }
        }
        return out;
    }
    function bytesUtf8(bytes) {
        var s = "", i = 0, b, b2, b3;
        while (i < bytes.length) {
            b = bytes[i++];
            if (b < 0x80) {
                s += String.fromCharCode(b);
            } else if (b < 0xE0) {
                s += String.fromCharCode(((b & 31) << 6) | (bytes[i++] & 63));
            } else {
                b2 = bytes[i++];
                b3 = bytes[i++];
                s += String.fromCharCode(((b & 15) << 12) | ((b2 & 63) << 6) | (b3 & 63));
            }
        }
        return s;
    }
    function bytesToB64(bytes) {
        var out = "", i, b0, b1, b2;
        for (i = 0; i < bytes.length; i += 3) {
            b0 = bytes[i];
            b1 = bytes[i + 1];
            b2 = bytes[i + 2];
            out += B64[b0 >> 2];
            out += B64[((b0 & 3) << 4) | ((b1 === undefined ? 0 : b1) >> 4)];
            out += b1 === undefined ? "=" : B64[((b1 & 15) << 2) | ((b2 === undefined ? 0 : b2) >> 6)];
            out += b2 === undefined ? "=" : B64[b2 & 63];
        }
        return out;
    }
    function b64ToBytes(text) {
        var s = String(text).replace(/[^A-Za-z0-9+/=]/g, ""), bytes = [], i, c0, c1, c2, c3;
        for (i = 0; i < s.length; i += 4) {
            c0 = B64.indexOf(s[i]);
            c1 = B64.indexOf(s[i + 1]);
            c2 = B64.indexOf(s[i + 2]);
            c3 = B64.indexOf(s[i + 3]);
            if (c0 < 0 || c1 < 0) break;
            bytes.push((c0 << 2) | (c1 >> 4));
            if (c2 >= 0) bytes.push(((c1 & 15) << 4) | (c2 >> 2));
            if (c3 >= 0) bytes.push(((c2 & 3) << 6) | c3);
        }
        return bytes;
    }
    g.btoa = function (text) { return bytesToB64(utf8Bytes(text)); };
    g.atob = function (text) { return bytesUtf8(b64ToBytes(text)); };
    g.base64X = g.btoa;
    if (typeof g.base64Encode !== "function") g.base64Encode = g.btoa;
    if (typeof g.base64Decode !== "function") g.base64Decode = g.atob;

    if (typeof g.local !== "undefined" && g.local !== null) {
        var store = {
            getItem: function (key) {
                try { return g.local.get("localStorage", String(key)); } catch (e) { return ""; }
            },
            setItem: function (key, value) {
                try { g.local.set("localStorage", String(key), String(value)); } catch (e) {}
            },
            removeItem: function (key) {
                try { g.local.delete("localStorage", String(key)); } catch (e) {}
            },
            clear: function () {}
        };
        store.get = store.getItem;
        store.set = store.setItem;
        store.delete = store.removeItem;
        g.localStorage = store;
    }

    g.fetch = function (url, options) {
        return new Promise(function (resolve) {
            var opt = options || {};
            var req = {
                method: (opt.method || "GET").toUpperCase(),
                headers: opt.headers || {},
                buffer: 0,
                complete: function (res) {
                    var content = res && res.content !== undefined ? String(res.content) : "";
                    resolve({
                        ok: !!(res && res.code >= 200 && res.code < 300),
                        status: res ? res.code : 0,
                        headers: (res && res.headers) || {},
                        url: url,
                        text: function () { return Promise.resolve(content); },
                        json: function () {
                            try { return Promise.resolve(JSON.parse(content)); }
                            catch (e) { return Promise.reject(e); }
                        }
                    });
                }
            };
            if (opt.body !== undefined) req.body = opt.body;
            else if (opt.data !== undefined) req.data = opt.data;
            try { g._http(url, req); } catch (e) { req.complete(null); }
        });
    };

    var HEADER_HINTS = ["user-agent", "useragent", "referer", "referrer", "cookie",
        "content-type", "accept", "authorization", "origin", "host", "token"];

    function isHeaderLike(obj) {
        if (!obj) return false;
        for (var k in obj) {
            if (!Object.prototype.hasOwnProperty.call(obj, k)) continue;
            var lower = String(k).toLowerCase();
            for (var i = 0; i < HEADER_HINTS.length; i++) {
                if (lower === HEADER_HINTS[i] || lower.indexOf(HEADER_HINTS[i]) >= 0) return true;
            }
        }
        return false;
    }

    function splitArgs(a, b, c) {
        var params = null, headers = null, options = {};
        var objects = [];
        [a, b, c].forEach(function (arg) {
            if (arg === undefined || arg === null) return;
            if (typeof arg === "string") {
                if (params === null) params = arg;
                return;
            }
            if (typeof arg === "object") {
                if (arg.method || arg.body !== undefined || arg.data !== undefined ||
                    arg.headers || arg.async !== undefined || arg.timeout !== undefined ||
                    arg.redirect !== undefined || arg.buffer !== undefined) {
                    Object.assign(options, arg);
                } else {
                    objects.push(arg);
                }
            }
        });
        // CatVod / 影视TV 的 JsHttp 契约是 (url, params, headers)：
        // 第一个普通对象是「参数」，第二个才是「请求头」。
        // 之前写反了（第一个当请求头），脚本传的参数被塞进 header、请求头被当参数，
        // 结果整个请求打空——这就是"js 桥无效"的根源。
        if (objects.length >= 2) {
            params = objects[0];
            headers = objects[1];
        } else if (objects.length === 1) {
            if (params === null && isHeaderLike(objects[0])) headers = objects[0];
            else if (params === null) params = objects[0];
            else headers = objects[0];
        }
        return { params: params, headers: headers, options: options };
    }

    function requestSync(method, url, a, b, c) {
        var split = splitArgs(a, b, c);
        var opt = Object.assign({}, split.options);
        opt.method = method;
        if (split.headers) opt.headers = split.headers;
        if (split.params) {
            if (method === "POST") {
                opt.data = split.params;
            } else if (typeof split.params === "string") {
                // Req.params 只接受对象，字符串参数得自己拼到 url 上
                url = url + (url.indexOf("?") >= 0 ? "&" : "?") + split.params;
            } else {
                opt.params = split.params;
            }
        }
        try {
            var res = g._http(url, opt);
            if (!res) return "";
            if (typeof res === "string") return res;
            var content = res.content !== undefined ? res.content : (res.body !== undefined ? res.body : "");
            return content === null || content === undefined ? "" : String(content);
        } catch (e) {
            return "";
        }
    }

    if (typeof g.http === "function") {
        g.http.get = function (url, a, b, c) { return requestSync("GET", url, a, b, c); };
        g.http.post = function (url, a, b, c) { return requestSync("POST", url, a, b, c); };
        g.http.getResponseCode = function (url, a, b) {
            var split = splitArgs(a, b, null);
            var opt = Object.assign({ method: "GET", redirect: 0 }, split.options);
            if (split.headers) opt.headers = split.headers;
            try {
                var res = g._http(url, opt);
                return res && res.code ? Number(res.code) : -1;
            } catch (e) {
                return -1;
            }
        };
        g.http.getHeaderForLocation = function (url, a, b) {
            var split = splitArgs(a, b, null);
            var opt = Object.assign({ method: "GET", redirect: 0 }, split.options);
            if (split.headers) opt.headers = split.headers;
            try {
                var res = g._http(url, opt);
                if (!res || !res.headers) return "";
                var headers = res.headers || {};
                return headers.location || headers.Location || "";
            } catch (e) {
                return "";
            }
        };
        g.http.toBytes = function (url, a, b) {
            var split = splitArgs(a, b, null);
            var opt = Object.assign({ method: "GET", buffer: 1 }, split.options);
            if (split.headers) opt.headers = split.headers;
            try {
                var res = g._http(url, opt);
                return (res && res.content) || [];
            } catch (e) {
                return [];
            }
        };
    }
})();
