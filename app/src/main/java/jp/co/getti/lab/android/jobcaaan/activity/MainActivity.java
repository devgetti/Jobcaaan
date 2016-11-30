package jp.co.getti.lab.android.jobcaaan.activity;

import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import jp.co.getti.lab.android.jobcaaan.BuildConfig;
import jp.co.getti.lab.android.jobcaaan.R;
import jp.co.getti.lab.android.jobcaaan.location.ILocationListenerStrategy;
import jp.co.getti.lab.android.jobcaaan.location.LocationListener;
import jp.co.getti.lab.android.jobcaaan.location.LocationStatus;
import jp.co.getti.lab.android.jobcaaan.service.JobcaaanService;
import jp.co.getti.lab.android.jobcaaan.utils.LocationUtils;
import jp.co.getti.lab.android.jobcaaan.utils.RequestLocationAccuracyHelper;
import jp.co.getti.lab.android.jobcaaan.utils.RequestPermissionHelper;
import jp.co.getti.lab.android.jobcaaan.view.AutoResizeTextView;

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

    /** 緯度View */
    protected EditText mTxtLatitude;

    /** 経度View */
    protected EditText mTxtLongitude;

    /** 住所View */
    protected TextView mTxtAddress;

    /** アラーム時刻1 */
    protected TextView mTxtAlerm1;

    /** アラーム時刻2 */
    protected TextView mTxtAlerm2;

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
            stamp(false);
        }
    };

    /** 打刻(位置取得有)ボタンクリックListener */
    private View.OnClickListener mBtnStampWithLocateClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logger.debug("mBtnStampWithLocateClickListener - onClick");
            // バリデーション
            stamp(true);
        }
    };

    /** 位置取得ボタンクリックListener */
    private View.OnClickListener mBtnLocationClickListener = new View.OnClickListener() {

        private LocationListener mLocationListener;

        @Override
        public void onClick(View v) {
            // 位置精度設定チェック
            mRequestLocationAccuracyHelper.checkAndRequest(new RequestLocationAccuracyHelper.Callback() {
                @Override
                public void onResult(final boolean result) {
                    logger.debug("mRequestLocationAccuracyHelper - onResult");
                    if (!result) {
                        showToast(getString(R.string.error_required_location_accuracy));
                    } else {
                        // 位置情報取得
                        mLocationListener = new LocationListener(MainActivity.this, new ILocationListenerStrategy() {
                            @Override
                            public void onDataReceived(long time, final Location location) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mTxtLatitude.setText(String.valueOf(location.getLatitude()));
                                        mTxtLongitude.setText(String.valueOf(location.getLongitude()));
                                    }
                                });
                                finish();
                            }

                            @Override
                            public void onStatusChanged(LocationStatus status) {
                            }

                            @Override
                            public void onError(int level, String msg, Throwable e) {
                                logger.error("msg", e);
                                showToast(getString(R.string.error_failed_get_now_location));
                                finish();
                            }

                            private void finish() {
                                mLocationListener.stop();
                            }
                        });
                        mLocationListener.start();
                    }
                }
            });
        }
    };

    /** アラーム設定ボタンクリックListener */
    private View.OnClickListener mBtnAlermClickListener = new View.OnClickListener() {

        private AutoResizeTextView targetText;

        private TimePickerDialog.OnTimeSetListener onTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                if (targetText != null) {
                    targetText.setText(String.format(Locale.JAPAN, "%02d:%02d", hourOfDay, minute));
                    targetText.resizeText();
                }
            }
        };

        @Override
        public void onClick(View v) {

            targetText = (AutoResizeTextView) v.getTag();

            Calendar calendar = Calendar.getInstance();
            int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            TimePickerDialog dialog = new TimePickerDialog(MainActivity.this,
                    onTimeSetListener, hourOfDay, minute, true);
            dialog.show();
        }
    };

    /** アラーム設定クリアボタンクリックListener */
    private View.OnClickListener mBtnAlermClearClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            AutoResizeTextView targetText = (AutoResizeTextView) v.getTag();
            targetText.setText("");
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

    /** 位置情報保存ボタンクリックListener */
    private View.OnClickListener mBtnSaveLocationClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logger.debug("mBtnSaveLocationClickListener - onClick");
            if (mJobcaaanService != null && validLocation()) {
                double latitude = Double.parseDouble(mTxtLatitude.getText().toString().trim());
                double longitude = Double.parseDouble(mTxtLongitude.getText().toString().trim());
                mJobcaaanService.saveLocation(latitude, longitude);
            }
        }
    };

    /** 設定保存ボタンクリックListener */
    private View.OnClickListener mBtnSaveSettingClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logger.debug("mBtnSaveSettingClickListener - onClick");
            if (mJobcaaanService != null && validSetting()) {
                String userCode = mTxtUserCode.getText().toString();
                String groupId = mTxtGroupId.getText().toString();
                mJobcaaanService.saveSetting(userCode, groupId);
            }
        }
    };

    /** アラーム保存ボタンクリックListener */
    private View.OnClickListener mBtnSaveAlermClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logger.debug("mBtnSaveAlermClickListener - onClick");
            if (mJobcaaanService != null) {
                Set<String> timeSet = new LinkedHashSet<>();
                String[] times = new String[]{mTxtAlerm1.getText().toString(), mTxtAlerm2.getText().toString()};
                for (String time : times) {
                    if (!TextUtils.isEmpty(time)) {
                        timeSet.add(time);
                    }
                }
                mJobcaaanService.saveAlearm(timeSet);
            }
        }
    };

    /** Jobcaaan ServiceConnection */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
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

    /** 緯度、経度テキスト変更時 */
    private TextWatcher mLocationTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            logger.debug("changed " + s);
            String strLat = mTxtLatitude.getText().toString().trim();
            String strLong = mTxtLongitude.getText().toString().trim();
            if (!TextUtils.isEmpty(strLat) && !TextUtils.isEmpty(strLong)) {
                String address = LocationUtils.getAddressInJapan(MainActivity.this, Double.parseDouble(strLat), Double.parseDouble(strLong));
                mTxtAddress.setText((address != null) ? address : "");
            } else {
                mTxtAddress.setText("");
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
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
        mTxtUserCode.setFilters(new InputFilter[]{new AlphaNumericFilter()});

        // グループID
        mTxtGroupId = (EditText) findViewById(R.id.txtGroupId);
        mTxtGroupId.setText(preferences.getString(JobcaaanService.PREF_GROUP_ID, ""));

        // 緯度、経度、住所
        mTxtAddress = (TextView) findViewById(R.id.txtAddress);

        mTxtLatitude = (EditText) findViewById(R.id.txtLatitude);
        mTxtLatitude.setText(preferences.getString(JobcaaanService.PREF_LATITUDE, ""));

        mTxtLongitude = (EditText) findViewById(R.id.txtLongitude);
        mTxtLongitude.setText(preferences.getString(JobcaaanService.PREF_LONGITUDE, ""));

        mTxtLatitude.addTextChangedListener(mLocationTextWatcher);
        mTxtLongitude.addTextChangedListener(mLocationTextWatcher);

        mLocationTextWatcher.onTextChanged("", 0, 0, 0);

        // アラーム時刻
        mTxtAlerm1 = (TextView) findViewById(R.id.txtAlerm1);
        mTxtAlerm2 = (TextView) findViewById(R.id.txtAlerm2);

        Set<String> timeSet = new TreeSet<>(preferences.getStringSet(JobcaaanService.PREF_ALERM_TIMES, new HashSet<String>()));
        int cnt = 0;
        for(String time: timeSet) {
            if(cnt == 0) {
                mTxtAlerm1.setText(time);
            } else if(cnt == 1) {
                mTxtAlerm2.setText(time);
            }
            cnt++;
        }

        // LoadingView
        mLoadingView = View.inflate(this, R.layout.layout_loading, null);

        // 位置情報取得ボタン
        ImageButton btnLocation = (ImageButton) findViewById(R.id.btnLocation);
        if (btnLocation != null) {
            btnLocation.setOnClickListener(mBtnLocationClickListener);
        }

        // 打刻ボタン
        Button btnStamp = (Button) findViewById(R.id.btnStamp);
        if (btnStamp != null) {
            btnStamp.setOnClickListener(mBtnStampClickListener);
        }

        // 打刻(位置取得有)ボタン
        Button btnStampWithLocate = (Button) findViewById(R.id.btnStampWithLocate);
        if (btnStampWithLocate != null) {
            btnStampWithLocate.setOnClickListener(mBtnStampWithLocateClickListener);
        }

        // 常駐ボタン
        Button btnResident = (Button) findViewById(R.id.btnResident);
        if (btnResident != null) {
            btnResident.setOnClickListener(mBtnResidentClickListener);
        }

        // 位置情報保存ボタン
        Button btnSaveLocation = (Button) findViewById(R.id.btnSaveLocation);
        if (btnSaveLocation != null) {
            btnSaveLocation.setOnClickListener(mBtnSaveLocationClickListener);
        }

        // 設定保存ボタン
        Button btnSaveSetting = (Button) findViewById(R.id.btnSaveSetting);
        if (btnSaveSetting != null) {
            btnSaveSetting.setOnClickListener(mBtnSaveSettingClickListener);
        }

        // XXX アラームはいずれ動的個数可能にしたい
        // アラーム１ボタン
        ImageButton btnAlerm1 = (ImageButton) findViewById(R.id.btnAlerm1);
        if (btnAlerm1 != null) {
            btnAlerm1.setTag(findViewById(R.id.txtAlerm1));
            btnAlerm1.setOnClickListener(mBtnAlermClickListener);
        }

        // アラーム２ボタン
        ImageButton btnAlerm2 = (ImageButton) findViewById(R.id.btnAlerm2);
        if (btnAlerm2 != null) {
            btnAlerm2.setTag(findViewById(R.id.txtAlerm2));
            btnAlerm2.setOnClickListener(mBtnAlermClickListener);
        }

        // アラーム１クリアボタン
        ImageButton btnAlerm1Clear = (ImageButton)findViewById(R.id.btnAlerm1Clear);
        if(btnAlerm1Clear != null) {
            btnAlerm1Clear.setTag(findViewById(R.id.txtAlerm1));
            btnAlerm1Clear.setOnClickListener(mBtnAlermClearClickListener);
        }

        // アラーム２クリアボタン
        ImageButton btnAlerm2Clear = (ImageButton)findViewById(R.id.btnAlerm2Clear);
        if(btnAlerm2Clear != null) {
            btnAlerm2Clear.setTag(findViewById(R.id.txtAlerm2));
            btnAlerm2Clear.setOnClickListener(mBtnAlermClearClickListener);
        }

        // アラーム保存ボタン
        Button btnSaveAlerm = (Button) findViewById(R.id.btnSaveAlerm);
        if (btnSaveAlerm != null) {
            btnSaveAlerm.setOnClickListener(mBtnSaveAlermClickListener);
        }

        if (BuildConfig.DEBUG && BuildConfig.FLAVOR.endsWith("develop")) {
            mTxtUserCode.setText(BuildConfig.DUMMY_USER_CODE);
            mTxtGroupId.setText(BuildConfig.DUMMY_GROUP_ID);
        }
    }

    @Override
    protected void onResume() {
        logger.debug("onResume");
        super.onResume();
        JobcaaanService.bindService(getApplicationContext(), mServiceConnection);
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
    }

    @Override
    protected void onPause() {
        logger.debug("onPause");
        JobcaaanService.unbindService(getApplicationContext(), mServiceConnection);
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
                if (contentRoot != null) {
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
            }
        });
    }

    private boolean validSetting() {
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

    private boolean validLocation() {
        boolean isError = false;
        final String latitude = mTxtLatitude.getText().toString().trim();
        final String longitude = mTxtLongitude.getText().toString().trim();
        final String address = mTxtAddress.getText().toString().trim();

        if (TextUtils.isEmpty(latitude) && !isNumeric(latitude)) {
            mTxtLatitude.setError(getString(R.string.error_field_required));
            mTxtLatitude.requestFocus();
            isError = true;
        }

        if (TextUtils.isEmpty(longitude) && !isNumeric(longitude)) {
            mTxtLongitude.setError(getString(R.string.error_field_required));
            mTxtLongitude.requestFocus();
            isError = true;
        }

        if (TextUtils.isEmpty(address)) {
            mTxtLatitude.setError(getString(R.string.error_invalid_location));
            mTxtLatitude.requestFocus();
            isError = true;
        }

        return !isError;
    }

    private void stamp(final boolean withLocate) {
        if (mJobcaaanService != null) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.dialog_confirm)
                    .setMessage("打刻してよろしいですか？")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            enableActivity(false);
                            mJobcaaanService.stamp(withLocate, new JobcaaanService.StampCallback() {
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

    public boolean isNumeric(String str) {
        try {
            @SuppressWarnings("unused")
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    class AlphaNumericFilter implements InputFilter {
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
