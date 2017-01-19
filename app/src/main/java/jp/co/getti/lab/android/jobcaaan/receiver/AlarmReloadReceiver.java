package jp.co.getti.lab.android.jobcaaan.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.getti.lab.android.jobcaaan.service.JobcaaanService;

/**
 * アラームリロードBroadcastReceiver
 * <pre>
 *     各種Broadcastを受信しAlermの再登録を行うReceiver。
 *
 *     Androidの時計機能はすぐずれるため、自動補正もよく行われる。
 *     タイマーは当然、登録時点の時間系で登録されるわけなので、
 *     時刻補正が起きた場合には再登録してあげるのがセオリー。
 *     また、AndroidのAlearmManagerは端末再起動時やアプリアップデート時にタイマーが消える。
 *
 *     本BroadcastReceiverはこれらのタイミング時に流れるBroadcastを受信し、アラームを再登録する。
 * </pre>
 */
public class AlarmReloadReceiver extends BroadcastReceiver {

    /** ロガー */
    private static final Logger logger = LoggerFactory.getLogger(AlarmReloadReceiver.class);

    /** {@inheritDoc} */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        logger.debug("onReceive action:" + action + " myPackage:" + context.getPackageName());

        // アラームリロード
        JobcaaanService.reloadAlerm(context);
    }
}
