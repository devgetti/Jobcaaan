package jp.co.getti.lab.android.jobcaaan.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

import butterknife.Bind;
import butterknife.ButterKnife;
import jp.co.getti.lab.android.jobcaaan.R;
import jp.co.getti.lab.android.jobcaaan.fragment.AbstTabFragment;
import jp.co.getti.lab.android.jobcaaan.fragment.HistoryTabFragment;
import jp.co.getti.lab.android.jobcaaan.fragment.OperationTabFragment;
import jp.co.getti.lab.android.jobcaaan.fragment.SettingTabFragment;
import jp.co.getti.lab.android.jobcaaan.service.JobcaaanService;
import jp.co.getti.lab.android.jobcaaan.utils.RequestPermissionHelper;

/**
 * メインTabActivity
 */
@SuppressWarnings("unused,WeakerAccess")
public class MainTabActivity extends AppCompatActivity implements AbstTabFragment.OnInteractionListener {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(MainTabActivity.class);

    /** リクエストコード　パーミッション */
    private static final int REQUEST_CODE_PERMISSION = 100;

    /** ツールバー */
    @Bind(R.id.toolbar)
    protected Toolbar mToolbar;

    /** タブ */
    @Bind(R.id.tabs)
    protected TabLayout mTabLayout;

    /** ViewPager */
    @Bind(R.id.viewPager)
    protected ViewPager mViewPager;

    /** ローディングView */
    protected View mLoadingView;

    /** パーミッションリクエストHelper */
    private RequestPermissionHelper mRequestPermissionHelper;

    /** {@inheritDoc} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_tab);

        // Viewバインド
        ButterKnife.bind(this);

        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        mViewPager.setAdapter(new TabPagerAdapter(getSupportFragmentManager()));
        mViewPager.setOffscreenPageLimit(1);
        mTabLayout.setupWithViewPager(mViewPager);

        // LoadingView
        mLoadingView = View.inflate(this, R.layout.layout_loading, null);

        // ====== サービス起動 ==================
        JobcaaanService.startService(getApplicationContext());

        // ====== リソース類初期化 ==================
        mRequestPermissionHelper = new RequestPermissionHelper(this, REQUEST_CODE_PERMISSION);
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        logger.debug("onResume");
        super.onResume();
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

    /** {@inheritDoc} */
    @Override
    protected void onPause() {
        logger.debug("onPause");
        super.onPause();
    }

    /** {@inheritDoc} */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        logger.debug("onRequestPermissionsResult");
        mRequestPermissionHelper.setResult(requestCode, permissions, grantResults);
    }

    /** {@inheritDoc} */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        logger.debug("onActivityResult");
    }

    /** {@inheritDoc} */
    @Override
    public void onViewLock() {
        enableActivity(false);
    }

    /** {@inheritDoc} */
    @Override
    public void onViewUnlock() {
        enableActivity(true);
    }

    /** {@inheritDoc} */
    @Override
    public void onSelectedListItem(AbstTabFragment fragment, Object item) {
    }

    /**
     * 画面有効/無効切り替え
     *
     * @param enable true:有効
     */
    private void enableActivity(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup contentRoot = (ViewGroup) MainTabActivity.this.findViewById(android.R.id.content);
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

    /**
     * Toast表示
     *
     * @param message メッセージ
     */
    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Tabページ列挙体
     */
    private enum TabPage {

        /** 操作 */
        Operation("操作", new Callable<AbstTabFragment>() {
            @Override
            public AbstTabFragment call() throws Exception {
                return OperationTabFragment.newInstance();
            }
        }),

        /** 設定 */
        Setting("設定", new Callable<AbstTabFragment>() {
            @Override
            public AbstTabFragment call() throws Exception {
                return SettingTabFragment.newInstance();
            }
        }),

        /** 履歴 */
        History("履歴", new Callable<AbstTabFragment>() {
            @Override
            public AbstTabFragment call() throws Exception {
                return HistoryTabFragment.newInstance();
            }
        });

        /** タイトル */
        private String mTitle;

        /** Fragment作成ロジック */
        private Callable<AbstTabFragment> mCreateFragmentLogic;

        /**
         * コンストラクタ
         *
         * @param title               タイトル
         * @param createFragmentLogic Fragment作成ロジック
         */
        TabPage(String title, Callable<AbstTabFragment> createFragmentLogic) {
            mTitle = title;
            mCreateFragmentLogic = createFragmentLogic;
        }

        /**
         * タイトルを取得する。
         *
         * @return タイトル
         */
        public String getTitle() {
            return mTitle;
        }

        /**
         * Fragmentを作成する
         *
         * @param params パラメータ群
         * @return Fragmentインスタンス
         */
        public AbstTabFragment createFragment(Object... params) {
            try {
                return mCreateFragmentLogic.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * タブPagerAdapter
     */
    public class TabPagerAdapter extends FragmentPagerAdapter {

        public TabPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return TabPage.values()[position].createFragment();
        }

        @Override
        public int getCount() {
            return TabPage.values().length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return TabPage.values()[position].getTitle();
        }
    }
}
