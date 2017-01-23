package jp.co.getti.lab.android.jobcaaan.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;

import jp.co.getti.lab.android.jobcaaan.utils.holiday.JapaneseHolidayUtils;

/**
 * 日時アラームManager
 * <pre>
 * 　　以下の問題に対応可能にすべく、精度が高い＆繰り返しを対応可能にするUtility。
 *
 *     AndroidのAlermManagerはSDKバージョンが上がるたびに登録方法が増え、
 *     精度や繰り返し動作などの動作が変化している。
 *
 *     困るのが日時繰り返しのタイマーが必要な場合、以前まで利用できていた
 *     setRepeatingの精度が大幅に悪くなった。
 * 　　参考:https://akira-watson.com/android/setexact-setwindow.html
 * 　　(実測したところ2、3分遅れる場合があった。こんなのはなくなってもよい。)
 *
 * 　　では精度の高い、setExactやsetExactAndAllowWhileIdleを使うとしてもこれの繰り返し版はない。
 *
 * 　　・アラーム登録時
 * 　　　Preferenceにアラーム情報を保存([次回アラーム時刻,呼び出すBroadcastReceiverクラスFQDN])。
 * 　　　内部Receiverを次回アラーム時刻に動作させるようアラーム登録
 *
 * 　　・アラーム実行時
 * 　　　上記で登録したアラームにより、内部Receiverが起動。
 * 　　　内部Receiver内でPreferenceからアラーム情報を読み込み、BroadcastReceiverへ送信。
 * 　　　次回アラーム時刻でPrefereceのアラーム情報を更新。
 * </pre>
 */
public class DailyAlarmManager {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(DailyAlarmManager.class);

    /** Preferenceキー 最大アラームID */
    private static final String PREF_MAX_ALARM_ID = DailyAlarmManager.class.getName() + ".maxAlarmId";

    /** Preferenceキー アラーム情報Prefix */
    private static final String PREF_ALARM_INFO_PREFIX = DailyAlarmManager.class.getName() + ".alarm_";

    /** EXTRA アラームID */
    private static final String EXTRA_ALARM_ID = "alarmId";

    /** リクエストコードオフセット */
    private static final int REQUEST_CODE_OFFSET = 1100;

    /** コンテキスト */
    private Context mContext;

    /** AlarmManager */
    private AlarmManager mAlarmManager;

    /** プリファレンス */
    private SharedPreferences mPreferences;

    /**
     * コンストラクタ
     *
     * @param context コンテキスト
     */
    public DailyAlarmManager(Context context) {
        mContext = context;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public static void dumpPref(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int maxAlarmId = preferences.getInt(PREF_MAX_ALARM_ID, -1);
        logger.debug(PREF_MAX_ALARM_ID + ":" + maxAlarmId);
        for (int i = 0; i <= maxAlarmId; i++) {
            String key = PREF_ALARM_INFO_PREFIX + i;
            String val = preferences.getString(key, null);
            logger.debug(key + ":" + val);
        }
    }

    /**
     * アラーム情報保存
     *
     * @param context   コンテキスト
     * @param alarmId   アラームID
     * @param alarmInfo アラーム情報
     */
    private static void saveAlarmInfo(Context context, int alarmId, AlarmInfo alarmInfo) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = PREF_ALARM_INFO_PREFIX + alarmId;
        if (alarmInfo == null) {
            preferences.edit().remove(key).apply();
        } else {
            preferences.edit().putString(key, alarmInfo.toString()).apply();
        }
    }

    /**
     * アラーム情報読み込み
     *
     * @param context コンテキスト
     * @param alarmId アラームID
     * @return アラーム情報
     */
    private static AlarmInfo loadAlarmInfo(Context context, int alarmId) {
        AlarmInfo ret = null;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = PREF_ALARM_INFO_PREFIX + alarmId;
        String alarmStr = preferences.getString(key, null);
        if (alarmStr != null) {
            ret = new AlarmInfo();
            try {
                String[] tmp = alarmStr.split(",");
                ret.time = Long.parseLong(tmp[0]);

                @SuppressWarnings("unchecked")
                Class<? extends BroadcastReceiver> clazz = (Class<? extends BroadcastReceiver>) Class.forName(tmp[1]);
                ret.clazz = clazz;
            } catch (NumberFormatException | ClassNotFoundException | IndexOutOfBoundsException e) {
                // 上手く取れなければ無視
                e.printStackTrace();
                ret = null;
            }
        }
        return ret;
    }

    /**
     * アラームPendingIntent作成
     *
     * @param context コンテキスト
     * @param alarmId アラームID
     * @return アラームPendingIntent
     */
    private static PendingIntent createAlarmPendingIntent(Context context, int alarmId) {
        Intent intent = new Intent(context, Receiver.class);
        intent.putExtra(EXTRA_ALARM_ID, alarmId);
        return PendingIntent.getBroadcast(context, REQUEST_CODE_OFFSET + alarmId, intent, 0);
    }

