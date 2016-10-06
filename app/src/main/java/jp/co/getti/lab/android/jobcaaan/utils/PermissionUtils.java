package jp.co.getti.lab.android.jobcaaan.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * パーミッションUtils
 */
public class PermissionUtils {

    /**
     * AndroidManifest上に記載されたアプリに必要なパーミッションの一覧を取得する
     *
     * @return パーミッション一覧
     */
    public static String[] getPredefinedPermissions(Context context) {
        String[] ret;
        if (context != null) {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo;
            try {
                packageInfo = packageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
            ret = packageInfo.requestedPermissions;
        } else {
            ret = new String[0];
        }
        return ret;
    }

    /**
     * 未付与のパーミッションリストを取得する。
     *
     * @param context コンテキスト
     * @return 未付与パーミッションリスト
     */
    @TargetApi(Build.VERSION_CODES.M)
    public static String[] getUnauthorisedPermissions(@NonNull Context context) {
        List<String> ret = new ArrayList<>();
        String[] permissionList = getPredefinedPermissions(context);
        if (permissionList != null) {
            for (String permission : permissionList) {
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    // 許可されていなければ、権限なしリストに追加
                    ret.add(permission);
                }
            }
        }
        return ret.toArray(new String[ret.size()]);
    }
}
