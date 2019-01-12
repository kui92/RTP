package com.example.zhangbaokui.rtp;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.example.zhangbaokui.rtp.jlibrtp.DataFrame;
import com.example.zhangbaokui.rtp.jlibrtp.Participant;
import com.example.zhangbaokui.rtp.jlibrtp.RTCPAppIntf;
import com.example.zhangbaokui.rtp.jlibrtp.RTPAppIntf;
import com.example.zhangbaokui.rtp.jlibrtp.RTPSession;

import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author kui
 * @date on 2019/1/2 11:01
 */
public class SendSession implements RTCPAppIntf, RTPAppIntf {

    public static final String TAG = "SendSession";
    public static final int RTP_PORT = 8002;
    public static final int RTCP_PORT = RTP_PORT+1;
    private static final int ADD_Participant = 1;
    private static final int SEND_DATA = 2;
    private static final int SIZE = 1480;//RtpPkt.setPayload: Cannot carry more than 1480 bytes for now.
    private RTPSession rtpSession = null;
    private DatagramSocket rtpSocket;
    private DatagramSocket rtcpSocket;
    private Handler sendHandler;
    private HandlerThread sendThread = new HandlerThread("sendThread");
    private boolean tag = false;

    public SendSession(){
        sendThread.start();
        sendHandler = new Handler(sendThread.getLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case ADD_Participant:
                        if (tag){
                            return;
                        }
                        if (msg.obj!=null&&msg.obj instanceof Participant){
                            tag = true;
                            rtpSession.addParticipant((Participant) msg.obj);
                            rtpSession.RTPSessionRegister(SendSession.this,SendSession.this,null);
                        }
                        break;
                    case SEND_DATA:
                        if (msg.obj!=null&&msg.obj instanceof DataPackge){
                            DataPackge packge = (DataPackge) msg.obj;
                            sendData(packge.data,packge.csrcArray,packge.markers,packge.rtpTimestamp,packge.seqNumbers);
                        }
                        break;
                }
            }
        };
        sendHandler.post(new Runnable() {
            @Override
            public void run() {
                initSession();
            }
        });
    }

    private void initSession(){
        try {
            rtpSocket = new DatagramSocket(RTP_PORT);
            rtcpSocket = new DatagramSocket(RTCP_PORT);
        } catch (Exception e) {
            Log.e(TAG, "InitSession Exception: " + "send init session exception:"+e);
        }
        rtpSession = new RTPSession(rtpSocket, rtcpSocket);
        //rtpSession.endSession();
    }

    public void addParticipant(Participant participant){
        if (participant!=null&&rtpSession!=null){
            Message message = Message.obtain();
            message.what = ADD_Participant;
            message.obj = participant;
            sendHandler.sendMessage(message);
        }
    }

    public void sendData(DataPackge packge){
        if (packge==null){
            return;
        }
        Message message = Message.obtain();
        message.obj = packge;
        message.what = SEND_DATA;
        sendHandler.sendMessage(message);
    }

    public void sendDataByte(byte[] bytes,long timeScap) {
        Log.i("Receiver","sendData:"+bytes.length+"--timeScap:"+timeScap);
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
        packge.rtpTimestamp = timeScap;
        sendData(packge);
    }

    public void sendDataList(byte[] bytes,long timeScap){
        DataPackge packge = DataHandle.splite(bytes,bytes.length);
        packge.rtpTimestamp= timeScap;
        sendData(packge);
    }


    private void sendData(byte[][] buffers,long[] csrcArray,boolean[] markers, long rtpTimestamp,long[] seqNumbers){
        if (rtpSession==null){
            return;
        }
        Log.i("Receiver","sendData rtpTimestamp:"+rtpTimestamp+"-----------------------------");
        rtpSession.sendData(buffers,csrcArray,markers,rtpTimestamp,seqNumbers);
    }

    public void relase(){
        if (sendHandler!=null){
            sendHandler.removeCallbacksAndMessages(null);
            sendHandler = null;
        }
        if (sendThread!=null){
            sendThread.quit();
            sendThread = null;
        }
        if (rtpSession!=null){
            rtpSession.endSession();
            rtpSession = null;
        }
        if (rtpSocket!=null){
            rtpSocket.close();
            rtpSocket = null;
        }
        if (rtpSocket!=null){
            rtcpSocket.close();
            rtcpSocket = null;
        }
    }

    /************** RTP ****************/
    @Override
    public void receiveData(DataFrame frame, Participant participant) {
        Log.i(TAG,"rtpSession:"+participant.getLocation());
    }

    @Override
    public void userEvent(int type, Participant[] participant) {
        Log.i(TAG,"userEvent:");
    }

    @Override
    public int frameSize(int payloadType) {
        Log.i(TAG,"frameSize:"+payloadType);
        return 1;
    }

    /********************   RTCP *****************************/
    @Override
    public void SRPktReceived(long ssrc, long ntpHighOrder, long ntpLowOrder, long rtpTimestamp, long packetCount, long octetCount, long[] reporteeSsrc, int[] lossFraction, int[] cumulPacketsLost, long[] extHighSeq, long[] interArrivalJitter, long[] lastSRTimeStamp, long[] delayLastSR) {

    }

    @Override
    public void RRPktReceived(long reporterSsrc, long[] reporteeSsrc, int[] lossFraction, int[] cumulPacketsLost, long[] extHighSeq, long[] interArrivalJitter, long[] lastSRTimeStamp, long[] delayLastSR) {

    }

    @Override
    public void SDESPktReceived(Participant[] relevantParticipants) {

    }

    @Override
    public void BYEPktReceived(Participant[] relevantParticipants, String reason) {

    }

    @Override
    public void APPPktReceived(Participant part, int subtype, byte[] name, byte[] data) {

    }

}
