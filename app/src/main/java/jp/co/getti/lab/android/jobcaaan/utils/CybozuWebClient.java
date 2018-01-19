package jp.co.getti.lab.android.jobcaaan.utils;

import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.CookieManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.JavaNetCookieJar;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * CybozuWebクライアント
 */
public class CybozuWebClient {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36";

    private static final String ACCEPT_LANGUAGE = "ja,en-US;q=0.8,en;q=0.6";

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(CybozuWebClient.class);

    /** HttpClient */
    private OkHttpClient client;

    /** Gson */
    private Gson gson;

    /**
     * コンストラクタ
     */
    public CybozuWebClient() {
        try {
            // 自己署名許可HttpClient作成
            X509TrustManager tm = new X509TrustManager() {

                @Override
                @SuppressWarnings("all")
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

                }

                @Override
                @SuppressWarnings("all")
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
                public void log(@NonNull String message) {

                    logger.debug(message);
                }
            });
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            client = new OkHttpClient().newBuilder().sslSocketFactory(sslSocketFactory, tm)
                    //.retryOnConnectionFailure(true)
                    .addInterceptor(logging).cookieJar(new JavaNetCookieJar(new CookieManager()))
                    //.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8889)))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS).followRedirects(true).hostnameVerifier(new HostnameVerifier() {

                        @Override
                        @SuppressWarnings("all")
                        public boolean verify(String hostname, SSLSession session) {

                            return true;
                        }
                    }).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        gson = new Gson();
    }

