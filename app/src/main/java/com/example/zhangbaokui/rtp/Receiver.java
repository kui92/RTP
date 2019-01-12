package com.example.zhangbaokui.rtp;

import android.os.HandlerThread;
import android.util.Log;

import com.example.zhangbaokui.rtp.jlibrtp.DataFrame;
import com.example.zhangbaokui.rtp.jlibrtp.Participant;
import com.example.zhangbaokui.rtp.jlibrtp.RTPAppIntf;
import com.example.zhangbaokui.rtp.jlibrtp.RTPSession;

import java.net.DatagramSocket;

/**
 * @author kui
 * @date on 2019/1/2 14:47
 */
public class Receiver implements RTPAppIntf {

    public static final int RTP_PORT = 16384;
    private static final int RTCP_PORT = 16385;

    private String TAG = "Receiver";

    DatagramSocket rtpSocket = null;
    DatagramSocket rtcpSocket = null;
    private RTPSession rtpSession;
    private Thread thread;
    private VideoDecoder videoDecoder;

    public Receiver(){
        thread = new Thread(){
            @Override
            public void run() {
                init();
            }
        };
        thread.start();
    }

    private void init(){
        try {
            rtpSocket = new DatagramSocket(RTP_PORT);
            rtcpSocket = new DatagramSocket(RTCP_PORT);
        } catch (Exception e) {
            Log.i(TAG,"RTPSession failed to obtain port:"+e.getMessage());
        }
        rtpSession = new RTPSession(rtpSocket, rtcpSocket);
        rtpSession.naivePktReception(true);
        rtpSession.RTPSessionRegister(this,null, null);
    }

    public void relase(){
        if (rtpSocket!=null){
            rtpSocket.close();
            rtpSocket = null;
        }
        if (rtcpSocket!=null){
            rtcpSocket.close();
            rtcpSocket = null;
        }
        if (rtpSession!=null){
            rtpSession.endSession();
            rtpSession = null;
        }
    }

    public void setVideoDecoder(VideoDecoder videoDecoder) {
        this.videoDecoder = videoDecoder;
    }

    private long time = 0;
    private int size = 0;

    @Override
    public void receiveData(DataFrame frame, Participant participant) {
        if (videoDecoder!=null){
            videoDecoder.getData(frame);
        }
    }

    @Override
    public void userEvent(int type, Participant[] participant) {
        //Log.i(TAG,"Receiver userEvent:");
    }

    @Override
    public int frameSize(int payloadType) {
       // Log.i(TAG,"Receiver frameSize:");
        return 1;
    }
}
