package jp.co.getti.lab.android.jobcaaan.db;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 認証情報DBマネージャ
 */
@SuppressWarnings("unused,WeakerAccess")
public class DataDBManager extends GeneralDBContentsProvider.AbstDBManager {

    /** コンテキスト */
    protected Context mContext;

    /** 認証情報DB */
    private DataDB mAuthDB;

    /** USB接続状況BroadcastReceiver */
    private final BroadcastReceiver mUsbStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshExStorageDir(context, (new File(mAuthDB.getDatabaseName()).getParent()));
        }
    };

    /**
     * コンテンツURIを取得する。
     *
     * @param context コンテキスト
     * @return コンテンツURI
     */
    public static Uri getContentsUri(Context context, String tableName) {
        return Uri.parse("content://" + context.getPackageName() + ".data" + "/" + tableName);
    }

    @Override
    public void open(Context context, String dbName) {
        mContext = context;
        mAuthDB = new DataDB(mContext, dbName);
        mContext.registerReceiver(mUsbStateReceiver, new IntentFilter("android.hardware.usb.action.USB_STATE"));
    }

    @Override
    public String getDatabaseName() {
        return mAuthDB.getDatabaseName();
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        return mAuthDB.getReadableDatabase();
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        return mAuthDB.getWritableDatabase();
    }

    @Override
    public void close() {
        mAuthDB.close();
        mContext.unregisterReceiver(mUsbStateReceiver);
    }

    @Override
    public String[] getTableNames() {
        return DataDB.TABLE_NAMES;
    }

    /**
     * 外部ストレージディレクトリ更新
     *
     * @param context コンテキスト
     * @param dirPath ディレクトリパス
     */
    private void refreshExStorageDir(Context context, String dirPath) {
        // MTP接続時のFileExplorer表示を最新化されるようにIntent発行
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                File dummy = new File(dirPath, "tmp_" + UUID.randomUUID());
                if (!dummy.createNewFile()) {
                    throw new IOException("Failed create dummy file." + dummy.getPath());
                }
                context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + dummy.getPath())));
                if (!dummy.delete()) {
                    throw new IOException("Failed delete dummy file." + dummy.getPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + dirPath)));
        }
    }
}
