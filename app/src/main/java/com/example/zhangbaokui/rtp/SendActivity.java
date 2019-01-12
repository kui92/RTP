package com.example.zhangbaokui.rtp;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.zhangbaokui.rtp.jlibrtp.Participant;

import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author kui
 * @date on 2019/1/2 14:07
 */
public class SendActivity extends AppCompatActivity {

    private String TAG = "SendActivity";

    @BindView(R.id.edtAddress)
    EditText edtAddress;
    @BindView(R.id.edtPort)
    EditText edtPort;
    @BindView(R.id.btnAddReciver)
    Button btnAddReciver;
    @BindView(R.id.btnSend)
    Button btnSend;

    public static final int SIZE = 20;

    private SendSession sendSession;

    private Receiver receiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activivty_send);
        ButterKnife.bind(this);
        sendSession = new SendSession();
        receiver = new Receiver();
        Log.i(SendSession.TAG,"ip:"+Utils.getIPIpv4(this));
        edtAddress.setText(Utils.getIPIpv4(this));
        edtPort.setText(Receiver.RTP_PORT+"");
    }


    @OnClick({R.id.btnAddReciver, R.id.btnSend})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btnAddReciver:
                add();
                break;
            case R.id.btnSend:
                sendData(getBy());
                break;
        }
    }

    private void add(){
        String ip = edtAddress.getText().toString().trim();
        String port = edtPort.getText().toString().trim();
        int rtpPort = Utils.strToInt(port);
        if (rtpPort<0){
            return;
        }
        int rtcpPort = rtpPort+1;
        Participant participant = new Participant(ip,rtpPort,rtcpPort);
        sendSession.addParticipant(participant);
    }

    private byte[] getBy(){
        byte[] bytes = new byte[100];
        for (int i=0;i<bytes.length;i++){
            bytes[i] = (byte) i;
        }
        return bytes;
    }


    /**
     * 将每帧进行分包并发送数据
     * @param bytes
     */
    private void sendData(byte[] bytes) {
        int dataLength = (bytes.length - 1) / SIZE + 1;
        final byte[][] data = new byte[dataLength][];
        final boolean[] marks = new boolean[dataLength];
        marks[marks.length - 1] = true;
        int x = 0;
        int y = 0;
        int length = bytes.length;
        for (int i = 0; i < length; i++){
            if (y == 0){
                data[x] = new byte[length - i > SIZE ? SIZE : length - i];
            }
            data[x][y] = bytes[i];
            y++;
            if (y == data[x].length){
                y = 0;
                x++;
            }
        }
        DataPackge packge = new DataPackge();
        packge.data = data;
        packge.markers = marks;
        packge.rtpTimestamp = 100000;
        // TODO: 17/6/15
        sendSession.sendData(packge);
        Log.e(TAG, "sendData: " + Arrays.deepToString(data));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sendSession.relase();
        receiver.relase();
    }
}
