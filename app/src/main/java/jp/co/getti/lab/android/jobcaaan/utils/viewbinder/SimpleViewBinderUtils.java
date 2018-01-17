package jp.co.getti.lab.android.jobcaaan.utils.viewbinder;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 簡易ViewBinder Utils
 * <pre>
 *     Viewとロジッククラス(Activity、Fragment)との簡易的なViewBindを行うUtility。
 *
 *     設定方法や動作はButterKnifeを参考にしている。
 *     (そもそも本パッケージを作成に至ったのは
 *     ButterKnifeにはJaccによるJava8対応を行うと使用できない問題があり、その代替案として実装。
 *     Jaccを利用するとclass->jacc->dexというクラスファイルの変換が行われる際ために、
 *     ButterKnifeの静的なViewBind情報クラスが消えてしまうらしい。)
 *     ButterKnifeはアノテーションプロセッサにより、事前にBind情報をクラス化して保存することで、
 *     多くの機能や速度なりを保っているが、そのあたりを省いた簡易的な実装としている。
 *     ・メソッド実行時の評価はRuntimeで行われる。
 *     ・メソッド引数などに制限があり、基本は各種Listenerメソッドと同じ引数で定義する必要がある。
 *     ・Listenerメソッドが複数あるケースには未対応。
 *     　(例:OnItemSelectedListenerにはonItemSelectedとonNothingSelectedの二つのメソッドがあるが、
 *     　　同じViewにはどちらか一方のメソッドにしかBindされない。)
 *
 *     ViewBindを行うことで、各種アノテーションを付与したフィールドまたはメソッドと
 *     各View要素(TextViewやButton)とのBindが行われる。
 *
 *     使用例：
 *     ActivityでのOnCreate時
 *          SimpleViewBinderUtils.bind(**Activity.this);
 *     FragmentでのOnCreateView時
 *          View view = View.infrate(R.layout.****);
 *          SimpleViewBinderUtils.bind(**Fragment.this, view);
 * </pre>
 */
public class SimpleViewBinderUtils {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(SimpleViewBinderUtils.class);

    /**
     * ViewBindを実施する。
     *
     * @param activity Activity
     */
    public static void bind(Activity activity) {
        if (activity != null) {
            bind(activity, activity.findViewById(android.R.id.content));
        }
    }

    /**
     * ViewBindを実施する。
     *
     * @param target Bind対象
     * @param view   view
     */
    public static void bind(Object target, View view) {
        if (target != null && view != null) {
            // フィールドBind
            bindFields(target, view);

            // メソッドBind
            bindMethods(target, view);
        }
    }

