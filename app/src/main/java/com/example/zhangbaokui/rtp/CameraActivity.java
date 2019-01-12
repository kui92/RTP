package com.example.zhangbaokui.rtp;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import java.io.IOException;
import java.util.List;

/**
 * @author : kui
 * date   : 2018/12/18  10:26
 * desc   :
 * version: 1.0
 */
public class CameraActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView surfaceView;
    private static final int FRONT = 1;//前置摄像头标记
    private static final int BACK = 2;//后置摄像头标记
    private int currentCameraType = -1;//当前打开的摄像头标记
    private SurfaceHolder holder;
    private Camera mCamera;
    private String TAG = "CameraActivity";
    private Camera.Size optimalPreviewSize;
    private int screenWidth,screenHeight;
    private int width,height;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        surfaceView = findViewById(R.id.surface);
        holder = surfaceView.getHolder();
        holder.addCallback(this);
        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        screenWidth = dm.widthPixels;         // 屏幕宽度（像素）
        screenHeight = dm.heightPixels;       // 屏幕高度（像素）
        findViewById(R.id.btn).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        //start();
                        break;
                    case MotionEvent.ACTION_UP:
                        stop();
                        break;
                }
                return false;
            }
        });
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera){
        Log.i(TAG,"onPreviewFrame:"+data);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        start();
    }


    private void start(){
        refreshCamera();
        initCamer(width,height);
    }

    private void stop(){
        relaseCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        relaseCamera();
    }

    private void initCamer(int width,int height){
        int displayOrientation = getDisplayOrientation();
        Log.e(TAG,"displayOrientation:"+displayOrientation);
        mCamera.setDisplayOrientation(displayOrientation);
        Camera.Parameters params = mCamera.getParameters();
        optimalPreviewSize = params.getPictureSize();
        Log.i(TAG,"width:"+optimalPreviewSize.width+"--height:"+optimalPreviewSize.height);
        optimalPreviewSize = getOptimalPreviewSize(mCamera.getParameters().getSupportedPreviewSizes(), width, height);
        /**
         * 下面两行注意，竖着拍的时候由于摄像头旋转了90度，宽高颠倒了
         */
        if (Math.abs(displayOrientation)==90){
            optimalPreviewSize.width = 1280;
            optimalPreviewSize.height = 720;
          /*  optimalPreviewSize.height = screenWidth;
            optimalPreviewSize.width = screenHeight;*/
        }
        Log.i(TAG,"width:"+optimalPreviewSize.width+"--height:"+optimalPreviewSize.height);
        params.setPictureSize(optimalPreviewSize.width, optimalPreviewSize.height);
        params.setPreviewFormat(ImageFormat.NV21);//default默认为21，所有手机均支持NV21
        params.setPreviewSize(optimalPreviewSize.width,optimalPreviewSize.height);//设置预览分辨率
        params.setPreviewFrameRate(25);
        params.setPictureFormat(ImageFormat.JPEG);
        if(currentCameraType == BACK){//后置需要自动对焦，否则人脸采集照片模糊
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        mCamera.setParameters(params);
    }

    private void refreshCamera() {
        if (holder.getSurface() == null) {
            //preview surface does not exist
            return;
        }
        if (mCamera==null){
            mCamera = openCamera(BACK);
        }else {
            mCamera.stopPreview();
        }
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private Camera openCamera(int type){
        int frontIndex =-1;
        int backIndex = -1;
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for(int cameraIndex = 0; cameraIndex<cameraCount; cameraIndex++){
            Camera.getCameraInfo(cameraIndex, info);
            if(info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT){
                frontIndex = cameraIndex;
            }else if(info.facing == Camera.CameraInfo.CAMERA_FACING_BACK){
                backIndex = cameraIndex;
            }
        }
        currentCameraType = type;
        if(type == FRONT && frontIndex != -1){
            return Camera.open(frontIndex);
        }else if(type == BACK && backIndex != -1){
            return Camera.open(backIndex);
        }
        return null;
    }

    //获取最佳的分辨率 而且是16：9的
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        Log.i(TAG,"viewHW:"+w+"--H:"+h);
        final double ASPECT_TOLERANCE = 0.75;
        double targetRatio = (double) w / h;
        if (sizes == null){
            return null;
        }

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            Log.i(TAG,"size width:"+size.width+"--sizeHeight:"+size.height+"--ratio:"+ratio);
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE){
                continue;
            }
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
    //这里是一小段算法算出摄像头转多少都和屏幕方向一致
    private int getDisplayOrientation() {
        WindowManager windowManager = getWindowManager();
        Display defaultDisplay = windowManager.getDefaultDisplay();
        int orientation = defaultDisplay.getOrientation();
        int degress = 0;
        switch (orientation) {
            case Surface.ROTATION_0:
                degress = 0;
                break;
            case Surface.ROTATION_90:
                degress = 90;
                break;
            case Surface.ROTATION_180:
                degress = 180;
                break;
            case Surface.ROTATION_270:
                degress = 270;
                break;
        }
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo);
        int result = (cameraInfo.orientation - degress + 360) % 360;
        return result;
    }

    private void relaseCamera(){
        if (mCamera!=null){
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


}
