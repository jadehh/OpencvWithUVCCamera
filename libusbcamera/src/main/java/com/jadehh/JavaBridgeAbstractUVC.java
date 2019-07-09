package com.jadehh;
import android.app.Activity;

import android.hardware.usb.UsbDevice;

import android.os.Environment;

import android.os.Looper;
import android.util.Log;
import android.view.View;




import org.json.JSONException;
import org.opencv.core.CvType;
import org.opencv.core.Size;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.utils.FileUtils;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.encoder.RecordParams;
import com.serenegiant.usb.widget.CameraViewInterface;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoWriter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;


import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.json.JSONObject;

import com.example.jade.jade_tools;



public class JavaBridgeAbstractUVC {
    private static final String ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
            + File.separator + "temp/";
    private String newRootPath = ""; //动态获取的

    private static final String TAG = "JavaBridgeUVC";
    private boolean isRequest;
    private static final boolean DEBUG = true;    // TODO set false on release
    private static final int PREVIEW_WIDTH = 1280;
    private static final int PREVIEW_HEIGHT = 720;

    private CameraInterface cameraListener;
    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;

    private static Activity activity;
    private jade_tools jTools;
    private int[] parmas;
    private JSONObject jsonObject;
    private double[] weight_init;
    private int exposureValue;

    public void init(View mTextureView, int line_y, int xmin, int ymin, int xmax, int ymax) {
        loadOpencv();
        mUVCCameraView = (CameraViewInterface) mTextureView;
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
        mCameraHelper.setDefaultPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        jTools = new jade_tools();
        int[] initparmas = {line_y,xmin,ymin,xmax,ymax};
        parmas = initparmas;
    }
    //打开摄像头
    public void open_camera(int cameraId,int exposureValue, double[] weight_begin, JSONObject jsonObject1) {
        jsonObject = jsonObject1;
        createFiles();
        exposureValue = exposureValue;
        mCameraHelper.initUSBMonitor(activity, mUVCCameraView, listener,cameraId);
        mCameraHelper.registerUSB();
        weight_init = weight_begin;

    }
    //录制视频
    public void recordVideo(){
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            Log.e(TAG,"sorry,camera open failed");
            return;
        }
        String videoName = jTools.getTime();
        String videoPath = newRootPath +  videoName;
        // if you want to record,please create RecordParams like this
        RecordParams params = new RecordParams();
        params.setSavePath(newRootPath);
        params.setRecordPath(videoPath);
        params.setJsonObject(jsonObject);
        params.setVideoName(videoName+".mp4");
        params.setRecordDuration(0);                        // 设置为0，不分割保存
        params.setVoiceClose(true);
        mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
            @Override
            public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                if (type == 1){
                    FileUtils.putFileStream(data, offset, length);
                }
                if(type == 0) {

                }
            }

            @Override
            public void onRecordResult(String videoPath) {
                Log.i(TAG,"videoPath = "+videoPath);
            }
        });
    }
    public void change_weight(){
        mCameraHelper.changeWeight();
    }
    public void change_weight_value(double[] weight){
        mCameraHelper.changeWeightValue(weight);
    }
    public void stop(double[] weight_end) {
        mCameraHelper.changeWeight();
        mCameraHelper.changeWeightValue(weight_end);
        FileUtils.releaseFile();
        mCameraHelper.stopPusher();
    }

    public interface CameraInterface {
        //摄像头的参数回调 0 为正常打开摄像头，
        //其余都为非正常打开摄像头
        public void onConnectCamera(boolean result);
        public void onDisconnectCamera(String path);
    }
    //USB摄像头的监听函数
    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {
        @Override
        public void onAttachDev(UsbDevice device,final int cameraId) {
            if (mCameraHelper == null || mCameraHelper.getUsbDeviceCount() == 0) {
                Log.e(TAG, "check no usb camera");
                if (cameraListener!=null){
                    cameraListener.onConnectCamera(false);
                }
                return;
            }
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(cameraId);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                Log.e(TAG, device.getDeviceName() + " is out");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                Log.e(TAG, "fail to connect,please check resolution params");
            }else{
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // 休眠500ms，等待Camera创建完毕
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // 开启预览
                        mCameraHelper.startPreview(mUVCCameraView);
                        mCameraHelper.setExposureMode(1);
                        mCameraHelper.setExposureValue(exposureValue);
                        mCameraHelper.initMotionParms(parmas);
                        mCameraHelper.changeWeightValue(weight_init);
                    }
                }).start();
            }
        }
        @Override
        public void onDisConnectDev(UsbDevice device) {

        }
    };
    private void createFiles() {
        try {
            jTools.createDir(ROOT_PATH);
            String fileName = jTools.getTime();
            newRootPath = ROOT_PATH + fileName + "/";
            try {
                jTools.createDir(newRootPath);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    //初始化回调函数
    public JavaBridgeAbstractUVC(final Activity context, final CameraInterface listener) {
        activity = context;
        if (DEBUG) Log.v(TAG, "USBMonitor:Constructor");
        if (listener == null)
            throw new IllegalArgumentException("OnDeviceConnectListener should not null.");
        cameraListener = listener;
    }
    //Load opencv的回调函数
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(activity) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    //Load Oencv库
    public void loadOpencv() {
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, activity, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
}
