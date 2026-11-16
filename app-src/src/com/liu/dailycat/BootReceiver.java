package com.liu.dailycat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机或应用更新后，把提醒重新登记到系统闹钟 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent it) {
        String a = it == null ? "" : it.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            AlarmSync.set(c, AlarmSync.load(c), false);
            /* 开机后组件的每日闹钟也丢了，一起补回来；顺手重画一次，免得停在关机前的周次 */
            try {
                WidgetProvider.refreshAll(c);
                WidgetProvider.scheduleDaily(c);
            } catch (Exception ignored) {
            }
        }
    }
}
