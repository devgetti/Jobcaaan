package jp.co.getti.lab.android.jobcaaan.utils;

import android.location.Location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.CookieManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * JobcanWebクライアント
 */
public class JobcanWebClient {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "ja,en-US;q=0.8,en;q=0.6";

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(JobcanWebClient.class);

    /** HttpClient */
    private OkHttpClient client;

    public JobcanWebClient() {
        try {
            // http://stackoverflow.com/questions/34881775/automatic-cookie-handling-with-okhttp-3
//            CookieJar cookieJar = new CookieJar() {
//                private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();
//
//                @Override
//                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
//                    cookieStore.put(url.host(), cookies);
//                }
//
//                @Override
//                public List<Cookie> loadForRequest(HttpUrl url) {
//                    List<Cookie> cookies = cookieStore.get(url.host());
//                    return cookies != null ? cookies : new ArrayList<Cookie>();
//                }
//            };

            // 自己署名許可HttpClient作成
            X509TrustManager tm = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{tm}, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                @Override
                public void log(String message) {
                    logger.debug(message);
                }
            });
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            client = new OkHttpClient().newBuilder().sslSocketFactory(sslSocketFactory, tm)
                    //.retryOnConnectionFailure(true)
                    .addInterceptor(logging)
                    .cookieJar(new JavaNetCookieJar(new CookieManager()))
                    //.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8889)))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .hostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    })
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void loginMobile(String userCode, Callback callback) {
        // ログインページへ
        Request request = new Request.Builder()
                .url("https://ssl.jobcan.jp/login/employee?code=" + userCode)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .build();
        client.newCall(request).enqueue(callback);
    }

    public void stamp(String groupId, Date date, Callback callback) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        RequestBody body = new FormBody.Builder()
                .add("lon", "-1")
                .add("lat", "-1")
                .add("year", Integer.toString(cal.get(Calendar.YEAR)))
                .add("month", Integer.toString(cal.get(Calendar.MONTH) + 1))
                .add("day", Integer.toString(cal.get(Calendar.DATE)))
                .add("reason", "")
                .add("time", "")
                .add("group_id", groupId)
                .add("position_id", "")
                .add("adit_item", "打刻")
                .add("yakin", "")
                .build();
        Request request = new Request.Builder()
                .url("https://ssl.jobcan.jp/m/work/stamp-save-smartphone/")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .build();
        client.newCall(request).enqueue(callback);
    }

    public void stamp(String groupId, Date date, Location location, Callback callback) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        RequestBody body = new FormBody.Builder()
                .add("confirm", "はい")
                .add("lon", Double.toString(location.getLongitude()))
                .add("lat", Double.toString(location.getLatitude()))
                .add("year", Integer.toString(cal.get(Calendar.YEAR)))
                .add("month", Integer.toString(cal.get(Calendar.MONTH) + 1))
                .add("day", Integer.toString(cal.get(Calendar.DATE)))
                .add("reason", "")
                .add("time", "")
                .add("group_id", groupId)
                .add("position_id", "")
                .add("adit_item", "打刻")
                .add("yakin", "")
                .build();
        Request request = new Request.Builder()
                .url("https://ssl.jobcan.jp/m/work/stamp-save-smartphone/")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .build();
        client.newCall(request).enqueue(callback);
    }

    public void stampFlow(final String userCode, final String groupId, final Date date, final Location location, final ResultCallback resultCallback) {
        // ログイン
        loginMobile(userCode, new Callback() {
            private void errorLogic(final String message) {
                if (resultCallback != null) {
                    resultCallback.onError(message);
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                errorLogic(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    if (!body.contains("認証エラー")) {
                        stamp(groupId, date, location, new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                errorLogic(e.getMessage());
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                if (response.isSuccessful()) {
                                    String body = response.body().string();
                                    if (body.contains("打刻完了")) {
                                        // 打刻完了時
                                        if (resultCallback != null) {
                                            resultCallback.onSuccess();
                                        }
                                    } else {
                                        errorLogic("stamp error");
                                    }
                                } else {
                                    errorLogic("stamp response:" + response.code());
                                }
                            }
                        });
                    } else {
                        errorLogic("login error");
                    }
                } else {
                    errorLogic("login response:" + response.code());
                }
            }
        });
    }

    public interface ResultCallback {
        void onSuccess();

        void onError(String msg);
    }


