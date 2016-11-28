package jp.co.getti.lab.android.jobcaaan.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.getti.lab.android.jobcaaan.service.JobcaaanService;

import static android.content.Context.VIBRATOR_SERVICE;


public class AlermReceiver extends BroadcastReceiver {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(AlermReceiver.class);

    /** {@inheritDoc} */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        logger.debug("onReceive action:" + action + " myPackage:" + context.getPackageName());

        // スクリーンオン
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Tag");
        wl.acquire(20000);

//        if(JobcaaanService.isRunning(context)) {
//            JobcaaanService jobcaaanService = ((JobcaaanService.LocalBinder) peekService(context, new Intent(context, JobcaaanService.class))).getService();
//            jobcaaanService.stamp(false, new JobcaaanService.StampCallback() {
//                @Override
//                public void onFinish() {
//                    logger.info("打刻完了");
//                }
//            });

            final Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
            long[] pattern = {500, 200, 150, 200, 150, 200, 500, 200, 150, 200, 150, 200, 500, 150, 200, 150, 200, 150, 200, 150, 200, 150, 200, 150, 200, 150, 200};
            vibrator.vibrate(pattern, 0);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    vibrator.cancel();
                }
            }, 5750 * 2);
//        } else {
//            logger.info("Jobcaaanサービスが起動していません。");
//        }
    }
}
