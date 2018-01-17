package jp.co.getti.lab.android.jobcaaan.utils.viewbinder;

import android.view.View;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

/**
 * ViewBinderアノテーション　OnClick
 */
@Target(METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnClick {

    /** リソースID配列 */
    int[] value() default {View.NO_ID};
}