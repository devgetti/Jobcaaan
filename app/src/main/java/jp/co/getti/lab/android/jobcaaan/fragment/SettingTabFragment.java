package jp.co.getti.lab.android.jobcaaan.fragment;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import jp.co.getti.lab.android.jobcaaan.utils.viewbinder.Bind;
import jp.co.getti.lab.android.jobcaaan.utils.viewbinder.OnClick;
import jp.co.getti.lab.android.jobcaaan.utils.viewbinder.SimpleViewBinderUtils;
import jp.co.getti.lab.android.jobcaaan.view.AutoResizeTextView;

@SuppressWarnings("unused,WeakerAccess")
public class SettingTabFragment extends AbstTabFragment {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(SettingTabFragment.class);

    /** リクエストコード　位置情報精度 */
    private static final int REQUEST_LOCATION_ACCURACY = 101;

    /** ユーザコードView */
    @Bind(R.id.txtUserCode)
    protected EditText mTxtUserCode;

    /** グループIDView */
    @Bind(R.id.txtGroupId)
    protected EditText mTxtGroupId;

    /** 緯度View */
    @Bind(R.id.txtLatitude)
    protected EditText mTxtLatitude;

    /** 経度View */
    @Bind(R.id.txtLongitude)
    protected EditText mTxtLongitude;

    /** 住所View */
    @Bind(R.id.txtAddress)
    protected TextView mTxtAddress;

    /** アラーム１TextView */
    @Bind(R.id.txtAlerm1)
    protected AutoResizeTextView mTxtAlerm1;

    /** アラーム２TextView */
    @Bind(R.id.txtAlerm2)
    protected AutoResizeTextView mTxtAlerm2;

    /** InteractionListener */
    private AbstTabFragment.OnInteractionListener mListener;

    /** Jobcaaanサービス */
    private JobcaaanService mJobcaaanService;

