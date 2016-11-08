package jp.co.getti.lab.android.jobcaaan.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.support.v4.app.NotificationCompat;
import android.view.Gravity;

import jp.co.getti.lab.android.jobcaaan.R;

/**
 * ジョブカンNotification
 */
public class JobcaaanNotification {

    /** Action 打刻ボタン押下時 */
    public static final String ACTION_CLICK_STAMP = JobcaaanNotification.class.getName() + ".ACTION_CLICK_STAMP";

    /** Action 打刻(位置取得有)ボタン押下時 */
    public static final String ACTION_CLICK_STAMP_WITH_LOCATE = JobcaaanNotification.class.getName() + ".ACTION_CLICK_STAMP_WITH_LOCATE";

    /** Action 終了ボタン押下時 */
    public static final String ACTION_CLICK_SHUTDOWN = JobcaaanNotification.class.getName() + ".ACTION_CLICK_SHUTDOWN";

    /** コンテキスト */
    private Service mService;

    /** Notification Manager */
    private NotificationManager mNotificationManager;

    /** Notification Builder */
    private NotificationCompat.Builder mNotificationBuilder;

    /** Notification表示状態 */
    private boolean mIsShown = false;

    /** 自身がWearかどうか */
    private boolean mIsWear = false;

    /** Notification ID */
    private int mNotificationId;

    /** コールバック */
    private INotificationAction mCallback;

    /** ボタンActionReceiver */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mCallback != null) {
                if (ACTION_CLICK_STAMP.equals(action)) {
                    mCallback.onClickStamp(false);
                } else if (ACTION_CLICK_STAMP_WITH_LOCATE.equals(action)) {
                    mCallback.onClickStamp(true);
                } else if (ACTION_CLICK_SHUTDOWN.equals(action)) {
                    mCallback.onClickShutdown();
                }
            }
        }
    };

    /**
     * コンストラクタ
     *
     * @param service        サービス
     * @param notificationId NotificationID
     * @param isWear         自身がwearかどうか
     * @param callback       コールバック
     */
    public JobcaaanNotification(Service service, int notificationId, boolean isWear, INotificationAction callback) {
        this.mService = service;
        this.mNotificationId = notificationId;
        this.mIsWear = isWear;
        this.mCallback = callback;

        // NotificationManager取得
        this.mNotificationManager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);

        // 計測開始Action作成
        int requestCode = 0;

        // 打刻Action
        NotificationCompat.Action clickStampAction;
        {
            Intent intent = new Intent(ACTION_CLICK_STAMP);
            intent.addCategory(new ComponentName(mService, mService.getClass()).getPackageName());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(mService, requestCode++, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            clickStampAction = new NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, mService.getString(R.string.stamp), pendingIntent).build();
        }

        // 打刻(現在位置)Action
        NotificationCompat.Action clickStampWithLocateAction;
        {
            Intent intent = new Intent(ACTION_CLICK_STAMP_WITH_LOCATE);
            intent.addCategory(new ComponentName(mService, mService.getClass()).getPackageName());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(mService, requestCode++, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            clickStampWithLocateAction = new NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, mService.getString(R.string.stamp_with_locate), pendingIntent).build();
        }

        // ShutdownAction作成
        NotificationCompat.Action clickFinishAction;
        {
            Intent intent = new Intent(ACTION_CLICK_SHUTDOWN);
            intent.addCategory(new ComponentName(mService, mService.getClass()).getPackageName());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(mService, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            clickFinishAction = new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, mService.getString(R.string.finish), pendingIntent).build();
        }

        Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.argb(255, 119, 170, 215));

        // カード表示位置
        NotificationCompat.WearableExtender extender = new NotificationCompat.WearableExtender()
                .setGravity(Gravity.CENTER_VERTICAL)
                .setCustomSizePreset(NotificationCompat.WearableExtender.SIZE_LARGE)
                .setBackground(bitmap);

        // NotificationBuilder作成
        mNotificationBuilder = new NotificationCompat.Builder(mService)
                .setSmallIcon((mIsWear) ? R.mipmap.ic_launcher : R.mipmap.ic_status)
                .setLargeIcon((mIsWear) ? null : BitmapFactory.decodeResource(mService.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(mService.getString(R.string.app_name))
                .extend(extender)
                .setStyle(new NotificationCompat.InboxStyle().addLine(" "))
                .addAction(clickStampAction)
                .addAction(clickStampWithLocateAction);
        //.addAction(clickFinishAction);
    }

    private static IntentFilter makeIntentFilter(Context context) {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addCategory(new ComponentName(context, context.getClass()).getPackageName());
        intentFilter.addAction(ACTION_CLICK_STAMP);
        intentFilter.addAction(ACTION_CLICK_STAMP_WITH_LOCATE);
        intentFilter.addAction(ACTION_CLICK_SHUTDOWN);
        return intentFilter;
    }

    /**
     * 更新
     *
     * @param detail 詳細文言
     */
    public void update(String detail, String lauchActivity) {
//        // タイトル
//        this.mNotificationBuilder.setContentTitle(title);

        // テキスト設定
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();//.addLine(status);
        if (detail != null) {
            String[] rows = detail.split("\n");
            for (String row : rows) {
                style.addLine(" " + row);
            }
        }
        this.mNotificationBuilder.setStyle(style);

        // タップ時Action
        PendingIntent contentIntent = null;
        if (lauchActivity != null) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName(mService.getApplicationContext().getPackageName(), lauchActivity));
            contentIntent = PendingIntent.getActivity(mService, 9999, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        this.mNotificationBuilder.setContentIntent(contentIntent);

        // Notification表示中であればそのまま表示更新
        if (mIsShown) {
            // 削除されないNotification作成
            Notification notification = this.mNotificationBuilder.build();
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            notification.flags |= Notification.FLAG_NO_CLEAR;

            // 通知表示
            mNotificationManager.notify(mNotificationId, notification);

            // サービスフォアグラウンド化
            mService.startForeground(mNotificationId, notification);
        }
    }

    /**
     * 表示
     */
    public void show() {
        if (!mIsShown) {
            mIsShown = true;

            // 削除されないNotification作成
            Notification notification = this.mNotificationBuilder.build();
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            notification.flags |= Notification.FLAG_NO_CLEAR;

            // 通知表示
            mNotificationManager.notify(mNotificationId, notification);

            // サービスフォアグラウンド化
            mService.startForeground(mNotificationId, notification);

            // Receiver登録
            mService.registerReceiver(mBroadcastReceiver, makeIntentFilter(mService));
        }
    }

    /**
     * 非表示
     */
    public void hide() {
        if (mIsShown) {
            // Reciever解除
            mService.unregisterReceiver(mBroadcastReceiver);

            // サービスフォアグラウンド化解除
            mService.stopForeground(true);

            // 通知非表示
            mNotificationManager.cancel(mNotificationId);

            mIsShown = false;
        }
    }

    public boolean isShow() {
        return mIsShown;
    }

    /**
     * 通知アクションInterface
     */
    public interface INotificationAction {

        /** 打刻ボタン押下時 */
        void onClickStamp(boolean withLocate);

        /** シャットダウンボタン押下時 */
        void onClickShutdown();
    }
}
