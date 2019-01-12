package com.example.zhangbaokui.rtp;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.example.zhangbaokui.rtp.jlibrtp.Participant;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author kui
 * @date on 2019/1/3 15:49
 */
public class VideoEncod {

    private String TAG = "VideoEncod";

    private int convertType;
    private MediaCodec mVideoEnc;
    private String videoMime="video/avc";   //视频编码格式
    private int videoRate=2048000;       //视频编码波特率
    private int frameRate=25;           //视频编码帧率
    private int frameInterval=1;        //视频编码关键帧，1秒一关键帧
    private int width;
    private int height;
    private volatile boolean videoRecording = false;
    private HandlerThread videoHandlerThread;
    private Handler videoHandler;
    private final int VIDEO_FRAM = 1;
    private final int VIDEO_QUIT = 2;
    private byte[] yuv420;
    private long startNanoTime;
    private long feedCount = 0,framCoun = 0;
    private SendSession sendSession;

    public VideoEncod(){
        sendSession = new SendSession();
    }

    public boolean prepare(int width,int height){
        this.width=width;
        this.height=height;
        yuv420 = new byte[width*height*3/2];
        MediaFormat videoFormat=MediaFormat.createVideoFormat(videoMime,width,height);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE,videoRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE,frameRate);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,frameInterval);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,checkColorFormat(videoMime));
        try {
            mVideoEnc=MediaCodec.createEncoderByType(videoMime);
        } catch (IOException e) {
            Log.e(TAG,"video 初始化失败:"+e.getMessage());
            e.printStackTrace();
            return false;
        }
        mVideoEnc.configure(videoFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        Bundle bundle=new Bundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            bundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE,videoRate);
            mVideoEnc.setParameters(bundle);
        }
        return true;
    }

    public void startVideo(final String ip, final int port){
        if (mVideoEnc==null){
            return;
        }
        videoRecording = true;
        mVideoEnc.start();
        if (videoHandler!=null){
            videoHandler.removeCallbacksAndMessages(null);
        }
        startNanoTime = System.nanoTime();
        if (videoHandlerThread!=null&&videoHandlerThread.isAlive()){
            quiThreadHandler(videoHandlerThread);
        }
        videoHandlerThread = new HandlerThread("videoThread");
        videoHandlerThread.start();
        videoHandler = new Handler(videoHandlerThread.getLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case VIDEO_FRAM:
                        try {
                            getFramMsg(msg);
                        }catch (Exception e){
                            Log.e(TAG,"video 编错误："+e.getMessage());
                        }
                        break;
                    case VIDEO_QUIT:
                        videoHandler.removeCallbacksAndMessages(null);
                        quiThreadHandler(videoHandlerThread);
                        break;
                }
            }
        };
        videoHandler.post(new Runnable() {
            @Override
            public void run() {
                Participant participant = new Participant(ip,port,port+1);
                sendSession.addParticipant(participant);
            }
        });
    }

    /**
     * 写入一帧数据
     * @param data
     * @return true编码继续 false结束视频录制
     */
    public boolean feedData(byte[] data){
        if (!videoRecording){
            return false;
        }
        //Log.i(TAG,"喂入一帧数据："+time);
        videoHandler.sendMessage(generatFramMessage(data));
        feedCount++;
        return true;
    }

    public void stopVideo(){
        if (!videoRecording){
            return;
        }
        Log.i(TAG,"stopVideo");
        videoRecording = false;
        videoHandler.removeCallbacksAndMessages(null);
        videoHandler.sendEmptyMessage(VIDEO_QUIT);
        try {
            videoHandlerThread.join();
            Log.i(TAG,"videoHandlerThread end");
        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.i(TAG,"videoHandlerThread end InterruptedException:"+e.getCause().getMessage());
        }
        if (mVideoEnc!=null){
            mVideoEnc.stop();
            mVideoEnc.release();
            mVideoEnc = null;
        }
        if (sendSession!=null){
            sendSession.relase();
        }
    }

    private void getFramMsg(Message message){
        byte[] frams = (byte[]) message.obj;
        int index = mVideoEnc.dequeueInputBuffer(-1);
        long time = (System.nanoTime() - startNanoTime)/1000L;
        framCoun++;
        if (index>=0){
            NV21toI420SemiPlanar(frams,yuv420,width,height);
            ByteBuffer buffer = getInputBuffer(mVideoEnc,index);
            buffer.clear();
            buffer.put(yuv420);
            int flag = videoRecording?0:MediaCodec.BUFFER_FLAG_END_OF_STREAM;
            mVideoEnc.queueInputBuffer(index,0,yuv420.length,time,flag);
        }
        MediaCodec.BufferInfo mInfo=new MediaCodec.BufferInfo();
        int outIndex = 0;
        while (outIndex>=0){
            outIndex = mVideoEnc.dequeueOutputBuffer(mInfo,0);
            //Log.e(TAG,"------获取到视频帧信息 flag："+mInfo.flags+"--offset:"+mInfo.offset+"--timeUs:"+mInfo.presentationTimeUs+"--size:"+mInfo.size);
            if (mInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM){
                Log.e(TAG,"读取到video结束标志:"+time+"***********"+outIndex);
                break;
            }
            if (outIndex>=0){
                ByteBuffer outBuffer = getOutputBuffer(mVideoEnc,outIndex);
                byte[] bytes = new byte[mInfo.size];
                outBuffer.get(bytes,mInfo.offset,mInfo.size);
                sendSession.sendDataList(bytes,time);
                mVideoEnc.releaseOutputBuffer(outIndex,false);
            }else if (outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                // The PPS and PPS shoud be there
                MediaFormat format = mVideoEnc.getOutputFormat();
                ByteBuffer spsb = format.getByteBuffer("csd-0");
                ByteBuffer ppsb = format.getByteBuffer("csd-1");
                byte[] data1 = new byte[spsb.array().length];
                spsb.get(data1,0,spsb.array().length);
                Log.e("readPPs","data1 INFO_OUTPUT_FORMAT_CHANGED:");
            }else {
                break;
                //Log.i(TAG,"读取到video其他状态:"+outIndex);
            }
        }
    }

    private Message generatFramMessage(byte[] framData){
        Message message;
        if (videoHandler!=null){
            message = Message.obtain(videoHandler);
            message.obj = framData;
        }else {
            message = Message.obtain();
            message.obj = framData;
        }
        message.what = VIDEO_FRAM;
        return message;
    }
    private static void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
        for (int i = width * height; i < nv21bytes.length; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
        }
    }

    private void quiThreadHandler(HandlerThread thread){
        if (thread==null){
            return;
        }
        if ( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2){
            thread.quitSafely();
        }else {
            thread.quit();
        }
    }


    private ByteBuffer getInputBuffer(MediaCodec codec,int index){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return codec.getInputBuffer(index);
        }else{
            return codec.getInputBuffers()[index];
        }
    }
    private ByteBuffer getOutputBuffer(MediaCodec codec,int index){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return codec.getOutputBuffer(index);
        }else{
            return codec.getOutputBuffers()[index];
        }
    }
    private int checkColorFormat(String mime){
        if(Build.MODEL.equals("HUAWEI P6-C00")){
            convertType=DataConvert.BGRA_YUV420SP;
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        }
        for (int i = 0; i< MediaCodecList.getCodecCount(); i++){
            MediaCodecInfo info= MediaCodecList.getCodecInfoAt(i);
            if(info.isEncoder()){
                String[] types=info.getSupportedTypes();
                for (String type:types){
                    if(type.equals(mime)){
                        Log.e("YUV","type-->"+type);
                        MediaCodecInfo.CodecCapabilities c=info.getCapabilitiesForType(type);
                        Log.e("YUV","color-->"+ Arrays.toString(c.colorFormats));
                        for (int j=0;j<c.colorFormats.length;j++){
                            if (c.colorFormats[j]==MediaCodecInfo.CodecCapabilities
                                    .COLOR_FormatYUV420Planar){
                                convertType=DataConvert.RGBA_YUV420P;
                                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
                            }else if(c.colorFormats[j]==MediaCodecInfo.CodecCapabilities
                                    .COLOR_FormatYUV420SemiPlanar){
                                convertType=DataConvert.RGBA_YUV420SP;
                                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
                            }
                        }
                    }
                }
            }
        }
        convertType=DataConvert.RGBA_YUV420SP;
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    }

}
