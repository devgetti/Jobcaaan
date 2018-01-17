package jp.co.getti.lab.android.jobcaaan.utils;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationUtils {

    /**
     * 緯度経度から住所文字列を取得する。
     *
     * @param context   コンテキスト
     * @param latitude  緯度
     * @param longitude 経度
     * @return 住所文字列
     */
    public static String getAddressInJapan(Context context, double latitude, double longitude) {

        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        List<Address> addresses;
        StringBuilder result = new StringBuilder();

        try {
            addresses = geocoder.getFromLocation(latitude, longitude, 1);

            for (Address address : addresses) {
                if ("JP".equals(address.getCountryCode())) {
                    int idx = address.getMaxAddressLineIndex();
                    // 1番目のレコードは国名のため省略
                    for (int i = 0; i <= idx; i++) {
                        result.append(address.getAddressLine(i));
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
        }
        return (result.length() > 0) ? result.toString() : null;
    }

    /**
     * 緯度経度から住所文字列を取得する。(非同期)
     *
     * @param context   コンテキスト
     * @param latitude  緯度
     * @param longitude 経度
     * @param callback  コールバック
     */
    public static void getAddressInJapan(final Context context, final double latitude, final double longitude, final Callback callback) {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                callback.onSuccess(getAddressInJapan(context, latitude, longitude));
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * コールバック
     */
    public interface Callback {
        /**
         * 成功時
         *
         * @param address 住所文字列
         */
        void onSuccess(String address);
    }
}
