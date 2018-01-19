package jp.co.getti.lab.android.jobcaaan.service;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jp.co.getti.lab.android.jobcaaan.BuildConfig;
import jp.co.getti.lab.android.jobcaaan.R;
import jp.co.getti.lab.android.jobcaaan.db.HistoryDataAccessor;
import jp.co.getti.lab.android.jobcaaan.location.ILocationListenerStrategy;
import jp.co.getti.lab.android.jobcaaan.location.LocationListener;
import jp.co.getti.lab.android.jobcaaan.location.LocationStatus;
import jp.co.getti.lab.android.jobcaaan.notification.JobcaaanNotification;
import jp.co.getti.lab.android.jobcaaan.receiver.AlarmLogicReceiver;
import jp.co.getti.lab.android.jobcaaan.utils.CybozuWebClient;
import jp.co.getti.lab.android.jobcaaan.utils.DailyAlarmManager;
import jp.co.getti.lab.android.jobcaaan.utils.JobcanWebClient;
import jp.co.getti.lab.android.jobcaaan.utils.LocationUtils;

/**
 * Jobcaaanメインサービス
 * <pre>
 *     Jobcanへのアクセスや設定保存などを行うメインサービス。
 * </pre>
 */
@SuppressWarnings("unused")
public class JobcaaanService extends Service {

    /** プリファレンスキー ユーザコード */
    public static final String PREF_USER_CODE = "UserCode";

    /** プリファレンスキー グループID */
    public static final String PREF_GROUP_ID = "GroupId";

    /** プリファレンスキー 最終打刻日時 */
    public static final String PREF_LAST_STAMP_DATE = "LastStampDate";

    /** プリファレンスキー 緯度 */
    public static final String PREF_LATITUDE = "Latitude";

    /** プリファレンスキー 経度 */
    public static final String PREF_LONGITUDE = "Longitude";

    /** プリファレンスキー アラーム時刻群 */
    public static final String PREF_ALERM_TIMES = "AlermTimes";

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(JobcaaanService.class);

    /** Notification ID */
    private static final int NOTIFICATION_ID = 1111;

    private static final int MAX_ALEARM_COUNT = 10;

    /** 日付フォーマット */
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

    /** Binder */
    private final IBinder mBinder = new LocalBinder();

    /** Jobcan Webクライアント */
    private JobcanWebClient mJobcanWebClient;

    private CybozuWebClient mCybozuWebClient;

    /** 位置情報Listener */
    private LocationListener mLocationListener;

    /** Preference */
    private SharedPreferences mPreferences;

    /** 履歴データアクセサ */
    private HistoryDataAccessor mHstoryDataAccessor;

    /** Jobcaaan Notification */
    private JobcaaanNotification mNotification;


    private CountDownLatch mLocationLatch;
    private Location mNowLocation;
    private ILocationListenerStrategy mLocationListenerStrategy = new ILocationListenerStrategy() {
        @Override
        public void onDataReceived(long time, Location location) {
            if (mLocationLatch != null && mLocationLatch.getCount() > 0) {
                mNowLocation = location;
                mLocationLatch.countDown();
            }
        }

        @Override
        public void onStatusChanged(LocationStatus status) {
        }

        @Override
        public void onError(int level, final String msg, Throwable e) {
            showToast(msg);
        }
    };

    /**
     * Notification Action
     */
    private JobcaaanNotification.INotificationAction mNotificationAction = new JobcaaanNotification.INotificationAction() {

        private boolean isStamping = false;

        @Override
        @SuppressWarnings("all")
        public void onClickStamp(boolean withLocate) {
            logger.debug("onClickStamp");
            if (!isStamping) {
                isStamping = true;
                // 通知バーを閉じる
                try {
                    Object service = getApplicationContext().getSystemService("statusbar");
                    Class<?> clazz = Class.forName("android.app.StatusBarManager");
                    Method method = null;
                    if (Build.VERSION_CODES.JELLY_BEAN_MR1 <= Build.VERSION.SDK_INT) {
                        method = clazz.getMethod("collapsePanels");
                    } else if (Build.VERSION_CODES.ICE_CREAM_SANDWICH <= Build.VERSION.SDK_INT
                            && Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
                        method = clazz.getMethod("collapse");
                    }
                    method.invoke(service);
                } catch (Exception e) {
                }

                stamp(withLocate, new StampCallback() {
                    @Override
                    public void onFinish() {
                        isStamping = false;
                    }
                });
            }
        }

        @Override
        public void onClickShutdown() {
            logger.debug("onClickShutdown");
            stopSelf();
            stopResident();
        }
    };

