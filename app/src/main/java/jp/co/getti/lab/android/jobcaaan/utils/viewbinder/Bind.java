package jp.co.getti.lab.android.jobcaaan.utils.viewbinder;

import android.view.View;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ViewBinderアノテーション　フィールド用
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Bind {

    /** リソースID */
    int value() default View.NO_ID;
}
