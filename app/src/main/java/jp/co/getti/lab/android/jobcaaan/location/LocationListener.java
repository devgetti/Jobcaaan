package jp.co.getti.lab.android.jobcaaan.location;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 位置情報Listener
 * <pre>
 *     端末機能より位置情報を取得する。
 *     ・FusedLocationApi(GoogleApi)を利用してのGPS、Wifiからの位置情報を取得
 *     ・取得したデータStrategyを経由して外部に渡す。
 * </pre>
 */
public class LocationListener implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(LocationListener.class);

    /** 計測間隔(ミリ秒) */
    private static final int LOCATION_INTERVAL = 5000;

    /** GoogleAPI Client */
    protected GoogleApiClient mGoogleApiClient;

    /** Location Request */
    protected LocationRequest mLocationRequest;

    /** センサ用ハンドラスレッド */
    private HandlerThread mHandlerThread;

    /** 計測状態 */
    private boolean mListening;

    /** コンテキスト */
    private Context mContext;

    /** LocationListenerStrategy */
    private ILocationListenerStrategy mListener;

    /**
     * コンストラクタ
     *
     * @param context コンテキスト
     */
    public LocationListener(Context context, ILocationListenerStrategy listener) {
        mContext = context;
        mListener = listener;

        // GoogleAPI Clientのビルド
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // 位置情報リクエスト作成
        mLocationRequest = new LocationRequest();
        // 取得間隔(目安)
        mLocationRequest.setInterval(LOCATION_INTERVAL);
        // 取得間隔(長時間取得時はこちらも設定が必要)
        mLocationRequest.setFastestInterval(LOCATION_INTERVAL);
        // 出来うる限り正確な位置
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /** {@inheritDoc} */
    @Override
    public void onConnected(Bundle connectionHint) {
        logger.info("Connected to GoogleApiClient");
    }

    /** {@inheritDoc} */
    @Override
    public void onLocationChanged(Location location) {
        logger.debug("onLocationChanged");

        // 位置情報の時刻が狂った時刻を返すことがあるため、OS時間で扱う
        onDataReceived(System.currentTimeMillis(), location);
    }

    /** {@inheritDoc} */
    @Override
    public void onConnectionSuspended(int cause) {
        logger.info("Connection suspended to GoogleApiClient");

        // 再接続
        mGoogleApiClient.connect();
    }

    /** {@inheritDoc} */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        logger.warn("Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());

        // 再接続
        mGoogleApiClient.connect();
    }

    /**
     * 計測開始
     */
    public void start() {
        start(null);
    }

    public void start(LocationRequest locationRequest) {
        logger.debug("start");
        if (!mListening) {
            mListening = true;
            mLocationRequest = (locationRequest != null) ? locationRequest : mLocationRequest;

            // データ取得用のHandlerThread作成
            mHandlerThread = new HandlerThread("SensorThread-Location", android.os.Process.THREAD_PRIORITY_DEFAULT);
            mHandlerThread.start();

            // GoogleAPIクライアント接続が非同期前提で行われるため、開始ロジックは非同期で実施
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    if (Looper.myLooper() == null) {
                        Looper.prepare();
                    }

                    // GoogleApiClient接続
                    mGoogleApiClient.blockingConnect();
                    try {
                        // 位置情報更新開始(FusedLocationApi)
                        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, LocationListener.this, mHandlerThread.getLooper());
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                    // 状態通知
                    onStatusChanged();
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * 計測停止
     */
    public void stop() {
        logger.debug("stop");
        if (mListening) {

            // GoogleAPI Client切断
            if (mGoogleApiClient.isConnected()) {
                // 位置情報更新停止(FusedLocationApi)
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

                mGoogleApiClient.disconnect();
            }

            // データ受信用HandlerThread停止
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mHandlerThread.quitSafely();
            } else {
                mHandlerThread.quit();
            }

            mListening = false;
        }
        // 状態通知
        onStatusChanged();
    }

    /**
     * ステータスを取得する。
     *
     * @return ステータス
     */
    public LocationStatus getStatus() {
        logger.debug("getStatus");
        return new LocationStatus(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION), mListening);
    }

    /**
     * 状態通知
     */
    private void onStatusChanged() {
        logger.debug("onStatusChanged");
        if (mListener != null) {
            mListener.onStatusChanged(getStatus());
        }
    }

    /**
     * データ通知
     *
     * @param time     時刻
     * @param location 位置情報
     */
    private void onDataReceived(long time, Location location) {
        logger.debug("onDataReceived");
        if (mListener != null) {
            mListener.onDataReceived(time, location);
        }
    }

    /**
     * エラー通知
     *
     * @param level   レベル
     * @param message メッセージ
     * @param e       例外
     */
    @SuppressWarnings("unused")
    private void onError(int level, String message, Throwable e) {
        logger.debug("onError");
        if (mListener != null) {
            mListener.onError(level, message, e);
        }
    }
}
