package jp.co.getti.lab.android.jobcaaan.activity;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import jp.co.getti.lab.android.jobcaaan.R;
import jp.co.getti.lab.android.jobcaaan.service.JobcaaanService;
import jp.co.getti.lab.android.jobcaaan.utils.RequestLocationAccuracyHelper;
import jp.co.getti.lab.android.jobcaaan.utils.RequestPermissionHelper;

public class MainActivity extends AppCompatActivity {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(MainActivity.class);

    /** リクエストコード　パーミッション */
    private static final int REQUEST_CODE_PERMISSION = 100;

    /** リクエストコード　位置情報精度 */
    private static final int REQUEST_LOCATION_ACCURACY = 101;

    /** ローディングView */
    protected View mLoadingView;

    /** ユーザコードView */
    protected EditText mTxtUserCode;

    /** グループIDView */
    protected EditText mTxtGroupId;

    /** パーミッションリクエストHelper */
    private RequestPermissionHelper mRequestPermissionHelper;

    /** 位置情報精度リクエストHelper */
    private RequestLocationAccuracyHelper mRequestLocationAccuracyHelper;

    /** Jobcaaanサービス */
    private JobcaaanService mJobcaaanService;

    /** 打刻ボタンクリックListener */
    private View.OnClickListener mBtnStampClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logger.debug("mBtnStampClickListener - onClick");
            // バリデーション
            if (mJobcaaanService != null) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.dialog_confirm)
                        .setMessage("打刻してよろしいですか？")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                enableActivity(false);
                                mJobcaaanService.stamp(new JobcaaanService.StampCallback() {
                                    @Override
                                    public void onFinish() {
                                        enableActivity(true);
                                    }
                                });
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        }
    };

    /** 常駐ボタンクリックListener */
    private View.OnClickListener mBtnResidentClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logger.debug("mBtnResidentClickListener - onClick");
            if (mJobcaaanService != null) {
                if (mJobcaaanService.isResident()) {
                    mJobcaaanService.stopResident();
                } else {
                    mJobcaaanService.startResident();
                }
            }
        }
    };

    /** 設定保存ボタンクリックListener */
    private View.OnClickListener mBtnSaveSettingClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logger.debug("mBtnSaveSettingClickListener - onClick");
            if (mJobcaaanService != null && validate()) {
                String userCode = mTxtUserCode.getText().toString();
                String groupId = mTxtGroupId.getText().toString();
                mJobcaaanService.saveSetting(userCode, groupId);
            }
        }
    };

    /** Jobcaaan ServiceConnection */
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.debug("onServiceConnected");
            JobcaaanService.LocalBinder binder = (JobcaaanService.LocalBinder) service;
            mJobcaaanService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logger.debug("onServiceDisconnected name:" + name.toString());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logger.debug("onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ====== サービス起動 ==================
        JobcaaanService.startService(getApplicationContext());

        // ====== リソース類初期化 ==================
        mRequestPermissionHelper = new RequestPermissionHelper(this, REQUEST_CODE_PERMISSION);
        mRequestLocationAccuracyHelper = new RequestLocationAccuracyHelper(this, REQUEST_LOCATION_ACCURACY);

        // ======= View初期化 =================
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // ユーザコード
        mTxtUserCode = (EditText) findViewById(R.id.txtUserCode);
        mTxtUserCode.setText(preferences.getString(JobcaaanService.PREF_USER_CODE, ""));
        mTxtUserCode.setFilters(new InputFilter[]{new MyFilter()});

        // グループID
        mTxtGroupId = (EditText) findViewById(R.id.txtGroupId);
        mTxtGroupId.setText(preferences.getString(JobcaaanService.PREF_GROUP_ID, ""));

        // LoadingView
        mLoadingView = View.inflate(this, R.layout.layout_loading, null);

        // 打刻ボタン
        Button btnStamp = (Button) findViewById(R.id.btnStamp);
        if (btnStamp != null) {
            btnStamp.setOnClickListener(mBtnStampClickListener);
        }

        // 常駐ボタン
        Button btnResident = (Button) findViewById(R.id.btnResident);
        if (btnResident != null) {
            btnResident.setOnClickListener(mBtnResidentClickListener);
        }

        // 設定保存ボタン
        Button btnSaveSetting = (Button) findViewById(R.id.btnSaveSetting);
        if (btnSaveSetting != null) {
            btnSaveSetting.setOnClickListener(mBtnSaveSettingClickListener);
        }

//        if (BuildConfig.DEBUG && BuildConfig.FLAVOR.endsWith("own")) {
//            mTxtUserCode.setText("557748ec07ede4771c90186a56491625");
//            mTxtGroupId.setText("2");
//        }
    }

    @Override
    protected void onResume() {
        logger.debug("onResume");
        super.onResume();
        JobcaaanService.bindService(getApplicationContext(), serviceConnection);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mRequestPermissionHelper.checkAndRequest(new RequestPermissionHelper.Callback() {
                @Override
                public void onResult(List<String> authedList, List<String> unauthList) {
                    logger.debug("mRequestPermissionHelper - onResult");
                    if (unauthList.size() > 0) {
                        showToast(getString(R.string.error_required_permission_auth));
                        finish();
                    }
                }
            });
        }
        // 位置精度設定チェック
        mRequestLocationAccuracyHelper.checkAndRequest(new RequestLocationAccuracyHelper.Callback() {
            @Override
            public void onResult(final boolean result) {
                logger.debug("mRequestLocationAccuracyHelper - onResult");
                if (!result) {
                    showToast(getString(R.string.error_required_location_accuracy));
                    finish();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        logger.debug("onPause");
        JobcaaanService.unbindService(getApplicationContext(), serviceConnection);
        super.onPause();
    }

    /** {@inheritDoc} */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        logger.debug("onRequestPermissionsResult");
        mRequestPermissionHelper.setResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        logger.debug("onActivityResult");
        mRequestLocationAccuracyHelper.setResult(requestCode, resultCode, data);
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void enableActivity(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup contentRoot = (ViewGroup) MainActivity.this.findViewById(android.R.id.content);
                assert (contentRoot != null);
                if (enable) {
                    if (mLoadingView.getParent() != null) {
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                        contentRoot.removeView(mLoadingView);
                    }
                } else {
                    if (mLoadingView.getParent() == null) {
                        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                        contentRoot.addView(mLoadingView);
                    }
                }
            }
        });
    }

    private boolean validate() {
        boolean isError = false;
        final String userCode = mTxtUserCode.getText().toString();
        final String groupId = mTxtGroupId.getText().toString();
        // ユーザIDValidate
        if (TextUtils.isEmpty(userCode)) {
            mTxtUserCode.setError(getString(R.string.error_field_required));
            mTxtUserCode.requestFocus();
            isError = true;
        }

        if (TextUtils.isEmpty(groupId)) {
            mTxtGroupId.setError(getString(R.string.error_field_required));
            mTxtGroupId.requestFocus();
            isError = true;
        }

        return !isError;
    }

    class MyFilter implements InputFilter {
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {

            if (source.toString().matches("^[a-zA-Z0-9]+$")) {
                return source;
            } else {
                return "";
            }
        }
    }
}