    /** 位置情報精度リクエストHelper */
    private RequestLocationAccuracyHelper mRequestLocationAccuracyHelper;

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
                //String address = LocationUtils.getAddressInJapan(getActivity(), Double.parseDouble(strLat), Double.parseDouble(strLong));
                LocationUtils.getAddressInJapan(getActivity(), Double.parseDouble(strLat), Double.parseDouble(strLong), new LocationUtils.Callback() {
                    @Override
                    public void onSuccess(final String result) {
                        Activity activity = getActivity();
                        if(activity != null) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mTxtAddress.setText((result != null) ? result : "");
                                }
                            });
                        }
                    }
                });
            } else {
                mTxtAddress.setText("");
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    /**
     * コンストラクタ
     */
    public SettingTabFragment() {
    }

    /**
     * インスタンス生成
     *
     * @return Fragmentインスタンス
     */
    public static SettingTabFragment newInstance() {
        SettingTabFragment fragment = new SettingTabFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        logger.debug("onCreate");
        super.onCreate(savedInstanceState);
        JobcaaanService.bindService(getContext(), mServiceConnection);

        // ====== リソース類初期化 ==================
        mRequestLocationAccuracyHelper = new RequestLocationAccuracyHelper(getActivity(), REQUEST_LOCATION_ACCURACY);
    }

    /** {@inheritDoc} */
    @Override
    public void onDestroy() {
        logger.debug("onDestroy");
        JobcaaanService.unbindService(getContext(), mServiceConnection);
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        logger.debug("onCreateView");
        View view = inflater.inflate(R.layout.fragment_setting_tab, container, false);
        SimpleViewBinderUtils.bind(this, view);

        // ======= View初期化 =================
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        // ユーザコード
        mTxtUserCode.setText(preferences.getString(JobcaaanService.PREF_USER_CODE, ""));
        mTxtUserCode.setFilters(new InputFilter[]{new AlphaNumericFilter()});

        // グループID
        mTxtGroupId.setText(preferences.getString(JobcaaanService.PREF_GROUP_ID, ""));

        // 緯度、経度、住所
        mTxtLatitude.setText(preferences.getString(JobcaaanService.PREF_LATITUDE, ""));
        mTxtLongitude.setText(preferences.getString(JobcaaanService.PREF_LONGITUDE, ""));

        mTxtLatitude.addTextChangedListener(mLocationTextWatcher);
        mTxtLongitude.addTextChangedListener(mLocationTextWatcher);

        mLocationTextWatcher.onTextChanged("", 0, 0, 0);

        // アラームボタンとテキストの関連付け
        view.findViewById(R.id.btnAlerm1).setTag(mTxtAlerm1);
        view.findViewById(R.id.btnAlerm1Clear).setTag(mTxtAlerm1);
        view.findViewById(R.id.btnAlerm2).setTag(mTxtAlerm2);
        view.findViewById(R.id.btnAlerm2Clear).setTag(mTxtAlerm2);

        // アラーム時刻
        Set<String> timeSet = new TreeSet<>(preferences.getStringSet(JobcaaanService.PREF_ALERM_TIMES, new HashSet<String>()));
        int cnt = 0;
        for (String time : timeSet) {
            if (cnt == 0) {
                mTxtAlerm1.setText(time);
            } else if (cnt == 1) {
                mTxtAlerm2.setText(time);
            }
            cnt++;
        }

        if (BuildConfig.DEBUG && BuildConfig.FLAVOR.endsWith("develop")) {
            mTxtUserCode.setText(BuildConfig.DUMMY_USER_CODE);
            mTxtGroupId.setText(BuildConfig.DUMMY_GROUP_ID);
        }
        return view;
    }

    /** {@inheritDoc} */
    @Override
    public void onAttach(Context context) {
        logger.debug("onAttach");
        super.onAttach(context);
        if (context instanceof AbstTabFragment.OnInteractionListener) {
            mListener = (AbstTabFragment.OnInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnFragmentInteractionListener");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onDetach() {
        logger.debug("onDetach");
        super.onDetach();
        mListener = null;
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        logger.debug("onActivityResult");
        mRequestLocationAccuracyHelper.setResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 設定保存ボタンクリック
     *
     * @param v View
     */
    @OnClick(R.id.btnSaveSetting)
    protected void btnSaveSetting_onClick(View v) {
        logger.debug("mBtnSaveSettingClickListener - onClick");
        if (mJobcaaanService != null && validSetting()) {
            String userCode = mTxtUserCode.getText().toString();
            String groupId = mTxtGroupId.getText().toString();
            mJobcaaanService.saveSetting(userCode, groupId);
        }
    }

    /**
     * 位置取得ボタンクリック
     *
     * @param v View
     */
    @OnClick(R.id.btnLocation)
    protected void btnLocation_onClick(View v) {
        // 位置精度設定チェック
        mRequestLocationAccuracyHelper.checkAndRequest(new RequestLocationAccuracyHelper.Callback() {

            private LocationListener mLocationListener;

            @Override
            public void onResult(final boolean result) {
                logger.debug("mRequestLocationAccuracyHelper - onResult");
                if (!result) {
                    showToast(getString(R.string.error_required_location_accuracy));
                } else {
                    // 位置情報取得
                    mLocationListener = new LocationListener(getContext(), new ILocationListenerStrategy() {
                        @Override
                        public void onDataReceived(long time, final Location location) {
                            getActivity().runOnUiThread(new Runnable() {
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

    /**
     * 位置情報保存ボタンクリック
     *
     * @param v View
     */
    @OnClick(R.id.btnSaveLocation)
    protected void btnSaveLocation_onClick(View v) {
        logger.debug("mBtnSaveLocationClickListener - onClick");
        if (mJobcaaanService != null && validLocation()) {
            double latitude = Double.parseDouble(mTxtLatitude.getText().toString().trim());
            double longitude = Double.parseDouble(mTxtLongitude.getText().toString().trim());
            mJobcaaanService.saveLocation(latitude, longitude);
        }
    }


    /**
     * アラーム設定ボタンクリック
     *
     * @param v View
     */
    @OnClick({R.id.btnAlerm1, R.id.btnAlerm2})
    protected void onClick(View v) {

        final AutoResizeTextView targetText = (AutoResizeTextView) v.getTag();
        if (targetText != null) {
            Calendar calendar = Calendar.getInstance();
            int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            TimePickerDialog dialog = new TimePickerDialog(getActivity(),
                    new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                            targetText.setText(String.format(Locale.JAPAN, "%02d:%02d", hourOfDay, minute));
                            targetText.resizeText();
                        }
                    }, hourOfDay, minute, true);
            dialog.show();
        }
    }

    /**
     * アラーム設定クリアボタンクリック
     *
     * @param v View
     */
    @OnClick({R.id.btnAlerm1Clear, R.id.btnAlerm2Clear})
    protected void btnAlermClear_onClick(View v) {
        AutoResizeTextView targetText = (AutoResizeTextView) v.getTag();
        targetText.setText("");
    }

    /**
     * アラーム保存ボタンクリック
     *
     * @param v View
     */
    @OnClick(R.id.btnSaveAlerm)
    public void btnSaveAlerm_onClick(View v) {
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

    /**
     * 設定値Validation
     *
     * @return true:全て正常
     */
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

    /**
     * 位置情報値Validation
     *
     * @return true:全て正常
     */
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

//        if (TextUtils.isEmpty(address)) {
//            mTxtLatitude.setError(getString(R.string.error_invalid_location));
//            mTxtLatitude.requestFocus();
//            isError = true;
//        }

        return !isError;
    }

    private void showToast(final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isNumeric(String str) {
        try {
            @SuppressWarnings("unused")
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    class AlphaNumericFilter implements InputFilter {
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {

            if (source.toString().matches("^[a-zA-Z0-9]+$")) {
                return source;
            } else {
                return "";
            }
        }
    }
}
