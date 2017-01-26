package jp.co.getti.lab.android.jobcaaan.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import jp.co.getti.lab.android.jobcaaan.R;
import jp.co.getti.lab.android.jobcaaan.db.HistoryDataAccessor;
import jp.co.getti.lab.android.jobcaaan.model.History;
import jp.co.getti.lab.android.jobcaaan.view.DividerItemDecoration;

/**
 * 履歴Fragment
 */
@SuppressWarnings("unused,WeakerAccess")
public class HistoryTabFragment extends AbstTabFragment {

    public static final String ARG_COLUMN_COUNT = "column-count";

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(HistoryTabFragment.class);

    private int mColumnCount = 1;

    private AbstTabFragment.OnInteractionListener mListener;

    /**
     * コンストラクタ
     */
    public HistoryTabFragment() {
    }

    public static HistoryTabFragment newInstance() {
        HistoryTabFragment fragment = new HistoryTabFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, 1);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history_tab, container, false);
        View listView = view.findViewById(R.id.list);
        // Set the adapter
        if (listView instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) listView;
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            RecyclerView.Adapter adapter = new MyItemRecyclerViewAdapter(mListener);
            adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    logger.debug("onChanged!!!!!");
                    super.onChanged();
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount) {
                    logger.debug("onItemRangeChanged!!!!!");
                    super.onItemRangeChanged(positionStart, itemCount);
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                    logger.debug("onItemRangeChanged!!!!!");
                    super.onItemRangeChanged(positionStart, itemCount, payload);
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    logger.debug("onChanged!!!!!");
                    super.onItemRangeInserted(positionStart, itemCount);
                }

                @Override
                public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                    logger.debug("Override!!!!!");
                    super.onItemRangeMoved(fromPosition, toPosition, itemCount);
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    logger.debug("onItemRangeRemoved!!!!!");
                    super.onItemRangeRemoved(positionStart, itemCount);
                }
            });
            recyclerView.setAdapter(adapter);

            // 区切り線設定
            recyclerView.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL_LIST));
        }
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof AbstTabFragment.OnInteractionListener) {
            mListener = (AbstTabFragment.OnInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnListFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public class MyItemRecyclerViewAdapter extends RecyclerView.Adapter<MyItemRecyclerViewAdapter.ViewHolder> {

        private final List<History> mValues;
        private final AbstTabFragment.OnInteractionListener mListener;
        private final SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy/M/dd", Locale.JAPAN);
        private final SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss", Locale.JAPAN);


        public MyItemRecyclerViewAdapter(AbstTabFragment.OnInteractionListener listener) {
            //mValues = new ArrayList<>();    // TODO
            //mValues.add(new History(1, new Date(), "テスト", "打刻"));
            mValues = new HistoryDataAccessor(getContext()).select(null, null, null, 120, true);
            mListener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_history_list_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mItem = mValues.get(position);
            holder.mIdView.setText(String.format(Locale.JAPAN, "%4d", mValues.get(position).getId()));
            holder.mDateView.setText(sdfDate.format(mValues.get(position).getDateTime()));
            holder.mTimeView.setText(sdfTime.format(mValues.get(position).getDateTime()));
            holder.mTypeView.setText(mValues.get(position).getType());
            holder.mTitleView.setText(mValues.get(position).getTitle());

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mListener) {
                        // Notify the active callbacks interface (the activity, if the
                        // fragment is attached to one) that an item has been selected.
                        mListener.onSelectedListItem(HistoryTabFragment.this, holder.mItem);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final TextView mIdView;
            public final TextView mDateView;
            public final TextView mTimeView;
            public final TextView mTypeView;
            public final TextView mTitleView;
            public History mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mIdView = (TextView) view.findViewById(R.id.txtId);
                mDateView = (TextView) view.findViewById(R.id.txtDate);
                mTimeView = (TextView) view.findViewById(R.id.txtTime);
                mTypeView = (TextView) view.findViewById(R.id.txtType);
                mTitleView = (TextView) view.findViewById(R.id.txtTitle);
            }
        }
    }
}
