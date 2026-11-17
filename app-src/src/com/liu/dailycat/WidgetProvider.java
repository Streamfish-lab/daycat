package com.liu.dailycat;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 桌面小组件：一周课表纵览 */
public class WidgetProvider extends AppWidgetProvider {

    public static final String PREF = "widget";
    public static final String KEY = "data";
    public static final String ACT_NAV = "com.liu.dailycat.WIDGET_NAV";
    public static final String TICK = "com.liu.dailycat.WIDGET_TICK";

    private static final String[] WD1 = {"一", "二", "三", "四", "五", "六", "日"};
    /** 三档画布高度（dp）：4×4 / 4×5 / 4×6 */
    private static final int[] TIER_H = {300, 400, 490};

    /** 三个独立组件各用自己的档位：0=4×4，1=4×5，2=4×6 */
    protected int tier() {
        return 0;
    }

    @Override
    public void onUpdate(Context c, final AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(c, mgr, id, tier());
        scheduleDaily(c);
        // 新建组件时系统给的尺寸还没算好，第一帧会被拉伸；稍后按真实尺寸重画一次
        final Context app = c.getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                refreshAll(app);
            }
        }, 900);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager mgr, int id, Bundle opts) {
        render(c, mgr, id, tier());
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        try {
            if (i == null) return;
            String a = i.getAction();
            if (TICK.equals(a)) {
                refreshAll(c);
                scheduleDaily(c);
            } else if (Intent.ACTION_DATE_CHANGED.equals(a)
                    || Intent.ACTION_TIME_CHANGED.equals(a)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
                /* 跨日 / 手动改时间 / 换时区。
                   注意：Android 8.0 起静态注册收不到 DATE_CHANGED 这类隐式广播，
                   这条路径只是老系统上的额外保险；真正保证每天翻页的是
                   scheduleDaily() 排的 00:05 / 06:05 / 12:05 三个闹钟，
                   以及 appwidget 的 30 分钟周期刷新。 */
                refreshAll(c);
            }
        } catch (Exception ignored) {
        }
    }

    public static void refreshAll(Context c) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            Class<?>[] cls = {WidgetProvider.class, Widget45.class, Widget46.class};
            int total = 0;
            for (int t = 0; t < cls.length; t++) {
                int[] ids = mgr.getAppWidgetIds(new ComponentName(c, cls[t]));
                if (ids == null) continue;
                total += ids.length;
                for (int id : ids) render(c, mgr, id, t);
            }
            try {
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                        .edit().putInt("cnt", total).apply();
            } catch (Exception ignored) {
            }
            scheduleDaily(c);   /* 幂等，只是把明天的闹钟重排一遍 */
        } catch (Exception ignored) {
        }
    }

    /** 每天 00:05 / 06:05 / 12:05 各刷一次。用 RTC_WAKEUP + setExactAndAllowWhileIdle，
     *  否则 Doze 会把闹钟推到维护窗口，跨零点那一次翻周就漏了。
     *
     *  为什么是三个时刻：国产 ROM 的省电策略偶尔会吞掉一次闹钟，只排一个点的话，
     *  被吞掉的那天小组件就会一直停在昨天。06:05 是给「早八」兜的底——
     *  就算 0 点那次没响，起床时也一定是对的一天。 */
    public static void scheduleDaily(Context c) {
        try {
            android.app.AlarmManager am =
                    (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent it = new Intent(c, WidgetProvider.class);
            it.setAction(TICK);
            arm(am, c, it, 7011, 0);
            arm(am, c, it, 7013, 6);
            arm(am, c, it, 7012, 12);
        } catch (Exception ignored) {
        }
    }

    /** 最后一个组件被移除时，把每日闹钟撤掉，不再空唤醒 */
    @Override
    public void onDisabled(Context c) {
        try {
            android.app.AlarmManager am =
                    (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent it = new Intent(c, WidgetProvider.class);
                it.setAction(TICK);
                int[] reqs = {7011, 7012, 7013};
                for (int r : reqs) {
                    PendingIntent pi = PendingIntent.getBroadcast(c, r, it,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    if (pi != null) am.cancel(pi);
                }
            }
        } catch (Exception ignored) {
        }
        super.onDisabled(c);
    }

    private static void arm(android.app.AlarmManager am, Context c, Intent it, int req, int hour) {
        try {
            PendingIntent pi = PendingIntent.getBroadcast(c, req, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, hour);
            t.set(Calendar.MINUTE, 5);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            /* 算「下一个 hour:05」：今天这个点还没到就排今天，到了才顺延到明天。
               原来是无条件 +1 天，在 00:00–00:05 之间被调用时会把当天那次直接跳过。 */
            if (t.getTimeInMillis() <= System.currentTimeMillis()) {
                t.add(Calendar.DAY_OF_YEAR, 1);
            }
            long at = t.getTimeInMillis();
            try {
                if (android.os.Build.VERSION.SDK_INT >= 23)
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, pi);
                else
                    am.setExact(android.app.AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception e2) {
                am.set(android.app.AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception ignored) {
        }
    }

    private static JSONObject load(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String s = sp.getString(KEY, "");
            if (s == null || s.length() == 0) return null;
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized void render(Context c, AppWidgetManager mgr, int id, int tier) {
        try {
            Bundle o = mgr.getAppWidgetOptions(id);
            int minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            int maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
            int maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            // 不少桌面（vivo/OPPO 等）拉大后只更新 MAX，MIN 还停在初始值。
            // 只读 MIN 就会按小尺寸画图、再被 fitXY 硬拉到实际大小 —— 卡片"被拉长"就是这么来的。
            // 所以取两者里较大的那个作为画布尺寸。
            int wDp = Math.max(minW, maxW);
            int hDp = Math.max(minH, maxH);
            if (wDp <= 0) wDp = 250;
            if (hDp <= 0) hDp = 300;

            // 三个独立组件，尺寸由各自 provider_info 定死；画布也用固定值，
            // 不再读桌面报的尺寸（各家报法不一，是之前反复出问题的根源）
            int stanH = TIER_H[tier];
            try {
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                        .putString("size", "4×" + (4 + tier) + " · " + 250 + "×" + stanH + "dp"
                                + " · 桌面报 " + wDp + "×" + hDp).apply();
            } catch (Exception ignored) {
            }

            boolean dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            JSONObject data = load(c);
            if (data != null) {
                String th = data.optString("theme", "auto");
                if ("dark".equals(th)) dark = true;
                else if ("light".equals(th)) dark = false;
            }

            Bitmap bmp = draw(c, data, 250, stanH, dark, tier);

            // 图写到文件里，再用 content:// 交给桌面：这样任意尺寸都不会撞上 1MB 上限
            java.io.File out = WidgetImageProvider.fileOf(c, id);
            java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.WEBP, 92, fo);
            fo.close();

            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setImageViewUri(R.id.wimg, WidgetImageProvider.uriOf(id));
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.wimg, pi);

            mgr.updateAppWidget(id, rv);
            try {
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                        .putString("last", System.currentTimeMillis() + "|" + bmp.getWidth() + "x"
                                + bmp.getHeight() + "|" + (out.length() / 1024) + "KB|" + id)
                        .putString("err", "").apply();
            } catch (Exception ignored) {
            }
        } catch (Throwable t) {
            // 组件画不出来时留个痕，方便排查（设置 pref 里能看到）
            try {
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                        .putString("err", System.currentTimeMillis() + "|" + t.getClass().getSimpleName()
                                + "|" + String.valueOf(t.getMessage())).apply();
            } catch (Exception ignored) {
            }
        }
    }

    /** 当前这次绘制用的有效密度（可能与屏幕密度不同：画布过大时会等比缩小） */
    private static float DEN = 0f;

    private static int dp(Context c, float v) {
        float den = DEN > 0 ? DEN : c.getResources().getDisplayMetrics().density;
        return Math.round(v * den);
    }

    private static int blend(int a, int b, float k) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * k);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * k);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k);
        return Color.rgb(r, g, bl);
    }

    private static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int weekOf(String termStart) {
        try {
            if (termStart == null || termStart.length() < 10) return 0;
            String[] p = termStart.split("-");
            Calendar a = Calendar.getInstance();
            a.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
            a.set(Calendar.MILLISECOND, 0);
            Calendar b = Calendar.getInstance();
            b.set(Calendar.HOUR_OF_DAY, 0);
            b.set(Calendar.MINUTE, 0);
            b.set(Calendar.SECOND, 0);
            b.set(Calendar.MILLISECOND, 0);
            long days = Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
            return (int) Math.floor(days / 7.0) + 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static Bitmap draw(Context c, JSONObject d, int wDp, int hDp, boolean dark, int tier) {
        // 固定画布会被 fitXY 缩放到桌面实际大小，多画 1.7 倍像素保证放大后不糊
        float density = c.getResources().getDisplayMetrics().density * 1.7f;
        int W = Math.max(Math.round(wDp * density), 10);
        int H = Math.max(Math.round(hDp * density), 10);
        // 现在图走文件不走 Binder，所以按真实像素画即可；
        // 只留一个内存上限（大组件最多约 2000px 边），避免极端情况下 OOM。
        int maxPx = 2600000;
        long px = (long) W * H;
        float sc = px > maxPx ? (float) Math.sqrt(maxPx / (double) px) : 1f;
        if (sc < 1f) {
            W = Math.max(10, Math.round(W * sc));
            H = Math.max(10, Math.round(H * sc));
            DEN = density * sc;
        } else {
            DEN = density;
        }
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        int bg = dark ? Color.parseColor("#16140F") : Color.parseColor("#FFFFFF");
        int fg = dark ? Color.parseColor("#F2EDE3") : Color.parseColor("#24211C");
        int sub = dark ? Color.parseColor("#8B8172") : Color.parseColor("#9AA0AE");

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg);
        cv.drawRoundRect(new RectF(0, 0, W, H), dp(c, 20), dp(c, 20), p);

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(sub);
        txt.setTextSize(dp(c, 11.5f));
        txt.setTextAlign(Paint.Align.CENTER);
        if (d == null) {
            cv.drawText("打开 Schedule 同步课表", W / 2f, H / 2f + dp(c, 4), txt);
            return bmp;
        }

        int pad = dp(c, 8);
        int headH = dp(c, 20);
        int weekH = dp(c, 15);

        Calendar now = Calendar.getInstance();
        int todayWd = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int week = weekOf(d.optString("termStart", ""));

        float ay = pad + dp(c, 11f);

        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        wp.setColor(fg);
        wp.setTextSize(dp(c, 12.5f));
        wp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        String wtxt = week > 0 ? ("第 " + week + " 周") : "本周";
        cv.drawText(wtxt, pad, ay, wp);

        Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
        rp.setColor(sub);
        rp.setTextSize(dp(c, 10.5f));
        rp.setTextAlign(Paint.Align.RIGHT);
        String dtxt = (now.get(Calendar.MONTH) + 1) + "/" + now.get(Calendar.DAY_OF_MONTH)
                + " 周" + WD1[todayWd];
        cv.drawText(dtxt, W - pad, ay, rp);

        JSONArray cs = d.optJSONArray("courses");
        List<JSONObject> list = new ArrayList<JSONObject>();
        if (cs != null) {
            for (int i = 0; i < cs.length(); i++) {
                JSONObject o = cs.optJSONObject(i);
                if (o == null) continue;
                JSONArray wk = o.optJSONArray("weeks");
                boolean on = (wk == null || wk.length() == 0);
                if (!on && week > 0) {
                    for (int k = 0; k < wk.length(); k++) {
                        if (wk.optInt(k) == week) { on = true; break; }
                    }
                }
                if (on) list.add(o);
            }
        }

        int gridTop = pad + headH + weekH;
        int gridH = H - gridTop - pad;
        float colW = (W - pad * 2) / 7f;

        Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
        hp.setTextSize(dp(c, 9.5f));
        hp.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 7; i++) {
            float cx = pad + i * colW + colW / 2f;
            float cy = pad + headH + dp(c, 6);
            if (i == todayWd) {
                hp.setColor(fg);
                hp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
                dot.setColor(fg);
                cv.drawCircle(cx, cy + dp(c, 6.5f), dp(c, 1.6f), dot);
            } else {
                hp.setColor(sub);
                hp.setTypeface(Typeface.DEFAULT);
            }
            cv.drawText(WD1[i], cx, cy, hp);
        }

        // 行按"本周实际有课的起始节"来分，不预设固定时段：
        // 一天 4 门课就有 4 行，几门课都不会被挤掉
        java.util.TreeSet<Integer> startSet = new java.util.TreeSet<Integer>();
        for (JSONObject o : list) startSet.add(Math.max(1, o.optInt("j1", 1)));
        List<Integer> starts = new ArrayList<Integer>(startSet);
        int rows = starts.size();

        if (rows == 0) {
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(sub);
            t.setTextSize(dp(c, 11));
            t.setTextAlign(Paint.Align.CENTER);
            cv.drawText("这一周没有课", W / 2f, gridTop + gridH / 2f + dp(c, 4), t);
            return bmp;
        }

        float rowH = gridH / (float) rows;
        // 4×4 课名+地点；4×5 加节次；4×6 再加开始时间
        int lines = tier + 2;
        if (rowH < dp(c, 52f) && lines > 2) lines--;
        if (rowH < dp(c, 26f)) lines = 1;

        for (int ri = 0; ri < rows; ri++) {
            final int j1 = starts.get(ri);
            float top = gridTop + ri * rowH;
            for (int wd = 0; wd < 7; wd++) {
                List<JSONObject> cell = new ArrayList<JSONObject>();
                for (JSONObject o : list) {
                    if (o.optInt("wd", 0) != wd) continue;
                    if (Math.max(1, o.optInt("j1", 1)) != j1) continue;
                    cell.add(o);
                }
                int m = cell.size();
                for (int k = 0; k < m; k++) {
                    JSONObject o = cell.get(k);
                    int j2 = Math.max(j1, o.optInt("j2", j1));
                    int span = Math.max(1, (j2 + 1) / 2 - (j1 - 1) / 2);
                    if (ri + span > rows) span = rows - ri;

                    // 卡片填满格子，只留 1dp 缝：整列看起来是连着的色块，紧凑
                    float spanH = span * rowH;
                    float ch = spanH - dp(c, 1.5f);
                    float c0 = top + dp(c, 0.75f);
                    float inner = Math.max(0, ch - dp(c, 2f));
                    float t = c0 + dp(c, 1f) + inner * k / m;
                    float b = c0 + ch - dp(c, 1f) - inner * (m - 1 - k) / m;

                    int col = Color.parseColor("#6E8F76");
                    try { col = Color.parseColor(o.optString("color", "#6E8F76")); } catch (Exception ignored) {
                    }
                    String name = o.optString("short", o.optString("name", ""));
                    String place = o.optString("place", "");
                    String tm = o.optString("t1", "");

                    float l = pad + wd * colW + dp(c, 1.5f);
                    float r = pad + (wd + 1) * colW - dp(c, 1.5f);

                    int soft = dark ? blend(col, Color.BLACK, 0.72f) : blend(col, Color.WHITE, 0.72f);
                    int tc = dark ? blend(col, Color.WHITE, 0.78f) : blend(col, Color.BLACK, 0.55f);

                    Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
                    box.setColor(soft);
                    cv.drawRoundRect(new RectF(l, t, r, b), dp(c, 8.5f), dp(c, 8.5f), box);

                    Paint np = new Paint(Paint.ANTI_ALIAS_FLAG);
                    np.setColor(tc);
                    np.setTextAlign(Paint.Align.CENTER);
                    np.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

                    float cellH = b - t;
                    float avail = (r - l) - dp(c, 2f);
                    float fsName;
                    if (lines >= 3) fsName = Math.min(dp(c, 12.5f), cellH * 0.28f);
                    else if (lines == 2) fsName = Math.min(dp(c, 12f), cellH * 0.38f);
                    else fsName = Math.min(dp(c, 12f), cellH * 0.55f);
                    fsName = Math.max(dp(c, 5.5f), fsName);
                    np.setTextSize(fsName);
                    while (np.measureText(name) > avail && fsName > dp(c, 7.5f)) {
                        fsName -= dp(c, 0.3f);
                        np.setTextSize(fsName);
                    }
                    String nm = fit(name, avail, np);
                    float cx = (l + r) / 2f;
                    float cyMid = (t + b) / 2f;

                    Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    pp.setColor(alpha(tc, 205));
                    pp.setTextAlign(Paint.Align.CENTER);
                    // 地点永远只占一行：字多就缩字号，绝不折行——
                    // 折行会让同一时段各天的卡片高度不齐，看着乱
                    float fsP = Math.max(dp(c, 6f), fsName * 0.62f);
                    pp.setTextSize(fsP);
                    while (place.length() > 0 && pp.measureText(place) > avail && fsP > dp(c, 3.8f)) {
                        fsP -= dp(c, 0.2f);
                        pp.setTextSize(fsP);
                    }
                    String pl = fit(place, avail, pp);
                    boolean hasPlace = place.length() > 0 && (lines >= 2);
                    // 4×5 第三行是节次；4×6 第四行是开始时间（取不到就不显示）
                    boolean showPeriod = lines >= 3;
                    String pe = showPeriod ? fit(j1 + "-" + j2 + "节", avail * 1.15f, pp) : "";
                    boolean showTime = lines >= 4 && tm.length() > 0;
                    String te = showTime ? fit(tm, avail * 1.20f, pp) : "";

                    float gapP = fsP * 1.32f;
                    int nlines = 1;
                    if (hasPlace) nlines++;
                    if (showPeriod) nlines++;
                    if (showTime) nlines++;
                    // 真正按墨迹高度居中（首行 baseline 到字顶 ≈ 0.8×字号）
                    float blockH = fsName * 0.80f + gapP * (nlines - 1) + fsP * 0.10f;
                    float y = cyMid - blockH / 2f + fsName * 0.80f;

                    cv.drawText(nm, cx, y, np);
                    if (hasPlace) {
                        y += gapP * 1.15f;
                        cv.drawText(pl, cx, y, pp);
                    }
                    if (showPeriod) {
                        y += gapP;
                        cv.drawText(pe, cx, y, pp);
                    }
                    if (showTime) {
                        y += gapP;
                        cv.drawText(te, cx, y, pp);
                    }
                }
            }
        }
        return bmp;
    }

    /** 每格能放几行文字：矮只放课名，中放课名+地点，高再加一节次 */
    private static String fit(String s, float maxW, Paint p) {
        if (s == null || s.length() == 0) return "";
        if (p.measureText(s) <= maxW) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s.substring(0, n) + "…") > maxW) n--;
        return n <= 1 ? s.substring(0, 1) : s.substring(0, n) + "…";
    }
}
