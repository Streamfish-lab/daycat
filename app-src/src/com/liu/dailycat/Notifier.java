package com.liu.dailycat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Build;

/** 统一发通知的地方（Activity 和闹钟广播都用它） */
public class Notifier {

    public static final String CH_ID = "reminder";
    /** 用户把主频道关掉后改用的备用频道，保证提醒不会被静默丢弃 */
    public static final String CH_ID2 = "reminder2";

    private static void make(Context c, String id, String name) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(id, name,
                NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 220, 120, 220});
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
        ch.enableLights(true);
        ch.setBypassDnd(false);
        nm.createNotificationChannel(ch);
    }

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm == null) return;
                if (nm.getNotificationChannel(CH_ID) == null) make(c, CH_ID, "任务提醒");
            } catch (Exception ignored) {
            }
        }
    }

    /** 真正要用的频道：主频道被关（importance 0）就换备用频道 */
    public static String activeChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return CH_ID;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return CH_ID;
            NotificationChannel a = nm.getNotificationChannel(CH_ID);
            if (a == null) { make(c, CH_ID, "任务提醒"); return CH_ID; }
            if (a.getImportance() != NotificationManager.IMPORTANCE_NONE) return CH_ID;
            NotificationChannel b = nm.getNotificationChannel(CH_ID2);
            if (b == null) make(c, CH_ID2, "任务提醒（备用）");
            return CH_ID2;
        } catch (Exception e) {
            return CH_ID;
        }
    }

    /** 记一笔通知的结果，方便在设置页里看到底发生了什么 */
    private static void log(Context c, String v) {
        try {
            c.getSharedPreferences("nlog", Context.MODE_PRIVATE).edit()
                    .putString("last", System.currentTimeMillis() + "|" + v).apply();
        } catch (Exception ignored) {
        }
    }

    public static String lastLog(Context c) {
        try {
            return c.getSharedPreferences("nlog", Context.MODE_PRIVATE).getString("last", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean show(Context c, String title, String body, int id) {
        ensureChannel(c);
        try {
            String chId = activeChannel(c);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = new Notification.Builder(c, chId);
            } else {
                b = new Notification.Builder(c);
            }
            b.setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(title == null ? "该做事了" : title)
                    .setContentText(body == null ? "" : body)
                    .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
                    .setAutoCancel(true)
                    .setVibrate(new long[]{0, 220, 120, 220})
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true);
            if (Build.VERSION.SDK_INT < 26) {
                b.setDefaults(Notification.DEFAULT_ALL);
                b.setPriority(Notification.PRIORITY_HIGH);
            }
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentIntent(pi);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) { log(c, "fail|noNM"); return false; }
            nm.notify(id, b.build());
            int imp = -1;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = nm.getNotificationChannel(chId);
                if (ch != null) imp = ch.getImportance();
            }
            log(c, "ok|" + chId + "|" + imp + "|" + id);
            return true;
        } catch (Exception e) {
            log(c, "fail|" + e.getClass().getSimpleName());
            return false;
        }
    }
}