    /**
     * サービス開始
     *
     * @param context コンテキスト
     */
    public static void startService(Context context) {
        logger.debug("startService");
        Intent intent = new Intent(context, JobcaaanService.class);
        context.startService(intent);
    }

    /**
     * サービス停止
     *
     * @param context コンテキスト
     */
    public static void stopService(Context context) {
        logger.debug("stopService");
        Intent intent = new Intent(context, JobcaaanService.class);
        context.stopService(intent);
    }

    /**
     * サービスBind
     *
     * @param context           コンテキスト
     * @param serviceConnection ServiceConnection
     */
    public static void bindService(Context context, ServiceConnection serviceConnection) {
        logger.debug("bindService");
        Intent intent = new Intent(context, JobcaaanService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * サービスUnbind
     *
     * @param context           コンテキスト
     * @param serviceConnection ServiceConnection
     */
    public static void unbindService(Context context, ServiceConnection serviceConnection) {
        logger.debug("unbindService");
        context.unbindService(serviceConnection);
    }

    /**
     * サービス起動中かどうか
     *
     * @param context コンテキスト
     * @return true:起動中
     */
    public static boolean isRunning(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningService = am.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo i : runningService) {
            if (JobcaaanService.class.getName().equals(i.service.getClassName()) && context.getPackageName().equals(i.service.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * アラーム再設定
     *
     * @param context コンテキスト
     */
    public static void reloadAlerm(Context context) {
        logger.debug("reloadAlerm");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> timeSet = sharedPreferences.getStringSet(PREF_ALERM_TIMES, new HashSet<String>());
        try {
            List<Integer[]> hhmmList = extractTime(timeSet);

            DailyAlarmManager dailyAlarmManager = new DailyAlarmManager(context);

            dailyAlarmManager.clear();

            // アラーム登録
            for (int i = 0; i < hhmmList.size() && i < MAX_ALEARM_COUNT; i++) {
                Integer[] hhmm = hhmmList.get(i);
                dailyAlarmManager.set(AlarmLogicReceiver.class, hhmm[0], hhmm[1]);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        DailyAlarmManager.dumpPref(context);
    }

    private static List<Integer[]> extractTime(Set<String> timeSet) throws Exception {
        List<Integer[]> ret = new ArrayList<>();
        if (timeSet != null) {
            for (String time : timeSet) {
                if (!TextUtils.isEmpty(time)) {
                    String[] hhmm = time.split(":");
                    Integer hour;
                    Integer minute;
                    if (hhmm.length == 2) {
                        try {
                            hour = Integer.parseInt(hhmm[0]);
                            minute = Integer.parseInt(hhmm[1]);
                        } catch (NumberFormatException e) {
                            throw new Exception(e);
                        }
                        ret.add(new Integer[]{hour, minute});
                    }
                }
            }
        }
        return ret;
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate() {
        logger.debug("onCreate");

        // ====== リソース類初期化 ==================
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mNotification = new JobcaaanNotification(this, NOTIFICATION_ID, false, mNotificationAction);
        mJobcanWebClient = new JobcanWebClient();
        mLocationListener = new LocationListener(getApplicationContext(), mLocationListenerStrategy);
        mHstoryDataAccessor = new HistoryDataAccessor(getApplicationContext());
        mCybozuWebClient = new CybozuWebClient();

        // Dump
        {
            String userCode = mPreferences.getString(PREF_USER_CODE, "");
            String groupId = mPreferences.getString(PREF_GROUP_ID, "");
            String lastStamp = mPreferences.getString(PREF_LAST_STAMP_DATE, "");
            String strLati = mPreferences.getString(PREF_LATITUDE, "");
            String strLong = mPreferences.getString(PREF_LONGITUDE, "");
            Set<String> timeSet = mPreferences.getStringSet(PREF_ALERM_TIMES, new HashSet<String>());

            logger.debug(PREF_USER_CODE + ":" + userCode);
            logger.debug(PREF_GROUP_ID + ":" + groupId);
            logger.debug(PREF_LAST_STAMP_DATE + ":" + lastStamp);
            logger.debug(PREF_LATITUDE + ":" + strLati);
            logger.debug(PREF_LONGITUDE + ":" + strLong);
            logger.debug(PREF_ALERM_TIMES + ":" + timeSet);
        }

        // ====== アラーム登録 ==================
        reloadAlerm(this);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        logger.debug("onBind");
        return mBinder;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onUnbind(Intent intent) {
        logger.debug("onUnbind");
        return super.onUnbind(intent);
    }

    /** {@inheritDoc} */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.debug("onStartCommand");

        // サービス強制終了時は諦める
        return START_NOT_STICKY;
//        return super.onStartCommand(intent, flags, startId);
    }

    /** {@inheritDoc} */
    @Override
    public void onDestroy() {
        logger.debug("onDestroy");
        mLocationListener.stop();
    }

    /**
     * 常駐開始
     */
    public void startResident() {
        logger.debug("startResident");
        mNotification.show();
        String lastStamp = mPreferences.getString(PREF_LAST_STAMP_DATE, "");
        if (!TextUtils.isEmpty(lastStamp)) {
            mNotification.update("最終打刻: " + lastStamp);
        }
    }

    /**
     * 常駐停止
     */
    public void stopResident() {
        logger.debug("stopResident");
        mNotification.hide();
    }

    /**
     * 常駐中かどうか
     *
     * @return true:常駐中
     */
    public boolean isResident() {
        logger.debug("isResident");
        return mNotification.isShow();
    }

    /**
     * 設定保存
     *
     * @param userCode ユーザコード
     * @param groupId  グループID
     */
    public void saveSetting(String userCode, String groupId) {
        logger.debug("saveSetting");
        if (TextUtils.isEmpty(userCode) || TextUtils.isEmpty(groupId)) {
            // ユーザ情報不正
            showToast(getString(R.string.error_validate_setting));
        } else {
            mPreferences.edit()
                    .putString(JobcaaanService.PREF_USER_CODE, userCode)
                    .putString(JobcaaanService.PREF_GROUP_ID, groupId)
                    .apply();
            showToast(getString(R.string.msg_save_setting));
        }
    }

    /**
     * 位置保存
     *
     * @param latitude  緯度
     * @param longitude 経度
     */
    public void saveLocation(double latitude, double longitude) {
        logger.debug("saveLocation");
        String address = LocationUtils.getAddressInJapan(this, latitude, longitude);
        if (TextUtils.isEmpty(address)) {
            // 位置情報不正
            showToast(getString(R.string.error_validate_setting));
        } else {
            mPreferences.edit()
                    .putString(JobcaaanService.PREF_LATITUDE, String.valueOf(latitude))
                    .putString(JobcaaanService.PREF_LONGITUDE, String.valueOf(longitude))
                    .apply();
            showToast(getString(R.string.msg_save_setting));
        }
    }

    /**
     * アラーム保存
     *
     * @param timeSet 時刻(hh:mm文字列)Set
     */
    public void saveAlearm(Set<String> timeSet) {
        logger.debug("saveAlearm");
        List<Integer[]> hhmmList = null;
        try {
            hhmmList = extractTime(timeSet);
        } catch (Exception e) {
            showToast(getString(R.string.error_validate_setting));
        }
        if (hhmmList != null) {
            // Preference保存
            mPreferences.edit()
                    .putStringSet(PREF_ALERM_TIMES, timeSet)
                    .apply();

            // アラームリロード
            reloadAlerm(this);

            showToast(getString(R.string.msg_save_setting));
        }
    }

    /**
     * 打刻
     *
     * @param withLocate 位置取得有無
     * @param callback   コールバック
     */
    public void stamp(boolean withLocate, final StampCallback callback) {
        logger.debug("stamp");
        if (withLocate) {
            stampWithGetLocation(callback);
        } else {
            String strLati = mPreferences.getString(PREF_LATITUDE, "");
            String strLong = mPreferences.getString(PREF_LONGITUDE, "");
            if (!TextUtils.isEmpty(strLati) && !TextUtils.isEmpty(strLong)) {
                Location location = new Location("manual");
                location.setLatitude(Double.parseDouble(strLati));
                location.setLongitude(Double.parseDouble(strLong));
                stamp(location, callback);
            } else {
                // 位置情報なし
                showToast(getString(R.string.error_failed_access_to_jobcan) + "(位置情報未設定)");
                mHstoryDataAccessor.insert(new Date(), "打刻", "失敗(位置情報未設定)");
                if (callback != null) {
                    callback.onFinish();
                }
            }
        }

        if (BuildConfig.FLAVOR.equals("own")) {
            mCybozuWebClient.stampFlow("a-kosuge@netwrk.co.jp", "******", new CybozuWebClient.ResultCallback() {
                @Override
                public void onSuccess() {
                    showToast("Cyboze打刻成功");
                }

                @Override
                public void onError(String msg, Throwable e) {
                    showToast("Cyboze打刻失敗\n" + msg);
                }
            });
        }
    }

    /**
     * 打刻(位置情報取得有)
     *
     * @param callback コールバック
     */
    private void stampWithGetLocation(final StampCallback callback) {
        // 現在位置取得
        mLocationLatch = new CountDownLatch(1);
        mNowLocation = null;
        mLocationListener.start();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    mLocationLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mLocationListener.stop();

                if (mNowLocation != null) {
                    stamp(mNowLocation, callback);
                } else {
                    showToast(getString(R.string.error_failed_get_now_location));
                    mHstoryDataAccessor.insert(new Date(), "打刻", "失敗(位置情報取得)");
                    if (callback != null) {
                        callback.onFinish();
                    }
                }

                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * 打刻
     *
     * @param location 位置情報
     * @param callback コールバック
     */
    private void stamp(final Location location, final StampCallback callback) {
        // 入力値取得
        final String userCode = mPreferences.getString(PREF_USER_CODE, "");
        final String groupId = mPreferences.getString(PREF_GROUP_ID, "");
//        final String userCode = "557748ec07ede4771c90186a56491625";
//        final String groupId = "2";
        if (TextUtils.isEmpty(userCode) || TextUtils.isEmpty(groupId)) {
            // ユーザ情報なし
            showToast(getString(R.string.error_failed_access_to_jobcan) + "(ユーザ情報未設定)");
            mHstoryDataAccessor.insert(new Date(), "打刻", "失敗(ユーザ情報未設定)");
            if (callback != null) {
                callback.onFinish();
            }
        } else {
            final Date date = new Date();

            // Jobcanアクセス
            mJobcanWebClient.stampFlow(userCode, groupId, date, location, new JobcanWebClient.ResultCallback() {
                @Override
                public void onSuccess() {
                    showToast(getString(R.string.msg_success_stamp) + "\n" + sdf.format(date)
                            + "\n" + LocationUtils.getAddressInJapan(JobcaaanService.this, location.getLatitude(), location.getLongitude()));
                    mHstoryDataAccessor.insert(new Date(), "打刻", "成功");
                    // 最後の打刻時刻を保存
                    mPreferences.edit()
                            .putString(PREF_LAST_STAMP_DATE, sdf.format(date))
                            .apply();
                    if (mNotification.isShow()) {
                        mNotification.update("最終打刻: " + sdf.format(date));
                    }
                    if (callback != null) {
                        callback.onFinish();
                    }
                }

                @Override
                public void onError(String msg) {
                    showToast(getString(R.string.error_failed_access_to_jobcan) + "(" + msg + ")");
                    mHstoryDataAccessor.insert(new Date(), "打刻", "失敗(Webアクセス)");
                    if (callback != null) {
                        callback.onFinish();
                    }
                }
            });
        }
    }

    /**
     * Toast表示
     *
     * @param message メッセージ
     */
    private void showToast(final String message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {

                LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View layout = View.inflate(getApplicationContext(), R.layout.layout_toast, null);

                // 画像を設定
                ImageView toastImage = (ImageView) layout.findViewById(R.id.toastImage);
                toastImage.setImageResource(R.mipmap.ic_launcher);

                // テキストを設定
                TextView toastText = (TextView) layout.findViewById(R.id.toastText);
                toastText.setText(message);

                Toast toast = new Toast(getApplicationContext());
                toast.setView(layout);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.show();
            }
        });
    }

    /**
     * 打刻コールバック
     */
    public interface StampCallback {
        /**
         * 完了時
         */
        void onFinish();
    }

    /**
     * ローカルバインダー
     */
    public class LocalBinder extends Binder {
        public JobcaaanService getService() {
            return JobcaaanService.this;
        }
    }
}
