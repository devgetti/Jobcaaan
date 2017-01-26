package jp.co.getti.lab.android.jobcaaan.fragment;

import android.support.v4.app.Fragment;

/**
 * タブ用Fragment基底
 */
public class AbstTabFragment extends Fragment {

    public interface OnInteractionListener {
        void onViewLock();

        void onViewUnlock();

        void onSelectedListItem(AbstTabFragment fragment, Object item);
    }
}
