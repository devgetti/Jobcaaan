package jp.co.getti.lab.android.jobcaaan;

import android.content.Context;
import android.os.Environment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


public class Application extends android.app.Application {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private static Context context;

    /** {@inheritDoc} */
    @SuppressWarnings("all")
    @Override
    public void onCreate() {
        super.onCreate();
        this.context = this;

        // 現在設定されている UncaughtExceptionHandler を退避
        final Thread.UncaughtExceptionHandler savedUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

        // キャッチされなかった例外発生時の処理を設定する
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            private volatile boolean mCrashing = false;

            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                try {
                    if (!mCrashing) {
                        mCrashing = true;

                        logger.error(ex.getMessage());
                    }
                } finally {
                    savedUncaughtExceptionHandler.uncaughtException(thread, ex);
                }
            }
        });
    }
}