//    public static void main(String[] argv) {
//
//        OkHttpClient client = null;
//        try {
//            // http://stackoverflow.com/questions/34881775/automatic-cookie-handling-with-okhttp-3
//            CookieJar cookieJar = new CookieJar() {
//                private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();
//
//                @Override
//                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
//                    cookieStore.put(url.host(), cookies);
//                }
//
//                @Override
//                public List<Cookie> loadForRequest(HttpUrl url) {
//                    List<Cookie> cookies = cookieStore.get(url.host());
//                    return cookies != null ? cookies : new ArrayList<Cookie>();
//                }
//            };
//            // 自己署名許可HttpClient作成
//            X509TrustManager tm = new X509TrustManager() {
//                @Override
//                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
//                }
//
//                @Override
//                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
//                }
//
//                @Override
//                public X509Certificate[] getAcceptedIssuers() {
//                    return new X509Certificate[0];
//                }
//            };
//            final SSLContext sslContext = SSLContext.getInstance("SSL");
//            sslContext.init(null, new TrustManager[] { tm }, new java.security.SecureRandom());
//            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
//
//            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
//                @Override
//                public void log(String message) {
//                    System.out.println(message);
//                }
//            });
//            logging.setLevel(Level.BASIC);
//
//            client = new OkHttpClient().newBuilder().sslSocketFactory(sslSocketFactory, tm)
//                    //.retryOnConnectionFailure(true)
//                    .addInterceptor(logging)
//                    .cookieJar(cookieJar)
//                    .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8889)))
//                    .connectTimeout(10, TimeUnit.SECONDS)
//                    .readTimeout(10, TimeUnit.SECONDS)
//                    .followRedirects(false)
//                    .hostnameVerifier(new HostnameVerifier() {
//                        @Override
//                        public boolean verify(String hostname, SSLSession session) {
//                            return true;
//                        }
//                    })
//                    .build();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        //		try {
//        //			Request request = new Request.Builder()
//        //					.url("http://www.google.co.jp")
//        //					.get()
//        //					.build();
//        //			Response response = client.newCall(request).execute();
//        //		} catch (IOException e) {
//        //			e.printStackTrace();
//        //		}
//
//        // ログインページへ
//        try {
//
//            //			GET https://ssl.jobcan.jp/login/employee?code=557748ec07ede4771c90186a56491625 HTTP/1.1
//            //			Connection: keep-alive
//            //			Upgrade-Insecure-Requests: 1
//            //			User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36
//            //			Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8
//            //			Referer: https://ssl.jobcan.jp/client/employee
//            //			Accept-Encoding: sdch, br
//            //			Accept-Language: ja,en-US;q=0.8,en;q=0.6
//            //			Cookie: sid=j9catagngdagd4263amp8ouiq6; _ga=GA1.2.1852776987.1475456741; _mkto_trk=id:869-PHA-420&token:_mch-ssl.jobcan.jp-1475456745215-66911; __utma=237666178.1852776987.1475456741.1475456741.1475456741.1; __utmb=237666178.83.10.1475456741; __utmc=237666178; __utmz=237666178.1475456741.1.1.utmcsr=jobcan.ne.jp|utmccn=(referral)|utmcmd=referral|utmcct=/; __zlcmid=cvftchyFfi7ujw; __utmmobile=0xf025a2c3ed6ac1db
//            //			Host: ssl.jobcan.jp
//            Request request = new Request.Builder()
//                    .url("https://ssl.jobcan.jp/login/employee?code=557748ec07ede4771c90186a56491625")
//                    .get()
//                    .build();
//            Response response = client.newCall(request).execute();
//            //			HTTP/1.1 200 OK
//            //			Date: Mon, 03 Oct 2016 01:41:29 GMT
//            //			Server: Apache
//            //			X-Frame-Options: SAMEORIGIN
//            //			Expires: Thu, 19 Nov 1981 08:52:00 GMT
//            //			Last-Modified: Mon, 03 Oct 2016 01:41:29 GMT
//            //			Cache-Control: no-cache
//            //			Pragma: no-cache
//            //			Vary: Accept-Encoding
//            //			Content-Length: 7506
//            //			Content-Type: text/html; charset=utf-8
//            //			Set-Cookie: __initial_session_checkid1=0; expires=Mon, 03-Oct-2016 01:11:29 GMT; path=/
//            //			Set-Cookie: __initial_session_checkid2=0; expires=Mon, 03-Oct-2016 01:11:29 GMT; path=/
//            //			Set-Cookie: __initial_session_checkid1=0; expires=Mon, 03-Oct-2016 01:11:29 GMT; path=/
//            //			Set-Cookie: __initial_session_checkid2=0; expires=Mon, 03-Oct-2016 01:11:29 GMT; path=/
//            //			Connection: close
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
////		// 打刻ページへ
////		try {
////			//			GET https://ssl.jobcan.jp/m/work/accessrecord?_m=adit HTTP/1.1
////			//			Connection: keep-alive
////			//			Upgrade-Insecure-Requests: 1
////			//			User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36
////			//			Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8
////			//			Referer: https://ssl.jobcan.jp/login/employee?code=557748ec07ede4771c90186a56491625
////			//			Accept-Encoding: sdch, br
////			//			Accept-Language: ja,en-US;q=0.8,en;q=0.6
////			//			Cookie: sid=j9catagngdagd4263amp8ouiq6; _ga=GA1.2.1852776987.1475456741; _mkto_trk=id:869-PHA-420&token:_mch-ssl.jobcan.jp-1475456745215-66911; __utma=237666178.1852776987.1475456741.1475456741.1475456741.1; __utmb=237666178.83.10.1475456741; __utmc=237666178; __utmz=237666178.1475456741.1.1.utmcsr=jobcan.ne.jp|utmccn=(referral)|utmcmd=referral|utmcct=/; __zlcmid=cvftchyFfi7ujw; __utmmobile=0xf025a2c3ed6ac1db
////			//			Host: ssl.jobcan.jp
////
////			Request request = new Request.Builder()
////					.url("https://ssl.jobcan.jp/m/work/accessrecord?_m=adit")
////					.get()
////					.build();
////			Response response = client.newCall(request).execute();
////
////			//			HTTP/1.1 200 OK
////			//			Date: Mon, 03 Oct 2016 01:41:42 GMT
////			//			Server: Apache
////			//			X-Frame-Options: SAMEORIGIN
////			//			Expires: Thu, 19 Nov 1981 08:52:00 GMT
////			//			Last-Modified: Mon, 03 Oct 2016 01:41:42 GMT
////			//			Cache-Control: no-cache
////			//			Pragma: no-cache
////			//			Vary: Accept-Encoding
////			//			Content-Length: 5541
////			//			Content-Type: text/html; charset=utf-8
////			//			Set-Cookie: __initial_session_checkid1=0; expires=Mon, 03-Oct-2016 01:11:42 GMT; path=/
////			//			Set-Cookie: __initial_session_checkid2=0; expires=Mon, 03-Oct-2016 01:11:42 GMT; path=/
////			//			Connection: close
////
////		} catch (IOException e) {
////			e.printStackTrace();
////		}
////
////		// 打刻(ボタンクリック)
////		try {
////			//			POST https://ssl.jobcan.jp/m/work/simplestamp HTTP/1.1
////			//			Connection: keep-alive
////			//			Content-Length: 59
////			//			Cache-Control: max-age=0
////			//			Origin: https://ssl.jobcan.jp
////			//			Upgrade-Insecure-Requests: 1
////			//			User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36
////			//			Content-Type: application/x-www-form-urlencoded
////			//			Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8
////			//			Referer: https://ssl.jobcan.jp/m/work/accessrecord?_m=adit
////			//			Accept-Encoding: br
////			//			Accept-Language: ja,en-US;q=0.8,en;q=0.6
////			//			Cookie: sid=j9catagngdagd4263amp8ouiq6; _ga=GA1.2.1852776987.1475456741; _mkto_trk=id:869-PHA-420&token:_mch-ssl.jobcan.jp-1475456745215-66911; __utma=237666178.1852776987.1475456741.1475456741.1475456741.1; __utmb=237666178.83.10.1475456741; __utmc=237666178; __utmz=237666178.1475456741.1.1.utmcsr=jobcan.ne.jp|utmccn=(referral)|utmcmd=referral|utmcct=/; __zlcmid=cvftchyFfi7ujw; __utmmobile=0xf025a2c3ed6ac1db
////			//			Host: ssl.jobcan.jp
////			//
////			//			time=&adit_item=%E6%89%93%E5%88%BB&gps=1&group_id=2&reason=
////
////			RequestBody body = new FormBody.Builder()
////					.add("time", "")
////					.add("adit_item", "打刻")
////					.add("gps", "1")
////					.add("group_id", "2")
////					.add("reason", "")
////					.build();
////			Request request = new Request.Builder()
////					.url("https://ssl.jobcan.jp/m/work/simplestamp")
////					.post(body)
////					.build();
////			Response response = client.newCall(request).execute();
////
////			//			HTTP/1.1 200 OK
////			//			Date: Mon, 03 Oct 2016 01:41:56 GMT
////			//			Server: Apache
////			//			X-Frame-Options: SAMEORIGIN
////			//			Expires: Thu, 19 Nov 1981 08:52:00 GMT
////			//			Last-Modified: Mon, 03 Oct 2016 01:41:56 GMT
////			//			Cache-Control: no-cache
////			//			Pragma: no-cache
////			//			Vary: Accept-Encoding
////			//			Content-Length: 7479
////			//			Content-Type: text/html; charset=utf-8
////			//			Set-Cookie: __initial_session_checkid1=0; expires=Mon, 03-Oct-2016 01:11:56 GMT; path=/
////			//			Set-Cookie: __initial_session_checkid2=0; expires=Mon, 03-Oct-2016 01:11:56 GMT; path=/
////			//			Set-Cookie: __initial_session_checkid1=0; expires=Mon, 03-Oct-2016 01:11:56 GMT; path=/
////			//			Set-Cookie: __initial_session_checkid2=0; expires=Mon, 03-Oct-2016 01:11:56 GMT; path=/
////			//			Set-Cookie: __initial_session_checkid1=0; expires=Mon, 03-Oct-2016 01:11:56 GMT; path=/
////			//			Set-Cookie: __initial_session_checkid2=0; expires=Mon, 03-Oct-2016 01:11:56 GMT; path=/
////			//			Connection: close
////
////		} catch (IOException e) {
////			e.printStackTrace();
////		}
////
////		// 位置確認後
////		try {
////			//			POST https://ssl.jobcan.jp/m/work/stamp-save-confirm/ HTTP/1.1
////			//			Connection: keep-alive
////			//			Content-Length: 115
////			//			Cache-Control: max-age=0
////			//			Origin: https://ssl.jobcan.jp
////			//			Upgrade-Insecure-Requests: 1
////			//			User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36
////			//			Content-Type: application/x-www-form-urlencoded
////			//			Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8
////			//			Referer: https://ssl.jobcan.jp/m/work/simplestamp
////			//			Accept-Encoding: br
////			//			Accept-Language: ja,en-US;q=0.8,en;q=0.6
////			//			Cookie: sid=j9catagngdagd4263amp8ouiq6; _ga=GA1.2.1852776987.1475456741; _mkto_trk=id:869-PHA-420&token:_mch-ssl.jobcan.jp-1475456745215-66911; __utma=237666178.1852776987.1475456741.1475456741.1475456741.1; __utmb=237666178.83.10.1475456741; __utmc=237666178; __utmz=237666178.1475456741.1.1.utmcsr=jobcan.ne.jp|utmccn=(referral)|utmcmd=referral|utmcct=/; __zlcmid=cvftchyFfi7ujw; __utmmobile=0xf025a2c3ed6ac1db
////			//			Host: ssl.jobcan.jp
////			//
////			//			lon=-1&lat=-1&year=2016&month=10&day=3&reason=ate&time=&group_id=2&position_id=&adit_item=%E6%89%93%E5%88%BB&yakin=
////
////			RequestBody body = new FormBody.Builder()
////					.add("lon", "139.7718614")
////					.add("lat", "35.6706505")
////					.add("year", "2016")
////					.add("month", "10")
////					.add("day", "3")
////					.add("reason", "")
////					.add("time", "")
////					.add("group_id", "2")
////					.add("position_id", "")
////					.add("adit_item", "打刻")
////					.add("yakin", "")
////					.build();
////			Request request = new Request.Builder()
////					.url("https://ssl.jobcan.jp/m/work/stamp-save-confirm/")
////					.post(body)
////					.build();
////			Response response = client.newCall(request).execute();
////
////			//			HTTP/1.1 302 Found
////			//			Date: Mon, 03 Oct 2016 01:42:28 GMT
////			//			Server: Apache
////			//			X-Frame-Options: SAMEORIGIN
////			//			Expires: Thu, 19 Nov 1981 08:52:00 GMT
////			//			Last-Modified: Mon, 03 Oct 2016 01:42:28 GMT
////			//			Cache-Control: no-cache
////			//			Pragma: no-cache
////			//			Location: /m/work/stampcomplete/year/2016/month/10/day/3/minutes/642/gps/gps-/
////			//			Vary: Accept-Encoding
////			//			Content-Length: 0
////			//			Content-Type: text/html; charset=utf-8
////			//			Set-Cookie: __initial_session_checkid1=0; expires=Mon, 03-Oct-2016 01:12:28 GMT; path=/
////			//			Set-Cookie: __initial_session_checkid2=0; expires=Mon, 03-Oct-2016 01:12:28 GMT; path=/
////			//			Connection: close
////
////		} catch (IOException e) {
////			e.printStackTrace();
////		}
//
//        // 位置確認後
//        try {
//            //			POST /m/work/stamp-save-smartphone/ HTTP/1.1
//            //			Host: ssl.jobcan.jp
//            //			Connection: keep-alive
//            //			Content-Length: 156
//            //			Cache-Control: max-age=0
//            //			Origin: https://ssl.jobcan.jp
//            //			Upgrade-Insecure-Requests: 1
//            //			User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36
//            //			Content-Type: application/x-www-form-urlencoded
//            //			Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8
//            //			Referer: https://ssl.jobcan.jp/m/work/stamp-save-confirm/
//            //			Accept-Encoding: gzip, deflate, br
//            //			Accept-Language: ja,en-US;q=0.8,en;q=0.6
//            //			Cookie: sid=j9catagngdagd4263amp8ouiq6; _ga=GA1.2.1852776987.1475456741; __utmt=1; _mkto_trk=id:869-PHA-420&token:_mch-ssl.jobcan.jp-1475456745215-66911; __utma=237666178.1852776987.1475456741.1475456741.1475461126.2; __utmb=237666178.2.10.1475461126; __utmc=237666178; __utmz=237666178.1475456741.1.1.utmcsr=jobcan.ne.jp|utmccn=(referral)|utmcmd=referral|utmcct=/; __zlcmid=cvftchyFfi7ujw; __utmmobile=0xf025a2c3ed6ac1db
//            //			Form Data
//            //			view source
//            //			view URL encoded
//            //
//            //			confirm=%E3%81%AF%E3%81%84&lon=139.7718614&lat=35.6706505&year=2016&month=10&day=3&reason=&time=&group_id=2&position_id=&adit_item=%E6%89%93%E5%88%BB&yakin=
//
//            RequestBody body = new FormBody.Builder()
//                    .add("confirm", "はい")
//                    .add("lon", "139.7718614")
//                    .add("lat", "35.6706505")
//                    .add("year", "2016")
//                    .add("month", "10")
//                    .add("day", "3")
//                    .add("reason", "")
//                    .add("time", "")
//                    .add("group_id", "2")
//                    .add("position_id", "")
//                    .add("adit_item", "打刻")
//                    .add("yakin", "")
//                    .build();
//            Request request = new Request.Builder()
//                    .url("https://ssl.jobcan.jp/m/work/stamp-save-smartphone/")
//                    .post(body)
//                    .build();
//            Response response = client.newCall(request).execute();
//
////			HTTP/1.1 302 Found
////			Date: Mon, 03 Oct 2016 02:24:33 GMT
////			Server: Apache
////			X-Frame-Options: SAMEORIGIN
////			Expires: Thu, 19 Nov 1981 08:52:00 GMT
////			Last-Modified: Mon, 03 Oct 2016 02:24:33 GMT
////			Cache-Control: no-cache
////			Pragma: no-cache
////			Location: /m/work/stampcomplete/year/2016/month/10/day/3/minutes/684/gps/gps-/
////			Vary: Accept-Encoding
////			Content-Encoding: gzip
////			Content-Length: 20
////			Content-Type: text/html; charset=utf-8
////			Set-Cookie: __initial_session_checkid1=0; expires=Mon, 03-Oct-2016 01:54:33 GMT; path=/
////			Set-Cookie: __initial_session_checkid2=0; expires=Mon, 03-Oct-2016 01:54:33 GMT; path=/
////			Connection: close
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//    //
//    //	private String postJson(URL url, String json) throws DTNApiException {
//    //		String ret;
//    //		try {
//    //			RequestBody body = RequestBody.create(JSON, json);
//    //			Request request = new Request.Builder()
//    //					.url(url)
//    //					.post(body)
//    //					.build();
//    //			Response response = httpClient.newCall(request).execute();
//    //
//    //			if (response.isSuccessful()) {
//    //				ret = response.body().string();
//    //			} else {
//    //				throw new DTNApiException(response.code(), response.body().string(), "API呼び出しに失敗しました。", null);
//    //			}
//    //		} catch (IOException e) {
//    //			throw new DTNApiException("API接続に失敗しました。", e);
//    //		}
//    //		return ret;
//    //	}
}