    /**
     * フィールドBind
     *
     * @param target Bind対象
     * @param view   View
     */
    private static void bindFields(Object target, View view) {
        Context context = getContext(target);
        logger.debug("フィールドとViewのBindを開始します。対象={}#{}", target.getClass().getSimpleName(), getResourceName(context, view.getId()));
        try {
            // 対象のフィールド群を取得
            Field[] fieldList = target.getClass().getDeclaredFields();
            for (Field f : fieldList) {
                f.setAccessible(true);
                // Bindアノテーションを持つフィールドを抽出
                if (f.getAnnotation(Bind.class) != null) {
                    Bind element = f.getAnnotation(Bind.class);

                    // Viewを対象フィールドに設定
                    f.set(target, view.findViewById(element.value()));

                    logger.debug("-フィールドBindしました。対象={} フィールド={}#{}"
                            , getResourceName(context, element.value()), target.getClass().getSimpleName(), f.getName());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.debug("フィールドとViewのBindを終了します。対象={}#{}", target.getClass().getSimpleName(), getResourceName(context, view.getId()));
    }

    /**
     * メソッドBind
     *
     * @param target Bind対象
     * @param view   View
     */
    private static void bindMethods(Object target, View view) {
        Context context = getContext(target);
        logger.debug("メソッドとViewのBindを開始します。対象={}#{}", target.getClass().getSimpleName(), getResourceName(context, view.getId()));
        try {
            // 対象のメソッド群を取得
            Method[] methods = target.getClass().getDeclaredMethods();
            for (final Method method : methods) {
                // 各種メソッド用アノテーションを持つフィールドを抽出し、Bindする。
                // 各BindロジックはTargetMethod列挙体にて定義
                for (BindTargetMethod bindTargetMethod : BindTargetMethod.values()) {
                    Annotation annotation = method.getAnnotation(bindTargetMethod.annotationClass);
                    if (annotation != null) {
                        int[] resIds = bindTargetMethod.bindLogic.getResIdsFromAnnotation(annotation);
                        for (int resId : resIds) {
                            // メソッドBind
                            bindTargetMethod.bindLogic.bindMethod(view, resId, target, method);

                            logger.debug("-メソッドBindしました。対象={}#{} メソッド={}#{}"
                                    , getResourceName(getContext(target), resId), bindTargetMethod.annotationClass.getSimpleName(), target.getClass().getSimpleName(), method.getName());
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        logger.debug("メソッドとViewのBindを終了します。対象={}#{}", target.getClass().getSimpleName(), getResourceName(context, view.getId()));
    }

    /**
     * メソッド呼び出し
     *
     * @param target Bindするオブジェクト
     * @param method Bindするオブジェクトの呼び出すメソッド定義
     * @return 戻り値
     */
    private static Object invokeMethod(Object target, Method method, Object[] srcArgs) throws Throwable {
        // アクセス許可
        method.setAccessible(true);

        // Bindするメソッドの引数型取得
        Class<?>[] paramTypes = method.getParameterTypes();

        // Listenerの引数をBindするメソッドに渡すための引数配列作成
        int argLength = (srcArgs.length > paramTypes.length) ? paramTypes.length : srcArgs.length;
        Object dstArgs[] = new Object[argLength];
        System.arraycopy(srcArgs, 0, dstArgs, 0, argLength);

        logger.debug("VierBindメソッド実行開始 {}#{}", target.getClass().getSimpleName(), method.getName());
        long timeStart = System.currentTimeMillis();
        Object ret = method.invoke(target, dstArgs);
        long timeEnd = System.currentTimeMillis();
        logger.debug("VierBindメソッド実行終了 {}#{} 経過時間={}ms", target.getClass().getSimpleName(), method.getName(), timeEnd - timeStart);
        return ret;
    }

    /**
     * 対象オブジェクトからコンテキストを取得する。
     *
     * @param target 対象
     * @return コンテキスト
     */
    private static Context getContext(Object target) {
        Context context = null;
        if (target instanceof Context) {
            context = (Context) target;
        } else if (target instanceof Fragment && ((Fragment) target).getActivity() != null) {
            context = ((Fragment) target).getActivity();
        } else if (target instanceof android.support.v4.app.Fragment && ((android.support.v4.app.Fragment) target).getActivity() != null) {
            context = ((android.support.v4.app.Fragment) target).getActivity();
        }
        return context;
    }

    /**
     * リソース名を取得する。
     *
     * @param context    コンテキスト
     * @param resourceId リソースID
     * @return リソース名
     */
    private static String getResourceName(Context context, int resourceId) {
        return ((context != null && resourceId != -1) ? context.getResources().getResourceEntryName(resourceId) : "");
    }

    /**
     * Bind対象メソッド列挙体
     */
    private enum BindTargetMethod {
        OnClick(OnClick.class, new IBindLogic() {
            @Override
            public void bindMethod(View view, int resId, final Object target, final Method method) {
                if (view != null && target != null && method != null) {
                    View bindView = view.findViewById(resId);
                    if (bindView != null) {
                        bindView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                try {
                                    invokeMethod(target, method, new Object[]{view});
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }
                }
            }

            @Override
            public int[] getResIdsFromAnnotation(Annotation annotation) {
                return (annotation != null && annotation instanceof OnClick) ? ((OnClick) annotation).value() : new int[0];
            }
        }),
        OnCheckedChanged(OnCheckedChanged.class, new IBindLogic() {
            @Override
            public void bindMethod(View view, int resId, final Object target, final Method method) {
                if (view != null && target != null && method != null) {
                    View bindView = view.findViewById(resId);
                    if (bindView != null && bindView instanceof CompoundButton) {
                        ((CompoundButton) bindView).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                                try {
                                    invokeMethod(target, method, new Object[]{compoundButton, b});
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }
                }
            }

            @Override
            public int[] getResIdsFromAnnotation(Annotation annotation) {
                return (annotation != null && annotation instanceof OnCheckedChanged) ? ((OnCheckedChanged) annotation).value() : new int[0];
            }
        }),
        OnEditorAction(OnEditorAction.class, new IBindLogic() {
            @Override
            public void bindMethod(View view, int resId, final Object target, final Method method) {
                if (view != null && target != null && method != null) {
                    View bindView = view.findViewById(resId);
                    if (bindView != null && bindView instanceof TextView) {
                        ((TextView) bindView).setOnEditorActionListener(new TextView.OnEditorActionListener() {
                            @Override
                            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                                boolean ret = false;
                                try {
                                    Object result = invokeMethod(target, method, new Object[]{textView, i, keyEvent});
                                    if (result instanceof Boolean) {
                                        ret = (Boolean) result;
                                    }
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                                return ret;
                            }
                        });
                    }
                }
            }

            @Override
            public int[] getResIdsFromAnnotation(Annotation annotation) {
                return (annotation != null && annotation instanceof OnEditorAction) ? ((OnEditorAction) annotation).value() : new int[0];
            }
        }),
        OnItemSelected(OnItemSelected.class, new IBindLogic() {
            @Override
            public void bindMethod(View view, int resId, final Object target, final Method method) {
                if (view != null && target != null && method != null) {
                    View bindView = view.findViewById(resId);
                    if (bindView != null && bindView instanceof AdapterView) {
                        final AdapterView.OnItemSelectedListener orgListener = ((AdapterView) bindView).getOnItemSelectedListener();
                        ((AdapterView) bindView).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                                try {
                                    invokeMethod(target, method, new Object[]{adapterView, view, i, l});
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> adapterView) {
                                if (orgListener != null) {
                                    orgListener.onNothingSelected(adapterView);
                                }
                            }
                        });
                    }
                }
            }

            @Override
            public int[] getResIdsFromAnnotation(Annotation annotation) {
                return (annotation != null && annotation instanceof OnItemSelected) ? ((OnItemSelected) annotation).value() : new int[0];
            }
        });

        /** アノテーションClass */
        @SuppressWarnings("unused")
        private Class<? extends Annotation> annotationClass;

        /** Binderインターフェース */
        private IBindLogic bindLogic;

        /**
         * コンストラクタ
         *
         * @param annotationClass アノテーションClass
         * @param bindLogic       Binderインターフェース
         */
        BindTargetMethod(Class<? extends Annotation> annotationClass, IBindLogic bindLogic) {
            this.annotationClass = annotationClass;
            this.bindLogic = bindLogic;
        }

        /**
         * Bindロジックインターフェース
         */
        public interface IBindLogic {

            /**
             * Bind実行
             *
             * @param view   対象View
             * @param resId  対象リソースID
             * @param target Bind先オブジェクト
             * @param method Bind先メソッド
             */
            void bindMethod(View view, int resId, Object target, Method method);

            /**
             * 対象リソースID群取得
             *
             * @param annotation アノテーション
             * @return リソースID群
             */
            int[] getResIdsFromAnnotation(Annotation annotation);
        }
    }
}