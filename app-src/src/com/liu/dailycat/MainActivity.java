package com.liu.dailycat;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class MainActivity extends Activity {

    /** 版本信息地址：改成你自己的仓库地址即可。
     *  jsDelivr 是 GitHub 的 CDN 镜像，国内可访问；主地址不通时可换 raw.githubusercontent.com */
    private static final String[] UPDATE_URLS = {
            "https://cdn.jsdelivr.net/gh/Streamfish-lab/daycat@main/version.json",
            "https://fastly.jsdelivr.net/gh/Streamfish-lab/daycat@main/version.json",
            "https://raw.githubusercontent.com/Streamfish-lab/daycat/main/version.json"
    };

    private static final String HOST = "https://app.local/";
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private boolean pendingInstall = false;      // 去过「未知来源安装」授权页，回来接着装
    private volatile boolean dlRunning = false;  // 正在下载新版
    private volatile boolean dlStop = false;     // 下载取消标记
    private static final int REQ_FILE = 4321;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setBackgroundColor(Color.parseColor("#FAF8F3"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(HOST)) {
                    String path = url.substring(HOST.length());
                    int q = path.indexOf('?');
                    if (q >= 0) path = path.substring(0, q);
                    int h = path.indexOf('#');
                    if (h >= 0) path = path.substring(0, h);
                    if (path.length() == 0) path = "index.html";
                    try {
                        InputStream is = getAssets().open("www/" + path);
                        return new WebResourceResponse(mimeOf(path), "utf-8", is);
                    } catch (IOException e) {
                        // 资源不存在时返回 404，避免 WebView 去真实网络解析这个假域名
                        InputStream empty = new java.io.ByteArrayInputStream(new byte[0]);
                        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                                new java.util.HashMap<String, String>(), empty);
                    }
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u.toString().startsWith(HOST)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = cb;
                try {
                    Intent i = params.createIntent();
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(i, REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#FAF8F3"));
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        applySystemBars();

        boolean restored = false;
        if (savedInstanceState != null) {
            try {
                restored = web.restoreState(savedInstanceState) != null;
            } catch (Exception ignored) {
            }
        }
        if (!restored) web.loadUrl(HOST + "index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (res == Activity.RESULT_OK && data != null) {
                if (data.getDataString() != null) {
                    results = new Uri[]{Uri.parse(data.getDataString())};
                } else if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    private void openAppInfoInner() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private final Set<Integer> pendingRc = new HashSet<Integer>();

    private void setAlarms(String json) {
        AlarmSync.persist(this, json);
        AlarmSync.set(this, json, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 回到前台时让页面重新登记一次闹钟（划掉 App 会被系统清掉闹钟，重开即恢复）
        callJs("window.__resyncAlarms&&window.__resyncAlarms()");
        // 进前台就把小组件重画一次：用户打开 App 一定看到最新的今天。
        // 重画要出位图压 WebP，放子线程，别卡住进页面。
        final android.content.Context _wc = this;
        new Thread(new Runnable() {
            public void run() {
                try { WidgetProvider.refreshAll(_wc); } catch (Exception ignored) { }
            }
        }).start();
        // 刚从「允许安装未知应用」授权页回来：已经给了权限就直接把安装器拉起来
        if (pendingInstall) {
            pendingInstall = false;
            if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) {
                installApkInternal();
            }
        }
    }

    /** 拉起系统安装器；没给「未知应用」权限就先跳设置页，回来由 onResume 接着装 */
    private void installApkInternal() {
        try {
            java.io.File f = new java.io.File(ApkProvider.dirOf(this), ApkProvider.FILE_NAME);
            if (!f.exists() || f.length() < 20000) return;
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                pendingInstall = true;
                Intent it = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                return;
            }
            Uri uri = ApkProvider.uriOf(ApkProvider.FILE_NAME);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            try {
                Uri uri = ApkProvider.uriOf(ApkProvider.FILE_NAME);
                Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                i.setData(uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) {
            try {
                web.saveState(outState);
            } catch (Exception ignored) {
            }
        }
    }

    private static final String CH_ID = "reminder";

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (nm.getNotificationChannel(CH_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CH_ID, "任务提醒",
                        NotificationManager.IMPORTANCE_HIGH);
                ch.enableVibration(true);
                ch.setVibrationPattern(new long[]{0, 220, 120, 220});
                ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
                ch.enableLights(true);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private boolean hasNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void callJs(final String js) {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    web.evaluateJavascript(js, null);
                } catch (Exception ignored) {
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 101) {
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            callJs("window.__notifyPerm&&window.__notifyPerm(" + (ok ? "true" : "false") + ")");
        }
    }

    /** 提供「导出备份到微信/文件」等原生能力 */
    public class Bridge {
        @JavascriptInterface
        public void shareBackup(final String json) {
            if (json == null || json.length() < 2) return;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_SUBJECT, "Schedule 备份");
                        i.putExtra(Intent.EXTRA_TEXT, json);
                        startActivity(Intent.createChooser(i, "保存或发送备份"));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        /** 每周自动备份：写进应用私有目录，留最近 4 份 */
        @JavascriptInterface
        public String saveBackup(String json) {
            if (json == null || json.length() < 2) return "";
            try {
                java.io.File dir = new java.io.File(getFilesDir(), "backups");
                if (!dir.exists()) dir.mkdirs();
                java.text.SimpleDateFormat f =
                        new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US);
                java.io.File out = new java.io.File(dir, "backup-" + f.format(new java.util.Date()) + ".json");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
                fo.write(json.getBytes("UTF-8"));
                fo.close();
                java.io.File[] all = dir.listFiles();
                if (all != null && all.length > 4) {
                    java.util.Arrays.sort(all, new java.util.Comparator<java.io.File>() {
                        public int compare(java.io.File a, java.io.File b) {
                            return Long.compare(b.lastModified(), a.lastModified());
                        }
                    });
                    for (int i = 4; i < all.length; i++) all[i].delete();
                }
                return String.valueOf(out.lastModified());
            } catch (Exception e) {
                return "";
            }
        }

        /** 列出应用私有目录里的自动备份 */
        @JavascriptInterface
        public String listBackups() {
            try {
                java.io.File dir = new java.io.File(getFilesDir(), "backups");
                java.io.File[] all = dir.listFiles();
                if (all == null) return "[]";
                java.util.Arrays.sort(all, new java.util.Comparator<java.io.File>() {
                    public int compare(java.io.File a, java.io.File b) {
                        return Long.compare(b.lastModified(), a.lastModified());
                    }
                });
                JSONArray arr = new JSONArray();
                for (int i = 0; i < all.length && i < 8; i++) {
                    JSONObject o = new JSONObject();
                    o.put("name", all[i].getName());
                    o.put("time", all[i].lastModified());
                    o.put("size", all[i].length());
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        /** 读某一份自动备份的内容 */
        @JavascriptInterface
        public String readBackup(String name) {
            try {
                if (name == null || name.contains("/") || name.contains("..")) return "";
                java.io.File f = new java.io.File(new java.io.File(getFilesDir(), "backups"), name);
                if (!f.exists() || !f.isFile()) return "";
                java.io.FileInputStream fi = new java.io.FileInputStream(f);
                byte[] buf = new byte[(int) f.length()];
                int n = fi.read(buf);
                fi.close();
                if (n <= 0) return "";
                return new String(buf, 0, n, "UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        /** 返回 "最近时间:份数"，没有则空串 */
        @JavascriptInterface
        public String backupInfo() {
            try {
                java.io.File dir = new java.io.File(getFilesDir(), "backups");
                java.io.File[] all = dir.listFiles();
                if (all == null || all.length == 0) return "";
                long mx = 0;
                for (java.io.File f : all) mx = Math.max(mx, f.lastModified());
                return mx + ":" + all.length;
            } catch (Exception e) {
                return "";
            }
        }

        /** 版本号（整数），用于判断有没有新版本 */
        @JavascriptInterface
        public int appVersionCode() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            } catch (Exception e) {
                return 0;
            }
        }

        /** 版本名，如 2.0.7 */
        @JavascriptInterface
        public String appVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }

        /** 从版本文件里抠出 code；抠不到返回 -1 */
        private int codeOf(String json) {
            try {
                int i = json.indexOf("\"code\"");
                if (i < 0) return -1;
                int c = json.indexOf(':', i);
                if (c < 0) return -1;
                int j = c + 1;
                StringBuilder sb = new StringBuilder();
                while (j < json.length()) {
                    char ch = json.charAt(j);
                    if (ch >= '0' && ch <= '9') { sb.append(ch); j++; }
                    else if (sb.length() > 0) break;
                    else if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') j++;
                    else break;
                }
                return sb.length() > 0 ? Integer.parseInt(sb.toString()) : -1;
            } catch (Exception e) {
                return -1;
            }
        }

        /** 检查更新：原生发请求，避开 WebView 的跨域限制。结果回调 window.__onUpdate(json|null) */
        @JavascriptInterface
        public void checkUpdate() {
            new Thread(new Runnable() {
                public void run() {
                    // 三条线路全查一遍，取 code 最大的那份。
                    // 原因：jsDelivr 对 @main 有约 12 小时缓存，刚发版时它可能还吐旧版，
                    // 而 raw.githubusercontent 是没有缓存的——只认"第一个成功的"会拿不到新版。
                    String body = null;
                    int bestCode = -1;
                    for (int k = 0; k < UPDATE_URLS.length; k++) {
                        java.net.HttpURLConnection c = null;
                        try {
                            c = (java.net.HttpURLConnection) new java.net.URL(UPDATE_URLS[k]).openConnection();
                            c.setConnectTimeout(5000);
                            c.setReadTimeout(5000);
                            c.setInstanceFollowRedirects(true);
                            c.setRequestProperty("User-Agent", "Schedule-UpdateCheck");
                            c.setRequestProperty("Cache-Control", "no-cache");
                            if (c.getResponseCode() == 200) {
                                java.io.InputStream in = c.getInputStream();
                                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                                byte[] buf = new byte[4096];
                                int n;
                                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                                in.close();
                                String t = bo.toString("UTF-8");
                                if (t != null && t.indexOf('{') >= 0) {   // 防止拿到错误页
                                    int cd = codeOf(t);
                                    if (cd > bestCode) { bestCode = cd; body = t; }
                                }
                            }
                        } catch (Exception ignored) {
                        } finally {
                            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
                        }
                    }
                    final String res = body;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                String arg = (res == null) ? "null" : JSONObject.quote(res);
                                web.evaluateJavascript(
                                        "window.__onUpdate&&window.__onUpdate(" + arg + ")", null);
                            } catch (Exception ignored) {
                            }
                        }
                    });
                }
            }).start();
        }

        /* ---------------- 应用内下载新版：不再跳浏览器 ---------------- */

        /** 按顺序试 urls 里的每条线路，成功一个就停。回调 __onDlProgress / __onDlDone / __onDlFail */
        @JavascriptInterface
        public void downloadApk(final String urlsJson) {
            if (dlRunning) return;
            dlRunning = true;
            dlStop = false;
            final java.io.File dir = ApkProvider.dirOf(MainActivity.this);
            final java.io.File out = new java.io.File(dir, ApkProvider.FILE_NAME);
            final java.io.File part = new java.io.File(dir, "Schedule.part");
            new Thread(new Runnable() {
                public void run() {
                    String lastErr = "所有线路都不通";
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray(urlsJson);
                        for (int i = 0; i < arr.length(); i++) {
                            if (dlStop) { lastErr = "已取消"; break; }
                            String u = arr.optString(i, "");
                            if (u == null || u.length() < 10) continue;
                            callJs("window.__onDlProgress&&window.__onDlProgress(0)");
                            String err = fetchApk(u, part, out);
                            if (err == null) {
                                dlRunning = false;
                                callJs("window.__onDlDone&&window.__onDlDone()");
                                return;
                            }
                            lastErr = err;
                        }
                    } catch (Exception e) {
                        lastErr = "下载出错";
                    }
                    try { part.delete(); } catch (Exception ignored) { }
                    dlRunning = false;
                    callJs("window.__onDlFail&&window.__onDlFail(" + JSONObject.quote(lastErr) + ")");
                }
            }).start();
        }

        /** 返回 null = 成功；否则返回失败原因 */
        private String fetchApk(String u, java.io.File part, java.io.File out) {
            java.net.HttpURLConnection c = null;
            java.io.FileOutputStream fo = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                c.setConnectTimeout(9000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) Schedule-Updater");
                c.setRequestProperty("Accept", "*/*");
                int code = c.getResponseCode();
                if (code != 200) return "线路无响应 " + code;
                long total = c.getContentLength();
                InputStream in = c.getInputStream();
                fo = new java.io.FileOutputStream(part);
                byte[] buf = new byte[16384];
                long got = 0;
                int n, lastPct = -1;
                while ((n = in.read(buf)) > 0) {
                    if (dlStop) { try { fo.close(); } catch (Exception ignored) { } return "已取消"; }
                    fo.write(buf, 0, n);
                    got += n;
                    if (total > 0) {
                        int pct = (int) (got * 100 / total);
                        if (pct >= lastPct + 2 || pct >= 100) {
                            lastPct = pct;
                            callJs("window.__onDlProgress&&window.__onDlProgress(" + pct + ")");
                        }
                    } else if (got / 65536 != lastPct) {
                        lastPct = (int) (got / 65536);
                        callJs("window.__onDlProgress&&window.__onDlProgress(-1)");
                    }
                }
                in.close();
                fo.close();
                fo = null;
                if (got < 20000) { try { part.delete(); } catch (Exception ignored) { } return "拿到的文件太小"; }
                // 校验：APK 本质是 zip，开头两字节必须是 PK，防止下回来一个报错页
                byte[] head = new byte[2];
                java.io.FileInputStream fi = new java.io.FileInputStream(part);
                int r = fi.read(head);
                fi.close();
                if (r < 2 || head[0] != 'P' || head[1] != 'K') {
                    try { part.delete(); } catch (Exception ignored) { }
                    return "下回来的不是安装包";
                }
                if (out.exists()) out.delete();
                if (!part.renameTo(out)) return "写入失败";
                return null;
            } catch (Exception e) {
                return "连接超时";
            } finally {
                try { if (fo != null) fo.close(); } catch (Exception ignored) { }
                try { if (c != null) c.disconnect(); } catch (Exception ignored) { }
            }
        }

        /** 本地是否已经有下好的安装包；有则返回字节数 */
        @JavascriptInterface
        public String apkReady() {
            try {
                java.io.File f = new java.io.File(ApkProvider.dirOf(MainActivity.this), ApkProvider.FILE_NAME);
                return (f.exists() && f.length() > 20000) ? String.valueOf(f.length()) : "";
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void installApk() {
            runOnUiThread(new Runnable() {
                public void run() { installApkInternal(); }
            });
        }

        @JavascriptInterface
        public void stopDownload() {
            dlStop = true;
        }

        /** App 里主动要求重画小组件：只要页面数据/日期是新的，就立刻同步到桌面 */
        @JavascriptInterface
        public void refreshWidget() {
            final android.content.Context _wc = MainActivity.this;
            new Thread(new Runnable() {
                public void run() {
                    try { WidgetProvider.refreshAll(_wc); } catch (Exception ignored) { }
                }
            }).start();
        }

        @JavascriptInterface
        public boolean isDownloading() {
            return dlRunning;
        }

        /** 用系统浏览器打开链接（下载新版 APK） */
        @JavascriptInterface
        public void openUrl(final String url) {
            if (url == null) return;
            final String u = url.trim();
            if (!u.startsWith("https://") && !u.startsWith("http://")) return;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean hasNotifyPermission() {
            return MainActivity.this.hasNotifyPermission();
        }

        @JavascriptInterface
        public void askNotifyPermission() {
            ensureChannel();
            if (Build.VERSION.SDK_INT >= 33 && !MainActivity.this.hasNotifyPermission()) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                        } catch (Exception ignored) {
                        }
                    }
                });
            } else {
                callJs("window.__notifyPerm&&window.__notifyPerm(true)");
            }
        }

        @JavascriptInterface
        public void notify(String title, String body) {
            if (!MainActivity.this.hasNotifyPermission()) return;
            int nid = (title == null ? "x" : title).hashCode() & 0x7fffffff;
            Notifier.show(MainActivity.this, title, body, nid);
        }

        /** 立刻发一条测试通知；返回 ok / noperm / off / fail，前端据此给提示 */
        @JavascriptInterface
        public String testNotify() {
            try {
                String[] p = notifyState().split("\\|");
                if (p.length > 0 && !"1".equals(p[0])) return "noperm";
                if (p.length > 1 && !"1".equals(p[1])) return "off";
                // 每次换一个 id：旧代码固定用 99001，同一条通知重复发时系统只做原地更新，
                // 不响不弹，看着就像"点了没反应"
                int nid = (int) (System.currentTimeMillis() % 900000) + 1000;
                boolean ok = Notifier.show(MainActivity.this, "测试通知",
                        "看到这条说明通知是通的，问题在定时那边", nid);
                return ok ? "ok" : "fail";
            } catch (Exception e) {
                return "fail";
            }
        }

        /** 小组件状态：同步了几门课 / 最近一次渲染错误 */
        @JavascriptInterface
        public String widgetDiag() {
            try {
                android.content.SharedPreferences sp =
                        getSharedPreferences(WidgetProvider.PREF, MODE_PRIVATE);
                String data = sp.getString(WidgetProvider.KEY, "");
                int n = 0;
                if (data != null && data.length() > 0) {
                    try {
                        n = new JSONObject(data).optJSONArray("courses") == null ? 0
                                : new JSONObject(data).optJSONArray("courses").length();
                    } catch (Exception ignored) {
                    }
                }
                int cnt = sp.getInt("cnt", -1);
                return n + "|" + sp.getString("err", "") + "|" + sp.getString("last", "") + "|" + cnt
                        + "|" + sp.getString("size", "?");
            } catch (Exception e) {
                return "0|";
            }
        }

        /** 网页端把课表推给桌面小组件 */
        @JavascriptInterface
        public void pushWidget(String json) {
            try {
                getSharedPreferences(WidgetProvider.PREF, MODE_PRIVATE).edit()
                        .putString(WidgetProvider.KEY, json).apply();
            } catch (Exception ignored) {
            }
            WidgetProvider.refreshAll(MainActivity.this);
        }

        /** 最近一次发通知的结果：时间|ok|频道|重要性|id */
        @JavascriptInterface
        public String notifyLog() {
            return Notifier.lastLog(MainActivity.this);
        }

        /** 上一次提醒的「计划时间|实际时间」，用于检查延迟 */
        @JavascriptInterface
        public String lastAlarmDiag() {
            try {
                return getSharedPreferences("fired", MODE_PRIVATE).getString("lastDiag", "");
            } catch (Exception e) {
                return "";
            }
        }

        /** 已登记到系统闹钟的提醒数量与最近时间 */
        @JavascriptInterface
        public String alarmInfo() {
            return AlarmSync.info();
        }

        /** 把未来的提醒交给系统闹钟，App 不在后台也能响 */
        @JavascriptInterface
        public void scheduleReminders(String json) {
            MainActivity.this.setAlarms(json);
        }

        /** 通知链路诊断：权限|App通知总开关|当前频道重要性（0 表示被关） */
        @JavascriptInterface
        public String notifyState() {
            try {
                String perm = (Build.VERSION.SDK_INT >= 33)
                        ? (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ? "1" : "0")
                        : "1";
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                String en = (nm != null && nm.areNotificationsEnabled()) ? "1" : "0";
                int imp = -1;
                if (nm != null && Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel ch = nm.getNotificationChannel(Notifier.activeChannel(MainActivity.this));
                    if (ch != null) imp = ch.getImportance();
                }
                return perm + "|" + en + "|" + imp;
            } catch (Exception e) {
                return "1|1|-1";
            }
        }

        /** 直接跳到本应用的通知频道页（能单独开关「任务提醒」） */
        @JavascriptInterface
        public void openChannelSettings() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        i.putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.activeChannel(MainActivity.this));
                        startActivity(i);
                    } catch (Exception e) {
                        try {
                            Intent j = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                            j.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                            startActivity(j);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }

        /** 真·系统闹钟测试：sec 秒后由 AlarmManager 发一条通知，可锁屏验证 */
        @JavascriptInterface
        public void testAlarm(int sec) {
            try {
                long at = System.currentTimeMillis() + Math.max(5, sec) * 1000L;
                JSONArray arr = new JSONArray();
                JSONObject o = new JSONObject();
                o.put("id", "testalarm" + at);
                o.put("at", at);
                o.put("title", "测试提醒");
                o.put("body", "锁屏时也能看到这条，说明后台提醒是通的");
                arr.put(o);
                AlarmSync.set(MainActivity.this, arr.toString(), false);
            } catch (Exception e) {
            }
        }

        /** 是否允许"精确闹钟"（Android 12 起需要用户单独打开） */
        @JavascriptInterface
        public boolean canExactAlarm() {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }

        /** 直接打开本应用的通知设置页 */
        @JavascriptInterface
        public void openNotifySettings() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(i);
                    } catch (Exception e) {
                        openAppInfoInner();
                    }
                }
            });
        }

        /** 直接打开本应用的系统信息页（电池、自启动等都在这一层） */
        @JavascriptInterface
        public void openAppInfo() {
            runOnUiThread(new Runnable() {
                public void run() {
                    openAppInfoInner();
                }
            });
        }

        /** 跳去系统的"闹钟和提醒"开关 */
        @JavascriptInterface
        public void openExactAlarmSetting() {
            if (Build.VERSION.SDK_INT < 31) return;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Settings.ACTION_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }

        @JavascriptInterface
        public void vibrate() {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) v.vibrate(new long[]{0, 150, 80, 150}, -1);
            } catch (Exception ignored) {
            }
        }
    }

    private void applySystemBars() {
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bg = Color.parseColor(dark ? "#14120E" : "#FAF8F3");
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(bg);
            w.setNavigationBarColor(bg);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            View dv = w.getDecorView();
            int flags = dv.getSystemUiVisibility();
            if (dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                if (dark) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            dv.setSystemUiVisibility(flags);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemBars();
    }

    private String mimeOf(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
