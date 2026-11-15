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

    public static Uri uriOf(int widgetId) {
        return Uri.parse("content://" + AUTHORITY + "/" + widgetId);
    }

    public static File fileOf(android.content.Context c, int widgetId) {
        File dir = new File(c.getFilesDir(), "widget");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "w" + widgetId + ".webp");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            int id = Integer.parseInt(uri.getLastPathSegment());
            File f = fileOf(getContext(), id);
            if (!f.exists()) throw new FileNotFoundException(f.getAbsolutePath());
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (NumberFormatException e) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            int id = Integer.parseInt(uri.getLastPathSegment());
            File f = fileOf(getContext(), id);
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
