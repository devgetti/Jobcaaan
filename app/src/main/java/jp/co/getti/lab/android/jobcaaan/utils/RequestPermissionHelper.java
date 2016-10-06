package jp.co.getti.lab.android.jobcaaan.utils;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * パーミッションリクエストHelper
 * <pre>
 *     パーミッション有効化リクエストを出す際に利用可能なHelper.
 *     Android 6からの新パーミッションモデルにより、
 *     アプリが必要とするパーミッション付与はインストール時の強制的なものより、
 *     ユーザが任意のものを好きなタイミングで選択可能になった。
 *
 *     本クラスでは必要なパーミッション付与をユーザに行ってもらうようにする補助機能を持つ。
 * </pre>
 */
@TargetApi(Build.VERSION_CODES.M)
public class RequestPermissionHelper {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(RequestPermissionHelper.class);

    /** 設定変更待ち受け用Latch */
    private CountDownLatch mLatch;

    /** 結果：許可済みパーミッションリスト */
    private List<String> mAuthedList;

    /** 結果：未許可パーミッションリスト */
    private List<String> mUnauthList;

    /** Activity */
    private Activity mActivity;

    /** リクエストコード */
    private int mRequestCode;

    /** チェック中有無 */
    private boolean mChecking;

    /**
     * コンストラクタ
     *
     * @param activity    Activity
     * @param requestCode リクエストコード
     */
    public RequestPermissionHelper(Activity activity, int requestCode) {
        mActivity = activity;
        mRequestCode = requestCode;
    }

    /**
     * パーミッションチェック&リクエスト
     * <pre>
     *     Android6からのパーミッションモデル(動的な権限チェック)に対応するため,
     *     AndroidManifest.xmlに記載された自身に必要なパーミッションの許可をユーザに促す。
     *     設定結果を指定のCallbackメソッド引数に渡して返す。
     *
     *     許可されていないパーミッションがある場合は、権限付与リクエストダイアログを表示する。
     *     (ダイアログでの操作結果は{@link Activity#onRequestPermissionsResult(int, String[], int[])}にて受け取る)
     *
     *     ＊＊＊＊＊注意＊＊＊＊＊
     *     リクエストダイアログでの結果を受け取るため、本メソッド実行にあわせて、
     *     onRequestPermissionsResultメソッド内で#setResultメソッドを呼び出さなくてはならない。
     *     setResultの呼び出し後、引数で指定されたコールバックを呼び出す。
     *     コールバックはUIスレッドで実行する。(指定のActivityインスタンスのrunOnUithreadメソッドで実行)
     * </pre>
     */
    public void checkAndRequest(final Callback callback) {
        if (!mChecking) {
            mChecking = true;

            String[] unauthPermissions = PermissionUtils.getUnauthorisedPermissions(mActivity);
            List<String> unauthPermissionList = new LinkedList(Arrays.asList(unauthPermissions));
            if (unauthPermissionList.size() > 0 && unauthPermissionList.contains(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                // SYSTEM_ALERT_WINDOWは特殊なので除外
                logger.debug("Exclude " + Manifest.permission.SYSTEM_ALERT_WINDOW);
                unauthPermissionList.remove(unauthPermissionList.indexOf(Manifest.permission.SYSTEM_ALERT_WINDOW));
            }
            if (unauthPermissionList.size() > 0) {
                logger.debug("Have unauth permissions.");
                mLatch = new CountDownLatch(1);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (mLatch != null) {
                            try {
                                mLatch.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        runCallback(callback);
                        mChecking = false;
                    }
                }).start();
                mActivity.requestPermissions(unauthPermissionList.toArray(new String[unauthPermissionList.size()]), mRequestCode);
            } else {
                logger.debug("Have'nt unauth permissions.");
                String[] permissions = PermissionUtils.getPredefinedPermissions(mActivity);
                mAuthedList = (permissions != null) ? Arrays.asList(permissions) : new ArrayList<String>();
                mUnauthList = new ArrayList<>();
                runCallback(callback);
                mChecking = false;
            }
        }
    }

    /**
     * パーミッション設定結果を設定する。
     * <pre>
     *     ユーザへのパーミッション許可リクエストダイアログの操作結果より設定結果を判定する。
     *     本メソッドはコンストラクタで指定されたActivityクラスのonRequestPermissionsResultメソッド内で呼び出すこと。
     *
     *     結果待機状態を解除(チェックメソッドで実行している待機スレッドの待ち受け解除)し、
     *     コールバック実行を可能にする。
     * </pre>
     *
     * @param requestCode  リクエストコード
     * @param permissions  パーミッションリスト
     * @param grantResults 結果リスト
     */
    public void setResult(int requestCode, String[] permissions, int[] grantResults) {
        if (mChecking && mRequestCode == requestCode) {
            if (mLatch.getCount() > 0) {
                mAuthedList = new ArrayList<>();
                mUnauthList = new ArrayList<>();
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        mAuthedList.add(permissions[i]);
                    } else {
                        mUnauthList.add(permissions[i]);
                    }
                }
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
                    callback.onResult(mAuthedList, mUnauthList);
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
         * @param authedList 許可済みパーミッションリスト
         * @param unauthList 未許可パーミッションリスト
         */
        void onResult(List<String> authedList, List<String> unauthList);
    }
}
