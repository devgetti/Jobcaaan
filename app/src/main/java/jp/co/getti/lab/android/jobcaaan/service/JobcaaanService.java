package jp.co.getti.lab.android.jobcaaan.service;

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
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jp.co.getti.lab.android.jobcaaan.BuildConfig;
import jp.co.getti.lab.android.jobcaaan.R;
import jp.co.getti.lab.android.jobcaaan.activity.MainActivity;
import jp.co.getti.lab.android.jobcaaan.location.ILocationListenerStrategy;
import jp.co.getti.lab.android.jobcaaan.location.LocationListener;
import jp.co.getti.lab.android.jobcaaan.location.LocationStatus;
import jp.co.getti.lab.android.jobcaaan.notification.JobcaaanNotification;
import jp.co.getti.lab.android.jobcaaan.utils.JobcanWebClient;


public class JobcaaanService extends Service {

    public static final String PREF_USER_CODE = "UserCode";
    public static final String PREF_GROUP_ID = "GroupId";
    public static final String PREF_LAST_STAMP_DATE = "LastStampDate";

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(JobcaaanService.class);
    private static final int NOTIFICATION_ID = 1111;
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
        public void onClickStamp() {
            logger.debug("onClickStamp");
            if(!isStamping) {
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

                stamp(new StampCallback() {
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

    @Override
    public void onCreate() {
        logger.debug("onCreate");

        // ====== リソース類初期化 ==================
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mNotification = new JobcaaanNotification(this, NOTIFICATION_ID, false, mNotificationAction);
        mJobcanWebClient = new JobcanWebClient();
        mLocationListener = new LocationListener(getApplicationContext(), mLocationListenerStrategy);
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
        if(!TextUtils.isEmpty(lastStamp)) {
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

    public void stamp(final StampCallback callback) {
        logger.debug("stamp");
        if (BuildConfig.FLAVOR.equals("own")) {
            Location location = new Location("manual");
            location.setLatitude(35.456060d);
            location.setLongitude(139.629811d);
            stamp(location, callback);
        } else {
            stampWithGetLocation(callback);
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

    private void stamp(Location location, final StampCallback callback) {
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
                    showToast(getString(R.string.msg_success_stamp) + "\n" + sdf.format(date));
                    // 最後の打刻時刻を保存
                    mPreferences.edit()
                            .putString(PREF_LAST_STAMP_DATE, sdf.format(date))
                            .apply();
                    if(mNotification.isShow()) {
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
