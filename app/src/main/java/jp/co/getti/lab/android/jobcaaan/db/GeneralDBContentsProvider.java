package jp.co.getti.lab.android.jobcaaan.db;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE;

/**
 * 汎用DBコンテンツプロバイダ
 * <pre>
 *     汎用用途で利用可能なAndroidContentsProviderクラス。
 *     ・単純なCRUDを提供する。
 *     ・ContentsURIはcontents://[authority]/[テーブル名]
 *
 *     使用時は以下の定義を
 * </pre>
 */
@SuppressWarnings("unused,WeakerAccess")
public class GeneralDBContentsProvider extends ContentProvider {

    /**
     * メタデータキー　DBHelperManagerクラス(必須)
     * <pre>
     *     DBアクセスのための処理を実装したAbstDBManagerの派生クラスのFQDN。
     * </pre>
     */
    public static final String META_DB_HELPER_MANAGER_CLASS = "db_mng_class";

    /**
     * メタデータキー　InメモリDBフラグ
     * <pre>
     *     InメモリDBとして展開する場合trueにする。
     *     trueの場合、ファイル保存に関わる設定は無視される。(in_ext_dirやdb_file_path等)
     * </pre>
     */
    public static final String META_IN_MEMORY = "in_memory";

    /**
     * メタデータキー　外部記憶領域保存フラグ
     * <pre>
     *     DBファイルの保存先を外部記憶領域(Environment#getExternalStorageDirectory())にする場合trueにする。
     *     値がtrueの場合: Environment#getExternalStorageDirectory()
     *     値がfalseの場合: Context#getFilesDir()
     * </pre>
     */
    public static final String META_IN_EXT_DIR = "in_ext_dir";

    /**
     * メタデータキー　DBファイルパス
     * <pre>
     *     保存先のDBファイルパス。
     *     in_ext_dirの値により、アプリデータ領域あるいは、外部記憶領域どちらからかのパス。
     * </pre>
     */
    public static final String META_DB_FILE_PATH = "db_file_path";


    /** デフォルト値　DBファイルパス */
    public static final String DEF_DB_FILE_PATH = "data.db";

    /** テーブルコードSet */
    public Set<Integer> sTableCodeSet;

    /** URI評価用のMatcher */
    private UriMatcher sUriMatcher;

