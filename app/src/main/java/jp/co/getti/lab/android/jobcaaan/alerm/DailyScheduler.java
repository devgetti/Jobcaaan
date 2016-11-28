package jp.co.getti.lab.android.jobcaaan.alerm;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

import jp.co.getti.lab.android.jobcaaan.service.JobcaaanService;

/**
 * デイリースケジューラ
 *
 * @param <T>
 */
class DailyScheduler<T> {

    /** ロガー */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int REQUEST_CODE_OFFSET = 1100;
    private Context mContext;
    private AlarmManager mAlarmManager;

    DailyScheduler(Context context) {
        mContext = context;
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
    }

    public void set(int alermId, Class<? extends T> clazz, int hour, int minute) {
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
        logger.debug("target:" + calTarget.getTime());

        // PendingIntent作成
        PendingIntent action = createPendingIntent(REQUEST_CODE_OFFSET + alermId, clazz);

        // タイマーセット
        mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calTarget.getTimeInMillis(), AlarmManager.INTERVAL_DAY, action);
    }

    public void cancel(int alermId, Class<? extends T> clazz) {
        // PendingIntent作成
        PendingIntent action = createPendingIntent(REQUEST_CODE_OFFSET + alermId, clazz);

        // タイマーキャンセル
        mAlarmManager.cancel(action);
    }

    private PendingIntent createPendingIntent(int requestCode, Class<? extends T> clazz) {
        Intent intent = new Intent(mContext, clazz);

        PendingIntent action = null;
        if (Activity.class.isAssignableFrom(clazz)) {
            action = PendingIntent.getActivity(mContext, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        } else if (Service.class.isAssignableFrom(clazz)) {
            action = PendingIntent.getService(mContext, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        } else if (BroadcastReceiver.class.isAssignableFrom(clazz)) {
            action = PendingIntent.getBroadcast(mContext, requestCode, intent, 0);//PendingIntent.FLAG_CANCEL_CURRENT);
        }
        return action;
    }
}
