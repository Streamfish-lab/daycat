package com.liu.dailycat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 系统闹钟到点后在这里发通知：不需要 App 在后台运行 */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent it) {
        if (it == null) return;
        String title = it.getStringExtra("title");
        String body = it.getStringExtra("body");
        int id = it.getIntExtra("rc", 1001);
        String key = it.getStringExtra("key");
        long planned = it.getLongExtra("planned", 0);
        long now = System.currentTimeMillis();

        // 两个闹钟互为备份，同一个提醒只弹一次
        if (key != null) {
            try {
                android.content.SharedPreferences sp =
                        ctx.getSharedPreferences("fired", Context.MODE_PRIVATE);
                long last = sp.getLong(key, 0);
                if (last > 0 && now - last < 300000L) return;
                sp.edit().putLong(key, now)
                        .putString("lastDiag", planned + "|" + now)
                        .apply();
            } catch (Exception ignored) {
            }
        }
        Notifier.show(ctx, title, body, id);
    }
}
