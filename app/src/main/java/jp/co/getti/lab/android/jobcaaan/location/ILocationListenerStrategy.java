package jp.co.getti.lab.android.jobcaaan.location;

import android.location.Location;

/**
 * 位置情報ListenerStrategyインターフェース
 */
public interface ILocationListenerStrategy {

    /**
     * データ受信時
     *
     * @param time     時刻
     * @param location 位置情報
     */
    void onDataReceived(long time, Location location);

    /**
     * ステータス変更時
     *
     * @param status ステータス
     */
    void onStatusChanged(LocationStatus status);

    /**
     * エラー発生時
     *
     * @param level レベル
     * @param msg   メッセージ
     * @param e     例外
     */
    void onError(int level, String msg, Throwable e);
}