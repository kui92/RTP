package com.example.zhangbaokui.rtp;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author kui
 * @date on 2019/1/3 16:52
 */
public class RtpActivity extends AppCompatActivity implements Camera.PreviewCallback, SurfaceHolder.Callback {

    public static final String IP = "ip";
    public static final String RTP_PORT = "rtp_port";
    @BindView(R.id.suface)
    SurfaceView sufacea;
    @BindView(R.id.start)
    Button start;
    @BindView(R.id.pause)
    Button pause;
    @BindView(R.id.suface2)
    SurfaceView suface2;

    private String ip;
    private int rtpPort, rtcpPort;

    private static final int FRONT = 1;//前置摄像头标记
    private static final int BACK = 2;//后置摄像头标记
    private int currentCameraType = -1;//当前打开的摄像头标记
    private SurfaceHolder holder;
    private Camera mCamera;
    private String TAG = "RtpActivity";
    private Camera.Size optimalPreviewSize;
    private int screenWidth, screenHeight;
    private int width, height;
    private VideoEncod encod;
    private Receiver receiver;
    private VideoDecoder videoDecoder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rtp);
        ButterKnife.bind(this);
        Intent intent = getIntent();
        ip = intent.getStringExtra(IP);
        rtpPort = intent.getIntExtra(RTP_PORT, 10000);
        rtcpPort = rtpPort + 1;
        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        encod = new VideoEncod();

        holder = suface2.getHolder();
        holder.addCallback(this);
        screenWidth = dm.widthPixels;         // 屏幕宽度（像素）
        screenHeight = dm.heightPixels;       // 屏幕高度（像素）
        receiver = new Receiver();
        videoDecoder = new VideoDecoder();
        receiver.setVideoDecoder(videoDecoder);
        encod.prepare(720, 480);

        suface2.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    @OnClick({R.id.start, R.id.pause})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.start:
                videoDecoder.start(sufacea.getHolder().getSurface(),720,480);
                encod.startVideo(ip, rtpPort);
                break;
            case R.id.pause:
                break;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        encod.feedData(data);
    }

    private void initCamer() {
        if (mCamera == null) {
            mCamera = openCamera(BACK);
        }
        Camera.Parameters params = mCamera.getParameters();
        params.setPictureSize(720, 480);
        params.setPreviewFormat(ImageFormat.NV21);//default默认为21，所有手机均支持NV21
        params.setPreviewSize(720, 480);//设置预览分辨率
        params.setPreviewFrameRate(25);
        params.setPictureFormat(ImageFormat.JPEG);
        if (currentCameraType == BACK) {//后置需要自动对焦，否则人脸采集照片模糊
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        mCamera.setParameters(params);
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "startPreview fail:");
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        initCamer();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        relaseCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        relaseCamera();
        encod.stopVideo();
        videoDecoder.stop();
        receiver.relase();
    }

    private void relaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            try {
                mCamera.setPreviewDisplay(null);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private Camera openCamera(int type) {
        int frontIndex = -1;
        int backIndex = -1;
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int cameraIndex = 0; cameraIndex < cameraCount; cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                frontIndex = cameraIndex;
            } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                backIndex = cameraIndex;
            }
        }
        currentCameraType = type;
        if (type == FRONT && frontIndex != -1) {
            return Camera.open(frontIndex);
        } else if (type == BACK && backIndex != -1) {
            return Camera.open(backIndex);
        }
        return null;
    }

}
