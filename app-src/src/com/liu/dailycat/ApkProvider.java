package com.liu.dailycat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 给系统安装器读我们下载好的 APK。
 * Android 7 起不再允许把 file:// 交给别的应用，会直接抛 FileUriExposedException，
 * 所以把安装包放在应用私有目录，用 content:// 借给安装器读。
 */
public class ApkProvider extends ContentProvider {

    public static final String AUTHORITY = "com.liu.dailycat.apk";
    public static final String FILE_NAME = "Schedule.apk";

    public static File dirOf(Context c) {
        File d = new File(c.getFilesDir(), "upd");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static Uri uriOf(String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + name);
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
        File f = new File(dirOf(getContext()), name);
        if (!f.exists()) throw new FileNotFoundException(f.getAbsolutePath());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File f = new File(dirOf(getContext()), String.valueOf(uri.getLastPathSegment()));
            String[] cols = projection != null ? projection
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor cur = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = FILE_NAME;
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
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] args) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
