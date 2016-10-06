package jp.co.getti.lab.android.jobcaaan.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import jp.co.getti.lab.android.jobcaaan.R;

public class AlertActivity extends AppCompatActivity {

    private static final String EXTRA_MSG = "MSG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String message = getIntent().getStringExtra(EXTRA_MSG);
        //setContentView(R.layout.activity_call_dialog);
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle("title");
        alertBuilder.setMessage("message");
        alertBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                AlertActivity.this.finish();//選択をしたら自信のActivityを終了させる
            }
        });
        alertBuilder.create().show();
    }

    public static void show(Context context, String message) {
        Intent intent = new Intent(context, AlertActivity.class);
        intent.putExtra(EXTRA_MSG, message);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}
