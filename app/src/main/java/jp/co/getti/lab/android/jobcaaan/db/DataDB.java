package jp.co.getti.lab.android.jobcaaan.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * 認証情報DB
 */
@SuppressWarnings("unused,WeakerAccess")
public class DataDB extends SQLiteOpenHelper {

    /** テーブル名 */
    public static final String HISTORY_TABLE_NAME = "history";

    /** 列 ID */
    public static final String HISTORY_COLUMN_ID = "_id";

    /** 列 名前空間 */
    public static final String HISTORY_COLUMN_DATE_TIME = "date_time";

    /** 列 ユーザ */
    public static final String HISTORY_COLUMN_TYPE = "type";

    /** 列 デバイス */
    public static final String HISTORY_COLUMN_TITLE = "title";

    /** 列 時刻 */
    public static final String HISTORY_COLUMN_UPDATE_DATE_TIME = "upd_time";

    /** テーブル名群 */
    public static final String[] TABLE_NAMES = {
            HISTORY_TABLE_NAME
    };

    /** 日付フォーマット */
    public static final SimpleDateFormat FORMAT_DATE_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.JAPAN);

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(DataDB.class);

    /** DBバージョン */
    private static final int DATABASE_VERSION = 1;

    /** QUERY CREATE TABLE */
    private static final String HISTORY_QUERY_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " + HISTORY_TABLE_NAME + " ("
            + HISTORY_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + HISTORY_COLUMN_DATE_TIME + " TEXT,"
            + HISTORY_COLUMN_TYPE + " TEXT,"
            + HISTORY_COLUMN_TITLE + " TEXT,"
            + HISTORY_COLUMN_UPDATE_DATE_TIME + " TEXT" + ")";

    /** QUERY CREATE INDEX KEY */
    private static final String HISTORY_QUERY_CREATE_INDEX_TYPE = "CREATE INDEX IF NOT EXISTS " + HISTORY_COLUMN_TYPE + " ON " + HISTORY_TABLE_NAME + "(" + HISTORY_COLUMN_TYPE + ")";

    /** QUERY DROP TABLE */
    private static final String HISTORY_QUERY_DROP_TABLE = "DROP TABLE IF EXISTS " + HISTORY_TABLE_NAME;

    /**
     * コンストラクタ
     *
     * @param context コンテキスト
     * @param dbName  DBパス
     */
    public DataDB(Context context, String dbName) {
        super(context, dbName, null, DATABASE_VERSION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL(HISTORY_QUERY_CREATE_TABLE);
            db.execSQL(HISTORY_QUERY_CREATE_INDEX_TYPE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            db.execSQL(HISTORY_QUERY_DROP_TABLE);
            db.execSQL(HISTORY_QUERY_CREATE_TABLE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
