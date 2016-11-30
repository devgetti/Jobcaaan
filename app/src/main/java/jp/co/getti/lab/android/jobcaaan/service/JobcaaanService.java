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

import jp.co.getti.lab.android.jobcaaan.R;
import jp.co.getti.lab.android.jobcaaan.activity.MainActivity;
import jp.co.getti.lab.android.jobcaaan.location.ILocationListenerStrategy;
import jp.co.getti.lab.android.jobcaaan.location.LocationListener;
import jp.co.getti.lab.android.jobcaaan.location.LocationStatus;
import jp.co.getti.lab.android.jobcaaan.notification.JobcaaanNotification;
import jp.co.getti.lab.android.jobcaaan.receiver.AlarmLogicReceiver;
import jp.co.getti.lab.android.jobcaaan.utils.DailyAlarmManager;
import jp.co.getti.lab.android.jobcaaan.utils.JobcanWebClient;
import jp.co.getti.lab.android.jobcaaan.utils.LocationUtils;


public class JobcaaanService extends Service {

    public static final String PREF_USER_CODE = "UserCode";
    public static final String PREF_GROUP_ID = "GroupId";
    public static final String PREF_LAST_STAMP_DATE = "LastStampDate";
    public static final String PREF_LATITUDE = "Latitude";
    public static final String PREF_LONGITUDE = "Longitude";
    public static final String PREF_ALERM_TIMES = "AlermTimes";

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(JobcaaanService.class);
    private static final int NOTIFICATION_ID = 1111;
    private static final int MAX_ALEARM_COUNT = 10;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

    private final IBinder mBinder = new LocalBinder();
    private JobcanWebClient mJobcanWebClient;
    private LocationListener mLocationListener;
    private CountDownLatch mLocationLatch;
    private Location mNowLocation;
    private SharedPreferences mPreferences;
    private JobcaaanNotification mNotification;
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

    public static void startService(Context context) {
        logger.debug("startService");
        Intent intent = new Intent(context, JobcaaanService.class);
        context.startService(intent);
    }

    public static void stopService(Context context) {
        logger.debug("stopService");
        Intent intent = new Intent(context, JobcaaanService.class);
        context.stopService(intent);
    }

    public static void bindService(Context context, ServiceConnection serviceConnection) {
        logger.debug("bindService");
        Intent intent = new Intent(context, JobcaaanService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public static void unbindService(Context context, ServiceConnection serviceConnection) {
        logger.debug("unbindService");
        Intent intent = new Intent(context, JobcaaanService.class);
        context.unbindService(serviceConnection);
    }

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

    @Override
    public void onCreate() {
        logger.debug("onCreate");

        // ====== リソース類初期化 ==================
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mNotification = new JobcaaanNotification(this, NOTIFICATION_ID, false, mNotificationAction);
        mJobcanWebClient = new JobcanWebClient();
        mLocationListener = new LocationListener(getApplicationContext(), mLocationListenerStrategy);

        // ====== アラーム登録 ==================
        reloadAlerm(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        logger.debug("onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        logger.debug("onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.debug("onStartCommand");

        // サービス強制終了時は諦める
        return START_NOT_STICKY;
//        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        logger.debug("onDestroy");
        mLocationListener.stop();
    }

    public void startResident() {
        logger.debug("startResident");
        mNotification.show();
        String lastStamp = mPreferences.getString(PREF_LAST_STAMP_DATE, "");
        if (!TextUtils.isEmpty(lastStamp)) {
            mNotification.update("最終打刻: " + lastStamp, MainActivity.class.getName());
        }
    }

    public void stopResident() {
        logger.debug("stopResident");
        mNotification.hide();
    }

    public boolean isResident() {
        logger.debug("isResident");
        return mNotification.isShow();
    }

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
                if (callback != null) {
                    callback.onFinish();
                }
            }
        }
    }

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
                    if (callback != null) {
                        callback.onFinish();
                    }
                }

                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void stamp(final Location location, final StampCallback callback) {
        // 入力値取得
        final String userCode = mPreferences.getString(PREF_USER_CODE, "");
        final String groupId = mPreferences.getString(PREF_GROUP_ID, "");
//        final String userCode = "557748ec07ede4771c90186a56491625";
//        final String groupId = "2";
        if (TextUtils.isEmpty(userCode) || TextUtils.isEmpty(groupId)) {
            // ユーザ情報なし
            showToast(getString(R.string.error_failed_access_to_jobcan) + "(ユーザ情報未設定)");
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
                    // 最後の打刻時刻を保存
                    mPreferences.edit()
                            .putString(PREF_LAST_STAMP_DATE, sdf.format(date))
                            .apply();
                    if (mNotification.isShow()) {
                        mNotification.update("最終打刻: " + sdf.format(date), MainActivity.class.getName());
                    }
                    if (callback != null) {
                        callback.onFinish();
                    }
                }

                @Override
                public void onError(String msg) {
                    showToast(getString(R.string.error_failed_access_to_jobcan) + "(" + msg + ")");
                    if (callback != null) {
                        callback.onFinish();
                    }
                }
            });
        }
    }

    private void showToast(final String message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {

                LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View layout = inflater.inflate(R.layout.layout_toast, null);

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

    public interface StampCallback {
        void onFinish();
    }

    public class LocalBinder extends Binder {
        public JobcaaanService getService() {
            return JobcaaanService.this;
        }
    }
}
