package com.example.zhangbaokui.rtp;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import com.example.zhangbaokui.rtp.jlibrtp.DataFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class VideoDecoder {
    private static final String TAG = "VideoDecoder";
    private MediaCodec codec;
    private int width,height;
    private Surface surface;
    private String MIME_TYPE = "video/avc";
    private long preTime = 0;
    private List<DataFrame> dataFrames;

    private HandlerThread encoderHandlerThread;
    private Handler handler;
    private static final int QUIT = 1;
    private static final int DATA = 2;



    public void deco(final byte[] data, final int len, final long time){
        Log.i(TAG,"deco:"+time);
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    runTask(data,len,time);
                }catch (Exception e){
                    Log.e(TAG,"runTask Exception:"+e.getMessage());
                }
            }
        });
    }


    public void getData(DataFrame frame){
        if (frame==null||handler==null){
            return;
        }
        if (dataFrames == null){
            dataFrames = new ArrayList<>();
        }
        if (preTime==0){
            preTime = frame.rtpTimestamp();
        }
        if (frame.rtpTimestamp() == preTime){
            dataFrames.add(frame);
        }
        if (frame.marked()){
            preTime = 0;
            List<DataFrame> temp = new ArrayList<>();
            temp.addAll(dataFrames);
            dataFrames.clear();
            Message message = Message.obtain();
            message.what = DATA;
            message.obj = temp;
            handler.sendMessage(message);
        }

    }

    public void start(Surface surface,int width,int height){
        this.width = width;
        this.height = height;
        this.surface = surface;
        encoderHandlerThread = new HandlerThread("encoder");
        encoderHandlerThread.start();
        handler = new Handler(encoderHandlerThread.getLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case QUIT:
                        encoderHandlerThread.quit();
                        break;
                    case DATA:
                        getFrams((List<DataFrame>) msg.obj);
                        break;
                }
            }
        };
    }

    public void tryConfig(int width,int height,byte[] sps,byte[] pps){
        try {
            codec = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,
                width, height);
        if(sps != null || pps != null){
            mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
            mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
        }
        codec.configure(mediaFormat, surface, null, 0);
//		codec.setVideoScalingMode(/*2*/type);
        codec.start();
    };

    byte[] data = new byte[50*1024];
    private void getFrams(List<DataFrame> frames){
        if (frames==null||frames.isEmpty()){
            return;
        }
//        Collections.sort(frames, new Comparator<DataFrame>() {
//            @Override
//            public int compare(DataFrame o1, DataFrame o2) {
//                return o2.sequenceNumbers()[0] - o1.sequenceNumbers()[0];
//            }
//        });
        int len = 0;
        byte[] temp;
        DataFrame frame = null;
        byte[][] bytes = new byte[frames.size()][];
        for (int i = 0;i<frames.size();i++){
            frame = frames.get(i);
            temp = frame.getConcatenatedData();
            bytes[i] = temp;
            len += temp.length;
        }
        byte[] data = new byte[len];
        data = DataHandle.com(bytes);
        try {
            runTask(data,len,frame.rtpTimestamp());
        }catch (Exception e){
            Log.e(TAG,"runTask Exception:"+e.getMessage());
        }
    }

    private void findPPs(byte[] data,int length){
        if (!isStartFlag(data,length,0)||length<10){
            return;
        }
        int pspStart = 6;
        int last = length - 3;
        for (;pspStart<last;){
            if (isStartFlag(data,length,pspStart)){
                break;
            }else {
                pspStart++;
            }
        }
        byte[] pps = new byte[pspStart-5];
        System.arraycopy(data,5,pps,0,pps.length);
        int pspEnd = pspStart+3;
        for (;pspEnd<length;){
            if (isStartFlag(data,length,pspEnd)){
                break;
            }else{
                pspEnd++;
            }
        }
        byte[] sps = new byte[pspEnd - pspStart-5];
        System.arraycopy(data,pspStart+5,sps,0,sps.length);
        tryConfig(width,height,pps,sps);
    }

    private boolean isStartFlag(byte[] data,int length,int index){
        if (index+3<length){
            //00 00 00 01 是一个NALU的开头
            return data[index]==0&&data[index+1]==0&&data[index+2]==0&&data[index+3]==1;
        }
        return false;
    }


    private void runTask(byte[] data,int length,long time){
        //Log.i("")
        //第一帧数据一般是PPS与SPS
        if (data[4] == 0x67){
            findPPs(data,length);
            //return;
        }
        if (codec==null){
            return;
        }
        int index = codec.dequeueInputBuffer(0);
        ByteBuffer inputBuffer = getInputBuffer(codec,index);
        inputBuffer.clear();
        inputBuffer.put(data);
        codec.queueInputBuffer(index,0,length,time,0);

        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex  = codec.dequeueOutputBuffer(videoBufferInfo,0);
        switch (outputBufferIndex) {
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                Log.v(TAG, "format changed");
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                Log.v(TAG, "解码当前帧超时");
                sleep(10);
                break;
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                //outputBuffers = videoCodec.getOutputBuffers();
                Log.v(TAG, "output buffers changed");
                break;
            default:
                //直接渲染到Surface时使用不到outputBuffer
                //ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                //渲染
                codec.releaseOutputBuffer(outputBufferIndex, true);
                break;
        }
    }

    /**
     * 睡眠一段时间
     * @param time
     */
    private void sleep(long time){
        try {
            // Log.i(TAG,"xiu")
            Thread.sleep(time);
        }catch (Exception e){
            Log.e(TAG,"sleep Exception:"+e.getMessage());
        }
    }

    private ByteBuffer getInputBuffer(MediaCodec codec, int index){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return codec.getInputBuffer(index);
        }else{
            return codec.getInputBuffers()[index];
        }
    }

    public void stop(){
        if (handler!=null){
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        if (encoderHandlerThread!=null){
            encoderHandlerThread.quit();
            encoderHandlerThread = null;
        }
        if (codec!=null){
            codec.stop();
            codec.release();
            codec = null;
        }
    }


}
