package com.liu.dailycat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 给桌面组件提供图片。
 * 直接传 Bitmap 会被 Binder 单次事务上限卡住，图一大就白屏；
 * 改成把图写进文件，Launcher 通过 content:// 来读，就没有大小限制。
 */
public class WidgetImageProvider extends ContentProvider {

    public static final String AUTHORITY = "com.liu.dailycat.widgetimg";

    public static File dirOf(android.content.Context c) {
        File dir = new File(c.getFilesDir(), "widget");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 每次重画都换一个新文件名。
     *  原因：桌面（Launcher）对同一张 content:// 图片会缓存，文件名不变、URI 不变，
     *  它就继续显示旧图——表现就是「过了零点、也打开了 App，小组件还是昨天的」。
     *  换名字等于换 URI，桌面只能重新读。 */
    public static String freshName(int widgetId) {
        return "w" + widgetId + "_" + System.currentTimeMillis() + ".webp";
    }

    public static Uri uriOf(String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + name);
    }

    public static File fileOf(android.content.Context c, String name) {
        return new File(dirOf(c), name);
    }

    /** 清理旧图：同一个组件的文件只保留最新两个，其余删掉 */
    public static void cleanOld(android.content.Context c, int widgetId, String keep) {
        try {
            File dir = dirOf(c);
            File[] all = dir.listFiles();
            if (all == null || all.length < 3) return;
            String pre = "w" + widgetId + "_";
            java.util.ArrayList<File> mine = new java.util.ArrayList<File>();
            for (File f : all) {
                String n = f.getName();
                if (n.startsWith(pre) && n.endsWith(".webp") && !n.equals(keep)) mine.add(f);
            }
            if (mine.size() < 2) return;
            java.util.Collections.sort(mine, new java.util.Comparator<File>() {
                public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
            });
            for (int i = 1; i < mine.size(); i++) {   // 留最近的 1 个旧图，其余删
                try { mine.get(i).delete(); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        File f = fileOf(getContext(), name);
        if (!f.exists()) throw new FileNotFoundException(f.getAbsolutePath());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            String name = uri.getLastPathSegment();
            File f = fileOf(getContext(), name);
            String[] cols = projection != null ? projection
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor cur = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
                else row[i] = null;
            }
            cur.addRow(row);
            return cur;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        return "image/webp";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] args) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