    /**
     * アラーム設定
     *
     * @param mAlarmManager アラームManager
     * @param time          時刻(ミリ秒)
     * @param pending       PendingIntent
     */
    private static void setAlarm(AlarmManager mAlarmManager, long time, PendingIntent pending) {
        // タイマーセット
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // android6.0以上
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // android４．４以上
            mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, time, pending);
        } else {
            mAlarmManager.set(AlarmManager.RTC_WAKEUP, time, pending);
        }
    }

    /**
     * アラーム設定
     *
     * @param clazz  呼び出し先BroadcastReceiver
     * @param hour   時
     * @param minute 分
     * @return アラームID
     */
    public int set(Class<? extends BroadcastReceiver> clazz, int hour, int minute) {
        // 対象日時
        Calendar calTarget = Calendar.getInstance();
        calTarget.clear(Calendar.SECOND);
        calTarget.clear(Calendar.MILLISECOND);
        calTarget.set(Calendar.HOUR_OF_DAY, hour);
        calTarget.set(Calendar.MINUTE, minute);

        // 現在日時
        Calendar calNow = Calendar.getInstance();

        // 現在日時より過ぎていた場合は、次の日からセット
        if (calTarget.getTimeInMillis() < calNow.getTimeInMillis()) {
            calTarget.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 土日祝を除いてさらにインクリメント
        while (calTarget.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                || calTarget.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                || JapaneseHolidayUtils.isHoliday(calTarget)) {
            calTarget.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 最大IDから新しいアラームID作成(0スタート)
        int alarmId = mPreferences.getInt(PREF_MAX_ALARM_ID, -1) + 1;
        long time = calTarget.getTimeInMillis();

        // アラーム登録
        setAlarm(mAlarmManager, time, createAlarmPendingIntent(mContext, alarmId));

        // プリファレンスに保存
        mPreferences.edit().putInt(PREF_MAX_ALARM_ID, alarmId).apply();
        saveAlarmInfo(mContext, alarmId, new AlarmInfo(time, clazz));

        logger.debug("Set alarm. time:" + new Date(time) + " target:" + clazz.getName());

        return alarmId;
    }

    /**
     * アラームキャンセル
     *
     * @param alarmId アラームID
     */
    public void cancel(int alarmId) {
        AlarmInfo alarmInfo = loadAlarmInfo(mContext, alarmId);
        if (alarmInfo != null) {
            // アラーム解除
            mAlarmManager.cancel(createAlarmPendingIntent(mContext, alarmId));

            // アラーム情報削除
            saveAlarmInfo(mContext, alarmId, null);
        }
    }

    /**
     * アラームクリア
     */
    public void clear() {
        // 最大IDを取得
        int maxAlarmId = mPreferences.getInt(PREF_MAX_ALARM_ID, -1);
        for (int i = 0; i <= maxAlarmId; i++) {
            cancel(i);
        }
        mPreferences.edit().remove(PREF_MAX_ALARM_ID).apply();
    }

    /**
     * アラーム情報
     */
    private static class AlarmInfo {
        /** 時刻(UTCミリ秒) */
        long time;

        /** 呼び出し先BroadcastReceiverクラス */
        Class<? extends BroadcastReceiver> clazz;

        /**
         * コンストラクタ
         */
        AlarmInfo() {
        }

        /**
         * コンストラクタ
         *
         * @param time  時刻(UTCミリ秒)
         * @param clazz 呼び出し先BroadcastReceiverクラス
         */
        AlarmInfo(long time, Class<? extends BroadcastReceiver> clazz) {
            this.time = time;
            this.clazz = clazz;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return time + "," + clazz.getName();
        }
    }

    /**
     * アラームレシーバ
     */
    public static class Receiver extends BroadcastReceiver {

        /** {@inheritDoc} */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logger.debug("onReceive action:" + action + " myPackage:" + context.getPackageName());

            int alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1);
            if (alarmId != -1) {
                // アラームIDから対象のアラーム情報を取得
                AlarmInfo alarmInfo = loadAlarmInfo(context, alarmId);
                if (alarmInfo != null) {
                    // アラームに従いBroadcast
                    context.sendBroadcast(new Intent(context, alarmInfo.clazz));

                    // 次回起動の時刻を算出
                    Calendar calTarget = Calendar.getInstance();
                    calTarget.setTime(new Date(alarmInfo.time));

                    // 本日分はこれで終わり
                    calTarget.add(Calendar.DAY_OF_MONTH, 1);

                    // 次回営業日までインクリメント
                    while (calTarget.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                            || calTarget.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                            || JapaneseHolidayUtils.isHoliday(calTarget)) {
                        calTarget.add(Calendar.DAY_OF_MONTH, 1);
                    }

                    alarmInfo.time = calTarget.getTimeInMillis();

                    // アラーム登録
                    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                    setAlarm(alarmManager, alarmInfo.time, createAlarmPendingIntent(context, alarmId));

                    // プリファレンスを更新
                    saveAlarmInfo(context, alarmId, alarmInfo);

                    logger.debug("Set alarm(reset). time:" + new Date(alarmInfo.time) + " target:" + alarmInfo.clazz.getName());
                }
            }
        }
    }
}
