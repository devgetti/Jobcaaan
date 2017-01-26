package jp.co.getti.lab.android.jobcaaan.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.ButterKnife;
import butterknife.OnClick;
import jp.co.getti.lab.android.jobcaaan.R;
import jp.co.getti.lab.android.jobcaaan.service.JobcaaanService;

/**
 * 操作フラグメント
 */
@SuppressWarnings("unused,WeakerAccess")
public class OperationTabFragment extends AbstTabFragment {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(OperationTabFragment.class);

    /** InteractionListener */
    private AbstTabFragment.OnInteractionListener mListener;

    /** Jobcaaanサービス */
    private JobcaaanService mJobcaaanService;

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

    public OperationTabFragment() {
    }

    public static OperationTabFragment newInstance() {
        OperationTabFragment fragment = new OperationTabFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        logger.debug("onCreate");
        super.onCreate(savedInstanceState);
        JobcaaanService.bindService(getContext(), mServiceConnection);
    }

    @Override
    public void onDestroy() {
        logger.debug("onDestroy");
        JobcaaanService.unbindService(getContext(), mServiceConnection);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        logger.debug("onCreateView");
        View view = inflater.inflate(R.layout.fragment_operation_tab, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

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

    @Override
    public void onDetach() {
        logger.debug("onDetach");
        super.onDetach();
        mListener = null;
    }

    /**
     * 打刻ボタンクリック
     *
     * @param v View
     */
    @OnClick(R.id.btnStamp)
    protected void btnStamp_onClick(View v) {
        logger.debug("mBtnStampClickListener - onClick");
        // バリデーション
        stamp(false);
    }

    /**
     * 打刻(位置取得有)ボタンクリック
     *
     * @param v View
     */
    @OnClick(R.id.btnStampWithLocate)
    protected void btnStampWithLocate_onClick(View v) {
        logger.debug("mBtnStampWithLocateClickListener - onClick");
        // バリデーション
        stamp(true);
    }

    /**
     * 常駐ボタンクリック
     *
     * @param v View
     */
    @OnClick(R.id.btnResident)
    protected void btnResident_onClick(View v) {
        logger.debug("mBtnResidentClickListener - onClick");
        if (mJobcaaanService != null) {
            if (mJobcaaanService.isResident()) {
                mJobcaaanService.stopResident();
            } else {
                mJobcaaanService.startResident();
            }
        }
    }

    /**
     * 打刻
     *
     * @param withLocate 位置情報取得有
     */
    private void stamp(final boolean withLocate) {
        if (mJobcaaanService != null) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.dialog_confirm)
                    .setMessage("打刻してよろしいですか？")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mListener.onViewLock();
                            mJobcaaanService.stamp(withLocate, new JobcaaanService.StampCallback() {
                                @Override
                                public void onFinish() {
                                    mListener.onViewUnlock();
                                }
                            });
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }
}