    /** DBManager */
    private AbstDBManager mDBHelperManager;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) {
            // AndroidManifestの当該ContentsProvider項目meta-dataから各種設定値を取得

            // DBManagerクラス(必須)
            String dbManagerClass = getMetaData(context, this.getClass(), META_DB_HELPER_MANAGER_CLASS, null);
            if (dbManagerClass == null) {
                throw new RuntimeException("Failed select <" + META_DB_HELPER_MANAGER_CLASS + "> value.");
            }

            // InMemoryDBかどうか
            boolean isInMemory = getMetaData(context, this.getClass(), META_IN_MEMORY, false);

            // 外部記憶領域保存かどうか
            boolean isInExtDir = getMetaData(context, this.getClass(), META_IN_EXT_DIR, false);

            // 保存先ファイルパス
            String filePath = getMetaData(context, this.getClass(), META_DB_FILE_PATH, DEF_DB_FILE_PATH);
            if (isInExtDir) {
                filePath = (new File(Environment.getExternalStorageDirectory(), filePath)).getPath();
            } else {
                filePath = (new File(context.getFilesDir(), filePath)).getPath();
            }

            // DBManagerのロード
            try {
                Class<?> clazzDBManager = Class.forName(dbManagerClass);
                mDBHelperManager = (AbstDBManager) clazzDBManager.newInstance();
            } catch (ClassNotFoundException | IllegalArgumentException | IllegalAccessException | InstantiationException e) {
                throw new RuntimeException("Failed create instance " + dbManagerClass + ".");
            }

            // URIMatcher作成
            sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
            sTableCodeSet = new HashSet<>();
            String[] tableNames = mDBHelperManager.getTableNames();
            for (int i = 0; i < tableNames.length; i++) {
                sUriMatcher.addURI(getAuthority(context, this.getClass()), tableNames[i], i);
                sTableCodeSet.add(i);
            }

            // DB Open
            mDBHelperManager.open(context, (isInMemory) ? null : filePath);

        } else {
            throw new RuntimeException("Failed select context.");
        }
        return true;
    }

    @Override
    public void shutdown() {
        mDBHelperManager.close();
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Uri評価
        validUri(uri);

        createParentDir();

        // DB取得
        SQLiteDatabase db = mDBHelperManager.getReadableDatabase();

        // 対象テーブル取得
        String table = uri.getPathSegments().get(0);

        // LIMIT取得
        String limit = uri.getQueryParameter("limit");

        // DISTINCT取得
        boolean distinct = Boolean.parseBoolean(uri.getQueryParameter("distinct"));

        // SELECT
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(table);
        qb.setDistinct(distinct);
        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit);

        // 通知設定
        if (getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        // Uri評価
        validUri(uri);

        createParentDir();

        // DB取得
        SQLiteDatabase db = mDBHelperManager.getWritableDatabase();

        // 対象テーブル取得
        String table = uri.getPathSegments().get(0);

        // INSERT
        long rowId = db.insertWithOnConflict(table, null, values, CONFLICT_REPLACE);

        // Return URI作成
        Uri returnUri = ContentUris.withAppendedId(uri, rowId);

        // 通知
        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(returnUri, null);
        }
        return returnUri;
    }

    @Override
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        validUri(uri);

        createParentDir();

        SQLiteDatabase db = mDBHelperManager.getWritableDatabase();
        db.beginTransaction();
        try {
            for (ContentValues value : values) {
                insert(uri, value);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return values.length;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Uri評価
        validUri(uri);

        createParentDir();

        // DB取得
        SQLiteDatabase db = mDBHelperManager.getWritableDatabase();

        // 対象テーブル取得
        String table = uri.getPathSegments().get(0);

        // UPDATE
        int count = db.updateWithOnConflict(table, values, selection, selectionArgs, CONFLICT_REPLACE);

        // 通知
        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        // Uri評価
        validUri(uri);

        createParentDir();

        // DB取得
        SQLiteDatabase db = mDBHelperManager.getWritableDatabase();

        // 対象テーブル取得
        String table = uri.getPathSegments().get(0);

        // DELETE
        int count = db.delete(table, selection, selectionArgs);

        // 通知
        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * URIバリデーション
     *
     * @param uri URI
     */
    protected void validUri(Uri uri) {
        Context context = getContext();
        if (context != null) {
            int code = sUriMatcher.match(uri);
            if (sTableCodeSet.contains(code)) {
                return;
            }
        }
        throw new IllegalArgumentException("unknown uri : " + uri);
    }

    /**
     * AndroidManifestから指定ContentProviderのauthority名を取得する。
     *
     * @param context コンテキスト
     * @param clazz   ContentProviderクラス
     * @return authority名
     */
    public String getAuthority(Context context, Class<? extends ContentProvider> clazz) {
        String result = null;
        if (context != null) {
            try {
                ProviderInfo providerInfo = context.getPackageManager().getProviderInfo(new ComponentName(context, clazz), PackageManager.GET_META_DATA);
                result = providerInfo.authority;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    /**
     * AndriodManifestから指定ContentProviderのメタデータ値を取得する。
     *
     * @param context      コンテキスト
     * @param clazz        ContentProviderクラス
     * @param key          Metaキー
     * @param defaultValue デフォルト値
     * @param <T>          取得値型
     * @return メタデータ値
     */
    @SuppressWarnings("unchecked")
    protected <T> T getMetaData(Context context, Class<? extends ContentProvider> clazz, String key, T defaultValue) {
        T result = null;
        if (context != null) {
            try {
                ProviderInfo providerInfo = context.getPackageManager().getProviderInfo(new ComponentName(context, clazz), PackageManager.GET_META_DATA);
                result = (T) providerInfo.metaData.get(key);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return (result != null) ? result : defaultValue;
    }

    /**
     * DBファイルの親ディレクトリを作成
     */
    private void createParentDir() {
        if (mDBHelperManager.getDatabaseName() != null) {
            File parentDir = new File(mDBHelperManager.getDatabaseName()).getParentFile();
            if (!parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Log.w("db", "Faild create dir. " + mDBHelperManager.getDatabaseName());
                } else {
                    Log.i("db", "Success create dir. " + mDBHelperManager.getDatabaseName());
                }
            }
        }
    }

    /**
     * DBManager
     * <pre>
     *     本汎用クラスからDBにアクセスするために使用するDBとの受渡しになるクラス。
     *     SQLiteHelperそのものでもよい。
     * </pre>
     */
    public static abstract class AbstDBManager {

        public AbstDBManager() {
        }

        public abstract void open(Context context, String dbName);

        public abstract String getDatabaseName();

        public abstract SQLiteDatabase getReadableDatabase();

        public abstract SQLiteDatabase getWritableDatabase();

        public abstract String[] getTableNames();

        public abstract void close();
    }
}
