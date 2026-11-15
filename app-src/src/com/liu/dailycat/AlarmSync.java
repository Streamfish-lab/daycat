package com.liu.dailycat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** 把提醒登记到系统闹钟；App 不在后台也靠它，重启后由 BootReceiver 重新登记 */
public class AlarmSync {

    public static final String PREF = "alarms";
    public static final String KEY = "json";

    /** rc -> 触发时刻；真实登记了什么，就记什么，不再每次重置 */
    private static final Map<Integer, Long> pending = new HashMap<Integer, Long>();
    /** 最近一次用的定时方式：clock / exact / inexact */
    private static String lastMode = "";

    private static final int TEST_RC = 899001;

    public static boolean isTestRc(int rc) {
        return rc == TEST_RC;
    }

    /** 个数|最近一个时刻|定时方式 */
    public static String info() {
        long now = System.currentTimeMillis();
        int n = 0;
        long min = 0;
        Iterator<Map.Entry<Integer, Long>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> e = it.next();
            if (e.getValue() == null || e.getValue() <= now) { it.remove(); continue; }
            n++;
            if (min == 0 || e.getValue() < min) min = e.getValue();
        }
        return n + "|" + min + "|" + (lastMode == null ? "" : lastMode);
    }

    public static int rcOf(String id) {
        if (id != null && id.startsWith("testalarm")) return TEST_RC;
        int h = id.hashCode() & 0x7fffffff;
        return h % 900000 + 1000;
    }

    private static PendingIntent piOf(Context c, int rc, String title, String body, String key, long planned) {
        Intent it = new Intent(c, ReminderReceiver.class);
        it.putExtra("title", title);
        it.putExtra("body", body);
        it.putExtra("rc", rc);
        if (key != null) {
            it.putExtra("key", key);
            it.putExtra("planned", planned);
        }
        return PendingIntent.getBroadcast(c, rc, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 保存一份，供开机后重新登记 */
    public static void persist(Context c, String json) {
        try {
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, json).apply();
        } catch (Exception ignored) {
        }
    }

    public static String load(Context c) {
        try {
            return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]");
        } catch (Exception e) {
            return "[]";
        }
    }

    /** @param replace true 时取消已不存在于新列表里的旧闹钟（测试闹钟除外） */
    public static void set(Context c, String json, boolean replace) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Map<Integer, Long> next = new HashMap<Integer, Long>();
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            long now = System.currentTimeMillis();
            boolean useClock = false, useExact = false, useInexact = false;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "r" + i);
                long at = o.optLong("at", 0);
                if (at <= now + 3000L) continue;
                String title = o.optString("title", "该做事了");
                String body = o.optString("body", "");
                int rc = rcOf(id);
                PendingIntent pi = piOf(c, rc, title, body, id, at);

                boolean done = false;
                // 首选「闹钟式」闹钟：系统最高优先级，Doze/后台受限时也会把你叫醒
                if (Build.VERSION.SDK_INT >= 21) {
                    try {
                        Intent open = new Intent(c, MainActivity.class);
                        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        PendingIntent show = PendingIntent.getActivity(c, rc, open,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
                        done = true;
                        useClock = true;
                    } catch (Exception e1) {
                        done = false;
                    }
                }
                if (!done) {
                    try {
                        if (Build.VERSION.SDK_INT >= 31) {
                            if (am.canScheduleExactAlarms()) {
                                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                                useExact = true;
                            } else {
                                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                                useInexact = true;
                            }
                        } else if (Build.VERSION.SDK_INT >= 23) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                            useExact = true;
                        } else {
                            am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
                            useExact = true;
                        }
                    } catch (Exception se) {
                        try {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                            useInexact = true;
                        } catch (Exception e2) {
                            try {
                                am.set(AlarmManager.RTC_WAKEUP, at, pi);
                                useInexact = true;
                            } catch (Exception e3) {
                            }
                        }
                    }
                }
                // 第二个闹钟：普通精确闹钟，和「闹钟式」互为备份，谁先醒谁响
                try {
                    PendingIntent pi2 = piOf(c, rc + 500000, title, body, id, at);
                    if (Build.VERSION.SDK_INT >= 23) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi2);
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, at, pi2);
                    }
                } catch (Exception ignored2) {
                }
                next.put(rc, at);
            }

            if (replace) {
                Iterator<Map.Entry<Integer, Long>> it = pending.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, Long> e = it.next();
                    int old = e.getKey();
                    if (next.containsKey(old)) continue;
                    if (isTestRc(old)) continue;              // 测试闹钟别被正式同步顺手取消
                    try { am.cancel(piOf(c, old, "", "", null, 0)); } catch (Exception ignored) {
                    }
                    it.remove();
                }
            }
            pending.putAll(next);
            lastMode = useInexact ? "inexact" : (useExact ? "exact" : (useClock ? "clock" : ""));
        } catch (Exception ignored) {
        }
    }
}
