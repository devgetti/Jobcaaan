package jp.co.getti.lab.android.jobcaaan.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * 位置精度設定リクエストHelper
 * <pre>
 *     端末の位置情報精度設定をユーザにリクエストするHelper。
 *
 *     位置情報は端末の「設定」-「位置情報」項目より、高、中、低と精度が選択可能。
 *     (Wifi、GPS、モバイルネットワークの組み合わせで精度変化)
 *
 *     本クラスでは必要な位置情報設定をユーザに行ってもらえるようにする補助機能を持つ。
 * </pre>
 */
public class RequestLocationAccuracyHelper {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(RequestLocationAccuracyHelper.class);

    /** 設定変更待ち受け用Latch */
    private CountDownLatch mLatch;

    /** Activity */
    private Activity mActivity;

    /** リクエストコード */
    private int mRequestCode;

    /** チェック中有無 */
    private boolean mChecking;

    /** GoogleApiClient */
    private GoogleApiClient mGoogleApiClient;

    /** 結果 */
    private boolean mResult;

    /**
     * コンストラクタ
     *
     * @param activity    Activity
     * @param requestCode リクエストコード
     */
    public RequestLocationAccuracyHelper(Activity activity, int requestCode) {
        mActivity = activity;
        mRequestCode = requestCode;
    }

    /**
     * 位置情報精度チェック＆リクエスト
     * <pre>
     *     アプリで要求する位置情報精度が設定されているかチェックする。
     *     ・ここでの位置情報精度とは端末の「設定」-「位置情報」から設定する端末自体の位置情報設定
     *     　これがGPSなど低精度であると、アプリ内での位置情報取得時の精度にも影響があるため、
     *     　あらかじめ設定を促す必要がある。
     *     設定結果を指定のCallbackメソッド引数に渡して返す。
     *
     *     アプリが要求する精度を満たしていない場合は、ユーザに設定を促すリクエストダイアログを表示する。
     *     (ダイアログ設定結果はコンストラクタで指定したActivityのonActivityResultメソッドにより受け取る。)
     *
     *     ＊＊＊＊＊注意＊＊＊＊＊
     *     リクエストダイアログでの結果を受け取るため、本メソッド実行にあわせて、
     *     onActivityResultメソッド内で#setResultメソッドを呼び出さなくてはならない。
     *     setResultの呼び出し後、引数で指定されたコールバックを呼び出す。
     *     コールバックはUIスレッドで実行する。(指定のActivityインスタンスのrunOnUithreadメソッドで実行)
     * </pre>
     *
     * @see <a href="http://qiita.com/daisy1754/items/aa9ad75d1a84b745469b">参考</a>
     */
    public void checkAndRequest(final Callback callback) {
        if (!mChecking) {
            mChecking = true;

            mGoogleApiClient = new GoogleApiClient.Builder(mActivity)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(@Nullable Bundle bundle) {
                            logger.debug("onConnected");

                            // 位置情報リクエスト作成
                            final LocationRequest mLocationRequest = new LocationRequest();
                            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                            // 必要な位置情報精度設定を満たしているかチェック
                            LocationSettingsRequest locationSettingRequest = new LocationSettingsRequest.Builder().addLocationRequest(mLocationRequest).setAlwaysShow(true).build();
                            PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, locationSettingRequest);
                            result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                                @Override
                                public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                                    logger.debug("LocationSettingRequest - onResult status:" + locationSettingsResult.getStatus());
                                    Status status = locationSettingsResult.getStatus();
                                    int code = status.getStatusCode();
                                    if (code == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                                        // 条件を満たしていない場合
                                        mLatch = new CountDownLatch(1);
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (mLatch != null) {
                                                    try {
                                                        mLatch.await();
                                                        logger.debug("Success dialog proc.");
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                runCallback(callback);
                                                mGoogleApiClient.disconnect();
                                                mChecking = false;
                                            }
                                        }).start();
                                        try {
                                            // 位置情報設定変更要求ダイアログを表示
                                            logger.debug("Show location request dialog.");
                                            status.startResolutionForResult(mActivity, mRequestCode);
                                        } catch (IntentSender.SendIntentException e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        mResult = (code == LocationSettingsStatusCodes.SUCCESS);
                                        runCallback(callback);
                                        mGoogleApiClient.disconnect();
                                        mChecking = false;

                                        //  ステータスコードがLocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLEは
                                        // 位置情報が取得できず、なおかつその状態からの復帰も難しい時呼ばれるらしい
                                    }
                                }
                            });
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            logger.error("onConnectionSuspended");
                            mResult = false;
                            runCallback(callback);
                            mGoogleApiClient.disconnect();
                            mChecking = false;
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                            logger.error("onConnectionFailed");
                            final String str = GoogleApiAvailability.getInstance().getErrorString(connectionResult.getErrorCode());
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mActivity, str, Toast.LENGTH_LONG).show();
                                }
                            });
                            mResult = false;
                            runCallback(callback);
                            mChecking = false;
                        }
                    })
                    .addApi(LocationServices.API)
                    .build();
            mGoogleApiClient.connect();
        }
    }

    /**
     * 位置情報設定結果を設定する。
     * <pre>
     *     ユーザへの位置情報設定リクエストダイアログの操作結果より位置情報設定結果を判定する。
     *     本メソッドはコンストラクタで指定されたActivityクラスの
     *     onActivityResultメソッド内で呼び出すこと。
     *
     *     結果待機状態を解除(チェックメソッドで実行している待機スレッドの待ち受け解除)し、
     *     コールバック実行を可能にする。
     * </pre>
     *
     * @param requestCode リクエストコード
     * @param resultCode  結果コード
     * @param data        データ(未使用)
     */
    @SuppressWarnings("unused")
    public void setResult(int requestCode, int resultCode, Intent data) {
        if (mChecking && mRequestCode == requestCode) {
            if (mLatch.getCount() > 0) {
                mResult = (resultCode == Activity.RESULT_OK);
                mLatch.countDown();
            }
        }
    }

    /**
     * コールバック実行
     *
     * @param callback コールバック
     */
    private void runCallback(final Callback callback) {
        if (callback != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(mResult);
                }
            });
        }
    }

    /**
     * コールバック
     */
    public interface Callback {
        /**
         * 結果取得
         *
         * @param result 結果(true:成功(位置変更してもらえた　あるいは最初から設定済み)
         */
        void onResult(boolean result);
    }
}