//    public static void main(String[] argv) {
//
//        try {
//            CybozuWebClient client = new CybozuWebClient();
//
//            client.stampFlow("a-kosuge@netwrk.co.jp", "tmgetti3!", new ResultCallback() {
//
//                @Override
//                public void onSuccess() {
//
//                    logger.error("成功");
//                }
//
//                @Override
//                public void onError(String msg) {
//
//                    logger.error(msg);
//                }
//            });
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    /**
     * トークン取得
     *
     * @param callback コールバック
     */
    public void getToken(final Callback callback) {

        RequestBody body = RequestBody.create(null, new byte[0]);
        Request request = new Request.Builder()
                .url("https://netwrk.cybozu.com/api/auth/getToken.json?_lc=ja_JP")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .build();
        client.newCall(request).enqueue(callback);
    }

    /**
     * ログイン
     *
     * @param userName     ユーザ名
     * @param password     パスワード
     * @param requestToken リクエストトークン
     * @param callback     コールバック
     */
    public void login(final String userName, final String password, final String requestToken, final Callback callback) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                gson.toJson(new LoginParam(userName, password, false, "", requestToken)));
        Request request = new Request.Builder()
                .url("https://netwrk.cybozu.com/api/auth/login.json?_lc=ja_JP")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .build();
        client.newCall(request).enqueue(callback);
    }

    /**
     * ログイン(トークン取得含)
     *
     * @param userName ユーザ名
     * @param password パスワード
     * @param callback コールバック
     */
    public void login(final String userName, final String password, final Callback callback) {
        runUseTokenLogic(new IUseTokenLogic() {

            @Override
            public void run(String requestToken) {
                login(userName, password, requestToken, new Callback() {

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {

                        if (response.isSuccessful()) {
                            top(new Callback() {

                                @Override
                                public void onResponse(Call call, Response response) throws IOException {
                                    if (response.isSuccessful()) {
                                        callback.onResponse(call, response);
                                    } else {
                                        callback.onFailure(call, new IOException("top: invalid response. code=" + response.code()));
                                    }
                                }

                                @Override
                                public void onFailure(Call call, IOException e) {
                                    callback.onFailure(call, e);
                                }
                            });

                        } else {
                            callback.onFailure(call, new IOException("login: invalid response. code=" + response.code()));
                        }
                    }

                    @Override
                    public void onFailure(Call call, IOException e) {
                        callback.onFailure(call, e);
                    }
                });
            }
        }, callback);
    }

    /**
     * ログアウト
     *
     * @param requestToken リクエストトークン
     * @param callback     コールバック
     */
    public void logout(final String requestToken, final Callback callback) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), gson.toJson(new LogoutParam(requestToken)));
        Request request = new Request.Builder()
                .url("https://netwrk.cybozu.com/api/auth/logout.json")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .build();
        client.newCall(request).enqueue(callback);
    }

    /**
     * ログアウト(トークン取得含)
     *
     * @param callback コールバック
     */
    public void logout(final Callback callback) {
        runUseTokenLogic(new IUseTokenLogic() {

            @Override
            public void run(String requestToken) {

                logout(requestToken, new Callback() {

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            callback.onResponse(call, response);
                        } else {
                            callback.onFailure(call, new IOException("logout: invalid response. code=" + response.code()));
                        }
                    }

                    @Override
                    public void onFailure(Call call, IOException e) {
                        callback.onFailure(call, e);
                    }
                });
            }
        }, callback);
    }

    /**
     * Top画面
     *
     * @param callback コールバック
     */
    public void top(final Callback callback) {
        Request request = new Request.Builder()
                .url("https://netwrk.cybozu.com/o/")
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .build();
        client.newCall(request).enqueue(callback);
    }

    /**
     * トークン利用ロジック実行
     *
     * @param logic    トークン利用ロジック
     * @param callback コールバック
     */
    private void runUseTokenLogic(final IUseTokenLogic logic, final Callback callback) {
        // トークン発行
        getToken(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(call, e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody tokenBody = response.body();
                    if (tokenBody != null) {
                        try {
                            String strBody = tokenBody.string();
                            BaseResponse res = gson.fromJson(strBody, BaseResponse.class);
                            if (res.success && res.result != null && res.result.token != null && res.result.token.length() > 0) {
                                logic.run(res.result.token);
                            } else {
                                callback.onFailure(call, new IOException("getToken: response error. body=" + strBody));
                            }
                        } catch (Exception e) {
                            callback.onFailure(call, new IOException("getToken: read response error.", e));
                        } finally {
                            tokenBody.close();
                        }
                    } else {
                        callback.onFailure(call, new IOException("getToken: body is null."));
                    }
                } else {
                    callback.onFailure(call, new IOException("getToken: invalid response. code=" + response.code()));
                }
            }
        });
    }

    /**
     * 打刻
     * <pre>
     *     Cybozuの打刻条件は以下
     *     ・アプリケーションメニューをクリックして、画面を遷移する
     *     ・サイボウズ Officeで新着メールをチェックする
     *     ・メッセージなどのデータの詳細画面を表示する
     *     ・キーボードの「F5」を押す
     *     ・KUNAIで次のアプリを操作する
     *     　・掲示板
     *     　・カスタムアプリ
     *     ・サイボウズ Office 新着通知で次の操作をする
     *     　・アプリホーム画面の予定カードをタップする
     *     　・通知をタップする
     *     　・PC画面を開く
     *
     *     つまりはトップページを開けばよいので、ログイン→トップGET→ログアウトする
     * </pre>
     *
     * @param userName       ユーザ名
     * @param password       パスワード
     * @param resultCallback コールバック
     * @see <a href="https://faq.cybozu.info/alphascope/cybozu/web/office/Detail.aspx?id=661">QA</a>
     */
    public void stampFlow(final String userName, final String password, final ResultCallback resultCallback) {
        login(userName, password, new Callback() {

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                logout(new Callback() {
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (resultCallback != null) {
                            resultCallback.onSuccess();
                        }
                    }

                    @Override
                    public void onFailure(Call call, IOException e) {
                        if (resultCallback != null) {
                            resultCallback.onError(e.getMessage(), e);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                if (resultCallback != null) {
                    resultCallback.onError(e.getMessage(), e);
                }
            }
        });
    }

    /**
     * 結果コールバック
     */
    public interface ResultCallback {

        /**
         * 成功時
         */
        void onSuccess();

        /**
         * エラー時
         *
         * @param msg メッセージ
         */
        void onError(String msg, Throwable e);
    }

    /**
     * トークン利用ロジックInterface
     */
    private interface IUseTokenLogic {

        void run(String requestToken);
    }

    private static class BaseParam implements Serializable {

        @SerializedName("__REQUEST_TOKEN__")
        String requestToken;
    }

    private static class LoginParam extends BaseParam {

        String username;

        String password;

        boolean keepUsername;

        String redirect;

        LoginParam(String username, String password, boolean keepUsername, String redirect, String requestToken) {
            this.username = username;
            this.password = password;
            this.keepUsername = keepUsername;
            this.redirect = redirect;
            this.requestToken = requestToken;
        }
    }

    private static class LogoutParam extends BaseParam {

        LogoutParam(String requestToken) {
            this.requestToken = requestToken;
        }
    }

    private static class BaseResponse {

        Result result;

        boolean success;

        private static class Result {

            String token;
        }
    }
}
