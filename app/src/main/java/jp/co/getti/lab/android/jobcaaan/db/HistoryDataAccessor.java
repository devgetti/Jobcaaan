package jp.co.getti.lab.android.jobcaaan.db;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jp.co.getti.lab.android.jobcaaan.model.History;

/**
 * 履歴データアクセサ
 */
@SuppressWarnings("unused,WeakerAccess")
public class HistoryDataAccessor {

    private Context mContext;

    public HistoryDataAccessor(Context context) {
        mContext = context;
    }

    public List<History> select(Date from, Date to, String type, Integer limit, boolean orderByDateTimeDesc) {
        // Uri
        Uri uri = DataDBManager.getContentsUri(mContext, DataDB.HISTORY_TABLE_NAME);

        if (limit != null) {
            // LIMIT句(LIMIT句はContentsProvider自体のメソッド引数で指定できないため、URIパラメータで渡す)
            uri = uri.buildUpon().appendQueryParameter("limit", limit.toString()).build();
        }

        // 汎用Curor取得
        Cursor cursor = getGeneralSelectCursor(uri, null, from, to, type, true);

        // 実行
        List<History> ret = new ArrayList<>();
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    History history = new History();
                    history.setId(cursor.getInt(cursor.getColumnIndex(DataDB.HISTORY_COLUMN_ID)));
                    try {
                        history.setDateTime(DataDB.FORMAT_DATE_TIME.parse(cursor.getString(cursor.getColumnIndex(DataDB.HISTORY_COLUMN_DATE_TIME))));
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                    history.setType(cursor.getString(cursor.getColumnIndex(DataDB.HISTORY_COLUMN_TYPE)));
                    history.setTitle(cursor.getString(cursor.getColumnIndex(DataDB.HISTORY_COLUMN_TITLE)));
                    ret.add(history);
                }
            } finally {
                cursor.close();
            }
        }
        return ret;
    }

    public long insert(Date dateTime, String type, String title) {
        // Uri
        Uri uri = DataDBManager.getContentsUri(mContext, DataDB.HISTORY_TABLE_NAME);

        // Values
        ContentValues values;
        {
            values = new ContentValues();
            if (dateTime != null) {
                values.put(DataDB.HISTORY_COLUMN_DATE_TIME, DataDB.FORMAT_DATE_TIME.format(dateTime));
            }
            values.put(DataDB.HISTORY_COLUMN_TYPE, type);
            values.put(DataDB.HISTORY_COLUMN_TITLE, title);
            values.put(DataDB.HISTORY_COLUMN_UPDATE_DATE_TIME, DataDB.FORMAT_DATE_TIME.format(new Date()));
        }

        // 実行
        Uri resultUri = mContext.getContentResolver().insert(uri, values);
        return (resultUri != null) ? ContentUris.parseId(resultUri) : -1;
    }

    public int selectCount(Date from, Date to, String type) {
        // Uri
        Uri uri = DataDBManager.getContentsUri(mContext, DataDB.HISTORY_TABLE_NAME);

        // Projection
        String[] projection = new String[]{"COUNT(*) AS count"};

        // 汎用Curor取得
        Cursor cursor = getGeneralSelectCursor(uri, projection, from, to, type, null);

        // 実行
        int count = 0;
        if (cursor != null) {
            try {
                if (cursor.moveToNext()) {
                    count = cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }
        return count;
    }

    public int delete(Date from, Date to, String type) {
        // Uri
        Uri uri = DataDBManager.getContentsUri(mContext, DataDB.HISTORY_TABLE_NAME);

        int ret;
        String selection;
        String[] selectionArgs;
        {
            StringBuilder sbParams = new StringBuilder();
            ArrayList<String> paramList = new ArrayList<>();
            if (from != null || to != null || type != null) {
                if (from != null) {
                    sbParams.append(DataDB.HISTORY_COLUMN_DATE_TIME).append(" >= ? AND ");
                    paramList.add(DataDB.FORMAT_DATE_TIME.format(from));
                }

                if (to != null) {
                    sbParams.append(DataDB.HISTORY_COLUMN_DATE_TIME).append(" <= ? AND ");
                    paramList.add(DataDB.FORMAT_DATE_TIME.format(to));
                }

                if (type != null) {
                    sbParams.append(DataDB.HISTORY_COLUMN_TYPE).append(" = ? AND ");
                    paramList.add(type);
                }
                sbParams.setLength(sbParams.length() - " AND ".length());
            }
            selection = sbParams.toString();
            selectionArgs = paramList.toArray(new String[paramList.size()]);
        }

        ret = mContext.getContentResolver().delete(uri, selection, selectionArgs);
        return ret;
    }

    /**
     * 汎用SELECTカーソル取得
     *
     * @param uri        URI
     * @param projection Projection
     * @return Cursor
     */
    private Cursor getGeneralSelectCursor(Uri uri, String[] projection, Date from, Date to, String type, Boolean orderByDateTimeDesc) {
        // Selection & Args
        String selection;
        String[] selectionArgs;
        {
            StringBuilder sbParams = new StringBuilder();
            ArrayList<String> paramList = new ArrayList<>();
            if (from != null || to != null || type != null) {
                if (from != null) {
                    sbParams.append(DataDB.HISTORY_COLUMN_DATE_TIME).append(" >= ? AND ");
                    paramList.add(DataDB.FORMAT_DATE_TIME.format(from));
                }

                if (to != null) {
                    sbParams.append(DataDB.HISTORY_COLUMN_DATE_TIME).append(" <= ? AND ");
                    paramList.add(DataDB.FORMAT_DATE_TIME.format(to));
                }

                if (type != null) {
                    sbParams.append(DataDB.HISTORY_COLUMN_TYPE).append(" = ? AND ");
                    paramList.add(type);
                }
                sbParams.setLength(sbParams.length() - " AND ".length());
            }
            selection = sbParams.toString();
            selectionArgs = paramList.toArray(new String[paramList.size()]);
        }

        // SortOrder
        String sortOrder;
        {
            StringBuilder sbOrder = new StringBuilder();
            if (orderByDateTimeDesc != null) {
                sbOrder.append(DataDB.HISTORY_COLUMN_DATE_TIME).append(" ").append(orderByDateTimeDesc ? "DESC" : "ASC").append(", ");
                sbOrder.setLength(sbOrder.length() - " ".length());
            }
            sbOrder.append(DataDB.HISTORY_COLUMN_ID + " DESC");
            sortOrder = sbOrder.toString();
        }

        // 実行
        return mContext.getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
    }
}
