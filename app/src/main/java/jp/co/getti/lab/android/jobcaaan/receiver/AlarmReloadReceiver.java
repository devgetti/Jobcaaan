package jp.co.getti.lab.android.jobcaaan.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.getti.lab.android.jobcaaan.service.JobcaaanService;


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
