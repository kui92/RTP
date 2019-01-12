package com.example.zhangbaokui.rtp;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.yanzhenjie.permission.AndPermission;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.btnTest)
    Button btnTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    public void click(View view) {
        switch (view.getId()) {
            case R.id.btnPermission:
                checkPermission();
                break;
            case R.id.btnSend:
                startActivity(new Intent(this, SendActivity.class));
                break;
        }
    }


    private void checkPermission() {
        String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET};
        AndPermission.with(this)
                .runtime()
                .permission(permissions)
                .start();
    }

    @OnClick(R.id.btnTest)
    public void onViewClicked() {
        startActivity(new Intent(this,Activity2.class));
    }
}
