package jp.co.getti.lab.android.jobcaaan.receiver;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.view.Gravity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.getti.lab.android.jobcaaan.BuildConfig;
import jp.co.getti.lab.android.jobcaaan.R;
import jp.co.getti.lab.android.jobcaaan.activity.MainActivity;
import jp.co.getti.lab.android.jobcaaan.service.JobcaaanService;

import static android.content.Context.VIBRATOR_SERVICE;

/**
 * アラームロジックReceiver
 * <pre>
 *     アラーム時刻に動作するReceiver。
 * </pre>
 */
public class AlarmLogicReceiver extends BroadcastReceiver {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(AlarmLogicReceiver.class);

    /** {@inheritDoc} */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        logger.debug("onReceive action:" + action + " myPackage:" + context.getPackageName());

        // スクリーンオン
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jobcaaan");
        wl.acquire(20000);

        if(BuildConfig.FLAVOR.equals("own")) {
            if (JobcaaanService.isRunning(context)) {
                JobcaaanService jobcaaanService = ((JobcaaanService.LocalBinder) peekService(context, new Intent(context, JobcaaanService.class))).getService();
                jobcaaanService.stamp(false, new JobcaaanService.StampCallback() {
                    @Override
                    public void onFinish() {
                        logger.info("打刻完了");
                    }
                });

                final Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
                long[] pattern = {500, 200, 150, 200, 150, 200, 500, 200, 150, 200, 150, 200, 500, 150, 200, 150, 200, 150, 200, 150, 200, 150, 200, 150, 200, 150, 200};
                vibrator.vibrate(pattern, 0);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        vibrator.cancel();
                    }
                }, 5750 * 2);
            } else {
                logger.info("Jobcaaanサービスが起動していません。");
            }
        } else {
            long[] pattern = {500, 200, 150, 200, 150, 200, 500, 200, 150, 200, 150, 200, 500, 150, 200, 150, 200, 150, 200, 150, 200, 150, 200, 150, 200, 150, 200};
            Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.argb(255, 119, 170, 215));

            // カード表示位置
            NotificationCompat.WearableExtender extender = new NotificationCompat.WearableExtender()
                    .setGravity(Gravity.CENTER_VERTICAL)
                    .setCustomSizePreset(NotificationCompat.WearableExtender.SIZE_LARGE)
                    .setBackground(bitmap);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationCompat.Builder mNotificationBuilder = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.mipmap.ic_status)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                    .setContentTitle(context.getString(R.string.alarm_name))
                    //.extend(extender)
                    .setStyle(new NotificationCompat.InboxStyle().addLine(" "))
                    .setVibrate(pattern)
                    .setContentText("打刻したら？")
                    .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0))
                    .setLights(0xffcccccc, 3000, 1000);

            nm.notify(10101, mNotificationBuilder.build());
        }
    }
}
