package com.example.zhangbaokui.rtp;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.EditText;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author kui
 * @date on 2019/1/3 16:24
 */
public class Activity2 extends AppCompatActivity {

    @BindView(R.id.edtAddress)
    EditText edtAddress;
    @BindView(R.id.edtPort)
    EditText edtPort;
    @BindView(R.id.btnStart)
    Button btnStart;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity2);
        ButterKnife.bind(this);

        edtAddress.setText(Utils.getIPIpv4(this));
        edtPort.setText(Receiver.RTP_PORT+"");
    }

    @OnClick(R.id.btnStart)
    public void onViewClicked() {
        String ip = edtAddress.getText().toString().trim();
        String tem = edtPort.getText().toString().trim();
        int rtpPort = Utils.strToInt(tem);
        Intent intent = new Intent();
        intent.putExtra(RtpActivity.IP,ip);
        intent.putExtra(RtpActivity.RTP_PORT,rtpPort);
        intent.setClass(this,RtpActivity.class);
        startActivity(intent);
    }
}
