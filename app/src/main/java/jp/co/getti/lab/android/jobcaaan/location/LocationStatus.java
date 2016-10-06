package jp.co.getti.lab.android.jobcaaan.location;

import java.io.Serializable;

/**
 * 位置情報Listenerステータス
 */
@SuppressWarnings("unused")
public class LocationStatus implements Serializable {

    /** 利用可否 */
    public boolean available = false;

    /** Listening状態 */
    public boolean listening = false;

    /**
     * コンストラクタ
     */
    public LocationStatus() {
    }

    /**
     * コンストラクタ
     *
     * @param available 利用可否
     * @param listening Liestening状態
     */
    public LocationStatus(boolean available, boolean listening) {
        this.available = available;
        this.listening = listening;
    }
}