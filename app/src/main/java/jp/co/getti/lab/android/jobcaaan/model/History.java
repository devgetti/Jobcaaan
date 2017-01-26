package jp.co.getti.lab.android.jobcaaan.model;

import java.io.Serializable;
import java.util.Date;


public class History implements Serializable {

    private int id;

    private Date dateTime;

    private String type;

    private String title;

    public History() {
    }

    public History(int id, Date dateTime, String type, String title) {
        this.dateTime = dateTime;
        this.id = id;
        this.title = title;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getDateTime() {
        return dateTime;
    }

    public void setDateTime(Date dateTime) {
        this.dateTime = dateTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
