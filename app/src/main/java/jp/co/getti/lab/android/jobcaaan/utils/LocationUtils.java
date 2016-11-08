package jp.co.getti.lab.android.jobcaaan.utils;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationUtils {

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
                    for (int i = 1; i <= idx; i++) {
                        result.append(address.getAddressLine(i));
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
        }
        return (result.length() > 0) ? result.toString() : null;
    }
}
