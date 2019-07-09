package com.serenegiant.usb.common;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.example.jade.ZipFiles;
import com.example.jade.jade_tools;
import com.example.motiondet.MtionDet3;
import com.google.gson.reflect.TypeToken;
import com.jadehh.JsonUtil;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBSize;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import com.serenegiant.usb.encoder.RecordParams;

import com.serenegiant.usb.encoder.biz.H264EncodeConsumer;
import com.serenegiant.usb.encoder.biz.Mp4MediaMuxer;
import com.serenegiant.usb.widget.CameraViewInterface;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.zip.ZipFile;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoWriter;

/**
 * Camera业务处理抽象类
 */
public abstract class AbstractUVCCameraHandler extends Handler {

    private static final boolean DEBUG = true;    // TODO set false on release
    private static final String TAG = "AbsUVCCameraHandler";


    // 对外回调接口
    public interface CameraCallback {
        public void onOpen();

        public void onClose();

        public void onStartPreview();

        public void onStopPreview();

        public void onStartRecording();

        public void onStopRecording();

        public void onError(final Exception e);
    }
    public static OnEncodeResultListener mListener;
    public static OnPreViewResultListener mPreviewListener;

    public interface OnEncodeResultListener {
        void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type);

        void onRecordResult(String videoPath);
    }

    public interface OnPreViewResultListener {
        void onPreviewResult(byte[] data);
    }

    public interface OnCaptureListener {
        void onCaptureResult(String picPath);
    }

    private static final int MSG_OPEN = 0;
    private static final int MSG_CLOSE = 1;
    private static final int MSG_PREVIEW_START = 2;
    private static final int MSG_PREVIEW_STOP = 3;
    private static final int MSG_CAPTURE_START = 5;
    private static final int MSG_CAPTURE_STOP = 6;
    private static final int MSG_MEDIA_UPDATE = 7;
    private static final int MSG_RELEASE = 9;
    private static final int MSG_CAMERA_FOUCS = 10;
    private static final int MSG_CHANGE_WEIGHT = 11;
    private static final int MSG_CHANGE_WEIGHT_VALUE = 12;
    private static final int MSG_CAMERA_EXPOSURE_MODE = 13;
    private static final int MSG_CAMERA_EXPOSURE_VALUE = 14;
    private static final int MSG_INIT_MOTION = 15;


    private final WeakReference<CameraThread> mWeakThread;
    private volatile boolean mReleased;



    protected AbstractUVCCameraHandler(final CameraThread thread) {
        mWeakThread = new WeakReference<CameraThread>(thread);
    }

    public int getWidth() {
        final CameraThread thread = mWeakThread.get();
        return thread != null ? thread.getWidth() : 0;
    }

    public int getHeight() {
        final CameraThread thread = mWeakThread.get();
        return thread != null ? thread.getHeight() : 0;
    }

    public boolean isOpened() {
        final CameraThread thread = mWeakThread.get();
        return thread != null && thread.isCameraOpened();
    }

    public boolean isPreviewing() {
        final CameraThread thread = mWeakThread.get();
        return thread != null && thread.isPreviewing();
    }

    public boolean isRecording() {
        final CameraThread thread = mWeakThread.get();
        return thread != null && thread.isRecording();
    }

    protected boolean isCameraThread() {
        final CameraThread thread = mWeakThread.get();
        return thread != null && (thread.getId() == Thread.currentThread().getId());
    }

    protected boolean isReleased() {
        final CameraThread thread = mWeakThread.get();
        return mReleased || (thread == null);
    }

    protected void checkReleased() {
        if (isReleased()) {
            throw new IllegalStateException("already released");
        }
    }

    public void open(final USBMonitor.UsbControlBlock ctrlBlock) {
        checkReleased();
        sendMessage(obtainMessage(MSG_OPEN, ctrlBlock));
    }

    public void close() {
        if (DEBUG) Log.v(TAG, "close:");
        if (isOpened()) {
            stopPreview();
            sendEmptyMessage(MSG_CLOSE);
        }
        if (DEBUG) Log.v(TAG, "close:finished");
    }

    // 切换分辨率
    public void resize(final int width, final int height) {
        checkReleased();
        throw new UnsupportedOperationException("does not support now");
    }


    // 开启Camera预览
    public void startPreview(final Object surface) {
        checkReleased();
        if (!((surface instanceof SurfaceHolder) || (surface instanceof Surface) || (surface instanceof SurfaceTexture))) {
            throw new IllegalArgumentException("surface should be one of SurfaceHolder, Surface or SurfaceTexture: " + surface);
        }

        sendMessage(obtainMessage(MSG_PREVIEW_START, surface));
    }

    public void setOnPreViewResultListener(OnPreViewResultListener listener) {
        AbstractUVCCameraHandler.mPreviewListener = listener;
    }

    // 关闭Camera预览
    public void stopPreview() {
        if (DEBUG) Log.v(TAG, "stopPreview:");
        removeMessages(MSG_PREVIEW_START);
        if (isRecording()) {
            stopRecording();
        }
        if (isPreviewing()) {
            final CameraThread thread = mWeakThread.get();
            if (thread == null) return;
            synchronized (thread.mSync) {
                sendEmptyMessage(MSG_PREVIEW_STOP);
                if (!isCameraThread()) {
                    try {
                        thread.mSync.wait();
                    } catch (final InterruptedException e) {
                    }
                }
            }
        }
        if (DEBUG) Log.v(TAG, "stopPreview:finished");
    }

    // 开始录制
    public void startRecording(final RecordParams params, OnEncodeResultListener listener) {
        AbstractUVCCameraHandler.mListener = listener;
        checkReleased();
        sendMessage(obtainMessage(MSG_CAPTURE_START, params));
    }

    // 停止录制
    public void stopRecording() {
        sendEmptyMessage(MSG_CAPTURE_STOP);
    }

    public void startCameraFoucs() {
        sendEmptyMessage(MSG_CAMERA_FOUCS);
    }
    public List<USBSize> getSupportedPreviewSizes() {
        return mWeakThread.get().getSupportedSizes();
    }

    public void release() {
        mReleased = true;
        close();
        sendEmptyMessage(MSG_RELEASE);
    }
    protected void updateMedia(final String path) {
        sendMessage(obtainMessage(MSG_MEDIA_UPDATE, path));
    }
    //设置曝光Mode
    public void setCameraExposureMode(int mode){
        sendMessage(obtainMessage(MSG_CAMERA_EXPOSURE_MODE,mode));
    }
    //设置曝光值
    public void setCameraExposureValue(int value){
        sendMessage(obtainMessage(MSG_CAMERA_EXPOSURE_VALUE,value));
    }
    //改变重量
    public void changeWeight(){
        sendEmptyMessage(MSG_CHANGE_WEIGHT);
    }
    //改变重量的值
    public void changeWeightValue(double[] weight){
        sendMessage(obtainMessage(MSG_CHANGE_WEIGHT_VALUE,weight));
    }

    //初始化检测器的参数
    public void initMtionParameter(int[] params){
        sendMessage(obtainMessage(MSG_INIT_MOTION,params));
    }


    @Override
    public void handleMessage(final Message msg) {
        final CameraThread thread = mWeakThread.get();
        if (thread == null) return;
        switch (msg.what) {
            case MSG_OPEN:
                thread.handleOpen((USBMonitor.UsbControlBlock) msg.obj);
                break;
            case MSG_CLOSE:
                thread.handleClose();
                break;
            case MSG_PREVIEW_START:
                thread.handleStartPreview(msg.obj);
                break;
            case MSG_PREVIEW_STOP:
                thread.handleStopPreview();
                break;
            case MSG_CAPTURE_START:
                thread.handleStartPusher((RecordParams) msg.obj);
                break;
            case MSG_CAPTURE_STOP:
                thread.handleStopPusher();
                break;
            case MSG_MEDIA_UPDATE:
                thread.handleUpdateMedia((String) msg.obj);
                break;
            case MSG_RELEASE:
                thread.handleRelease();
                break;
            // 自动对焦
            case MSG_CAMERA_FOUCS:
                thread.handleCameraFoucs();
                break;
            //改变重量
            case MSG_CHANGE_WEIGHT:
                thread.handleChangeWeight();
                break;
            case MSG_CHANGE_WEIGHT_VALUE:
                thread.handleChangeWeightValue((double[]) msg.obj);
                break;
            //设置曝光值
            case MSG_CAMERA_EXPOSURE_MODE:
                thread.handleCameraExposureMode((int)msg.obj);
                break;
            case MSG_CAMERA_EXPOSURE_VALUE:
                thread.handleCameraExposureValue((int)msg.obj);
                break;
            case MSG_INIT_MOTION:
                thread.handleInitMotionParmas((int [])msg.obj);
                break;
            default:
                throw new RuntimeException("unsupported message:what=" + msg.what);
        }
    }

    public static final class CameraThread extends Thread {
        private static final String TAG_THREAD = "CameraThread";
        private final Object mSync = new Object();
        private final Class<? extends AbstractUVCCameraHandler> mHandlerClass;
        private final WeakReference<Activity> mWeakParent;
        private final WeakReference<CameraViewInterface> mWeakCameraView;
        private final int mEncoderType;
        private final Set<CameraCallback> mCallbacks = new CopyOnWriteArraySet<CameraCallback>();
        private int mWidth, mHeight, mPreviewMode;
        private float mBandwidthFactor;
        private boolean mIsPreviewing;
        private boolean mIsRecording;

        private int index;
        private BlockingQueue<QueueObject> _img_to_movement_detect_que;
        private int weights_index;

        private AbstractUVCCameraHandler mHandler;
        // 处理与Camera相关的逻辑，比如获取byte数据流等
        private UVCCamera mUVCCamera;

        private Mp4MediaMuxer mMuxer;


        private String videoPath;
        private String savePath;
        private String videoName;

        private NV12ToMat nv12Utils;
        private boolean isStopping;
        private ArrayList<double[]> weights_list;
        private ArrayList<Integer> weights_index_list;
        private ArrayList<Integer> TIME_STAMP;


        private ArrayList<byte []> frame_cache;
        private ArrayList<Integer> frame_id_cache;
        private ArrayList<Integer> weights_id_cache;
        private MtionDet3 det;
        private jade_tools jTools;
        private JSONObject jsonObject;

        /**
         * 构造方法
         * <p>
         * clazz 继承于AbstractUVCCameraHandler
         * parent Activity子类
         * cameraView 用于捕获静止图像
         * encoderType 0表示使用MediaSurfaceEncoder;1表示使用MediaVideoEncoder, 2表示使用MediaVideoBufferEncoder
         * width  分辨率的宽
         * height 分辨率的高
         * format 颜色格式，0为FRAME_FORMAT_YUYV；1为FRAME_FORMAT_MJPEG
         * bandwidthFactor
         */
        CameraThread(final Class<? extends AbstractUVCCameraHandler> clazz,
                     final Activity parent, final CameraViewInterface cameraView,
                     final int encoderType, final int width, final int height, final int format,
                     final float bandwidthFactor) {

            super("CameraThread");
            mHandlerClass = clazz;
            mEncoderType = encoderType;
            mWidth = width;
            mHeight = height;
            mPreviewMode = format;
            mBandwidthFactor = bandwidthFactor;
            mWeakParent = new WeakReference<>(parent);
            mWeakCameraView = new WeakReference<>(cameraView);
            nv12Utils = new NV12ToMat(parent);
            mIsRecording = false;
        }

        @Override
        protected void finalize() throws Throwable {
            Log.i(TAG, "CameraThread#finalize");
            super.finalize();
        }

        public AbstractUVCCameraHandler getHandler() {
            if (DEBUG) Log.v(TAG_THREAD, "getHandler:");
            synchronized (mSync) {
                if (mHandler == null)
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                    }
            }
            return mHandler;
        }

        public int getWidth() {
            synchronized (mSync) {
                return mWidth;
            }
        }

        public int getHeight() {
            synchronized (mSync) {
                return mHeight;
            }
        }

        public boolean isCameraOpened() {
            synchronized (mSync) {
                return mUVCCamera != null;
            }
        }

        public boolean isPreviewing() {
            synchronized (mSync) {
                return mUVCCamera != null && mIsPreviewing;
            }
        }

        public boolean isRecording() {
            synchronized (mSync) {
                return (mUVCCamera != null) && (mH264Consumer != null);
            }
        }


        public boolean isEqual(final UsbDevice device) {
            return (mUVCCamera != null) && (mUVCCamera.getDevice() != null) && mUVCCamera.getDevice().equals(device);
        }

        public void handleOpen(final USBMonitor.UsbControlBlock ctrlBlock) {
            if (DEBUG) Log.v(TAG_THREAD, "handleOpen:");
            handleClose();
            try {
                final UVCCamera camera = new UVCCamera();
                camera.open(ctrlBlock);
                synchronized (mSync) {
                    mUVCCamera = camera;
                }
                callOnOpen();
            } catch (final Exception e) {
                callOnError(e);
            }
            if (DEBUG)
                Log.i(TAG, "supportedSize:" + (mUVCCamera != null ? mUVCCamera.getSupportedSize() : null));
        }

        public void handleClose() {
            if (DEBUG) Log.v(TAG_THREAD, "handleClose:");
            handleStopPusher();
            final UVCCamera camera;
            synchronized (mSync) {
                camera = mUVCCamera;
                mUVCCamera = null;
            }
            if (camera != null) {
                camera.stopPreview();
                camera.destroy();
                callOnClose();
            }
        }

        public void handleStartPreview(final Object surface) {
            if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:");
            if ((mUVCCamera == null) || mIsPreviewing) return;
            try {
                mUVCCamera.setPreviewSize(mWidth, mHeight, 1, 31, mPreviewMode, mBandwidthFactor);
                mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP);
            } catch (final IllegalArgumentException e) {
                try {
                    // fallback to YUV mode
                    mUVCCamera.setPreviewSize(mWidth, mHeight, 1, 31, UVCCamera.DEFAULT_PREVIEW_MODE, mBandwidthFactor);
                } catch (final IllegalArgumentException e1) {
                    callOnError(e1);
                    return;
                }
            }
            if (surface instanceof SurfaceHolder) {
                mUVCCamera.setPreviewDisplay((SurfaceHolder) surface);
            }
            if (surface instanceof Surface) {
                mUVCCamera.setPreviewDisplay((Surface) surface);
            } else {
                mUVCCamera.setPreviewTexture((SurfaceTexture) surface);
            }
            mUVCCamera.startPreview();
            mUVCCamera.updateCameraParams();
            synchronized (mSync) {
                mIsPreviewing = true;
            }
            callOnStartPreview();
        }

        public void handleStopPreview() {
            if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:");
            if (mIsPreviewing) {
                if (mUVCCamera != null) {
                    mUVCCamera.stopPreview();
                    mUVCCamera.setFrameCallback(null, 0);
                }
                synchronized (mSync) {
                    mIsPreviewing = false;
                    mSync.notifyAll();
                }
                callOnStopPreview();
            }
            if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:finished");
        }

        private H264EncodeConsumer mH264Consumer;
        public void handleStartPusher(RecordParams params) {
            if ((mUVCCamera == null) || (mH264Consumer != null))
                return;
            if (params != null) {
                videoPath = params.getRecordPath();
                savePath = params.getSavePath();
                jsonObject = params.getJsonObject();
                videoName = params.getVideoName();
                mMuxer = new Mp4MediaMuxer(params.getRecordPath(),
                        params.getRecordDuration() * 60 * 1000, params.isVoiceClose());
            }
            isStopping = false;
            //开启线程处理图像
            Thread detThread = detFrame();
            detThread.start();
            // 启动视频编码线程
            startVideoRecord();
            callOnStartRecording();
        }

        public void handleStopPusher() {
            isStopping = true;
        }
        public void stopRecord(){
            // 停止混合器
            if (mMuxer != null) {
                mMuxer.release();
                mMuxer = null;
                Log.i(TAG, TAG + "---->停止本地录制");
            }
            stopVideoRecord();
            mWeakCameraView.get().setVideoEncoder(null);
            // you should not wait here
            callOnStopRecording();
            // 返回路径
            if (mListener != null) {
                writeConfig();
                zipFiles();
                deleteFiles(savePath.substring(0,savePath.length()-1));
                mListener.onRecordResult(savePath.substring(0, savePath.length() - 1) + ".zip");
            }
            clear();
        }

        public void clear(){
            weights_index = 0;
            index = 0;
            isStopping = false;
            weights_list.clear();
            weights_index_list.clear();
            _img_to_movement_detect_que.clear();
            frame_cache.clear();
            frame_id_cache.clear();
            weights_id_cache.clear();
        }
        private void startVideoRecord() {
            mH264Consumer = new H264EncodeConsumer(getWidth(), getHeight());
            mH264Consumer.setOnH264EncodeResultListener(new H264EncodeConsumer.OnH264EncodeResultListener() {
                @Override
                public void onEncodeResult(byte[] data, int offset, int length, long timestamp) {
                    if (mListener != null) {
                        mListener.onEncodeResult(data, offset, length, timestamp, 1);
                    }
                }
            });
            mH264Consumer.start();
            // 添加混合器
            if (mMuxer != null) {
                if (mH264Consumer != null) {
                    mH264Consumer.setTmpuMuxer(mMuxer);
                }
            }
        }

        private void stopVideoRecord() {
            if (mH264Consumer != null) {
                mH264Consumer.exit();
                mH264Consumer.setTmpuMuxer(null);
                try {
                    Thread t2 = mH264Consumer;
                    mH264Consumer = null;
                    if (t2 != null) {
                        t2.interrupt();
                        t2.join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        //写入视频的线程
        private Thread detFrame() {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        QueueObject object = _img_to_movement_detect_que.poll();
                        if (object != null) {
                            Mat img = object.img;
                            byte[] yuv = object.data;
                            int index = object.index;
                            int weight_index = object.weight_index;
                            frame_cache.add(yuv);
                            frame_id_cache.add(index);
                            weights_id_cache.add(weight_index);
                            if (frame_cache.size() > 11) {
                                frame_cache.remove(0);
                                frame_id_cache.remove(0);
                                weights_id_cache.remove(0);
                            }
                            Mat dst = new Mat();
                            Imgproc.cvtColor(img,dst,Imgproc.COLOR_BGRA2RGB);
                            List<Integer> frame_id_list = det.detect(dst, index);
                            for (int j = 0; j < frame_id_list.size(); j++) {
                                int frame_id = frame_id_list.get(j);
                                int id = frame_id_cache.indexOf(frame_id);
                                byte[] b = frame_cache.get(id).clone();
                                int weight_idx = weights_id_cache.get(id);
                                writeVideo(b,index,weight_idx);

                            }
                        }
                        if (object == null && isStopping){
                            if (DEBUG){
                                Log.e(TAG,"停止录像");
                            }
                            stopRecord();
                            break;
                        }
                    }
                }
            };
            return thread;
        }
        public void writeVideo(byte[] yuv, int index, int weights_index_tem){
            long start_time = jTools.getTimeStamp();
            weights_index_list.add(weights_index_tem);
            TIME_STAMP.add(index + 10000);
            mH264Consumer.setRawYuv(yuv,getWidth(),getHeight());
            long end_time = jTools.getTimeStamp();
            Log.i(TAG, "从队列中取出满足条件的图片写入视频中,写入耗时" + String.valueOf(end_time - start_time) + "ms");
        }

        private final IFrameCallback mIFrameCallback = new IFrameCallback() {
            @Override
            public void onFrame(final ByteBuffer frame) {
                //视频编码
                if (mH264Consumer != null && !isStopping) {
                    int len = frame.capacity();
                    final byte[] yuv = new byte[len];
                    frame.get(yuv);
                    Mat a = RGBX(yuv);
                    if (DEBUG){
                        Log.i(TAG,"从Frame获取第"+String.valueOf(index)+"帧图片"+"此时Weight_index="+String.valueOf(weights_index));
                    }

                    QueueObject object = new QueueObject(a,yuv, index, weights_index);
                    _img_to_movement_detect_que.add(object);
                    index = index + 1;

                }
            }
        };
        //    //视频解码
        private Mat RGBX(byte[] data) {
            Mat dst = nv12Utils.nv21ToMat(data,mWidth,mHeight);
            return dst;
        }
        //写入配置文件
        private void writeConfig() {
            try {
                //数据模型
                ZipConfig zipConfig = new ZipConfig();
                //找到文件路径
                String filepath = jsonObject.getString("DRIC_AREAS_V0");
                //转移文件
                File file = new File(filepath);
                if (file.exists()) {
                    Log.e(TAG, "DRIC_AREAS_V0 :" + filepath);
                    String areafile = savePath + "/container.png";
                    jTools.copyFile(filepath, areafile);
                    zipConfig.setDRIC_AREAS_V0("container.png");
                }
                //时间
                zipConfig.setTIME_STAMP(TIME_STAMP);
                //视频文件名称
                zipConfig.setIMG_V0_PATH(videoName);
                //重量index list
                zipConfig.setWEIGHT_MEASURE_INDEX(weights_index_list);
                //重量list
                zipConfig.setWEIGHT_CHANGES(weights_list);
                //商品key
                List<String> keys;
                keys = JsonUtil.getInstance().parserList(jsonObject.get("GOODS_KEY").toString(), new TypeToken<List<String>>() {
                });
                zipConfig.setGOODS_KEY(keys);
                //商品重量
                List<Double> weights;
                weights = JsonUtil.getInstance().parserList(jsonObject.get("GOODS_WEIGHT").toString(), new TypeToken<List<Double>>() {
                });
                zipConfig.setGOODS_WEIGHT(weights);
                //用户配置
                ZipConfig.UserConfig userConfig = JsonUtil.getInstance().getGson().fromJson(jsonObject.get("USER_CONFIG").toString(), ZipConfig.UserConfig.class);
                zipConfig.setUSER_CONFIG(userConfig);

                jTools.writeTxtToFile(JsonUtil.getInstance().getGson().toJson(zipConfig), savePath, "config.json");

            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        private void zipFiles() {
            try {
                ZipFiles.zip(savePath.substring(0, savePath.length() - 1), savePath.substring(0, savePath.length() - 1) + ".zip");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        private void deleteFiles(String path) {
            File dir = new File(path);
            jTools.deleteDirWihtFile(dir);
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        public void handleUpdateMedia(final String path) {
            if (DEBUG) Log.v(TAG_THREAD, "handleUpdateMedia:path=" + path);
            final Activity parent = mWeakParent.get();
            final boolean released = (mHandler == null) || mHandler.mReleased;
            if (parent != null && parent.getApplicationContext() != null) {
                try {
                    if (DEBUG) Log.i(TAG, "MediaScannerConnection#scanFile");
                    MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{path}, null, null);
                } catch (final Exception e) {
                    Log.e(TAG, "handleUpdateMedia:", e);
                }
                if (released || parent.isDestroyed())
                    handleRelease();
            } else {
                Log.w(TAG, "MainActivity already destroyed");
                handleRelease();
            }
        }

        public void handleRelease() {
            if (DEBUG) Log.v(TAG_THREAD, "handleRelease:mIsRecording=" + mIsRecording);
            handleClose();
            mCallbacks.clear();
            if (!mIsRecording) {
                mHandler.mReleased = true;
                Looper.myLooper().quit();
            }
            if (DEBUG) Log.v(TAG_THREAD, "handleRelease:finished");
        }

        // 自动对焦
        public void handleCameraFoucs() {
            if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:");
            if ((mUVCCamera == null) || !mIsPreviewing)
                return;
            mUVCCamera.setAutoFocus(true);
        }

        // 获取支持的分辨率
        public List<USBSize> getSupportedSizes() {
            if ((mUVCCamera == null) || !mIsPreviewing)
                return null;
            return mUVCCamera.getSupportedSizeList();
        }
        //设置曝光Mode
        public void handleCameraExposureMode(int mode){
            if (DEBUG) Log.v(TAG_THREAD, "handleCameraExposureMode");
            if ((mUVCCamera == null) || !mIsPreviewing)
                return;
            mUVCCamera.setExposureMode(mode);
        }
        //设置曝光值
        public void handleCameraExposureValue(int value){
            if (DEBUG) Log.v(TAG_THREAD, "handleCameraExposureMode");
            if ((mUVCCamera == null) || !mIsPreviewing)
                return;
            mUVCCamera.setExposureValue(value);
        }

        //改变重量索引
        public void handleChangeWeight(){
            if (DEBUG) Log.v(TAG_THREAD, "handleChangeWeight:");
            if ((mUVCCamera == null) || !mIsPreviewing ||isStopping)
                return;
            weights_index = weights_index + 1;
            Log.v("handleChangeWeight",String.valueOf(weights_index));
        }
        public void handleChangeWeightValue(double[] weight){
            if (DEBUG) Log.v(TAG_THREAD, "handleChangeWeightValue:");
            if ((mUVCCamera == null) || !mIsPreviewing ||isStopping)
                return;
            weights_list.add(weight);
        }
        public void handleInitMotionParmas(int[] parmas){
            if (DEBUG) Log.v(TAG_THREAD, "handleInitMotionParmas:");
            if (mUVCCamera == null || !mIsPreviewing){
                return;
            }
            det = new MtionDet3();
            det.init(parmas[0],parmas[1],parmas[2],parmas[3],parmas[4]);
            jTools = new jade_tools();
            _img_to_movement_detect_que = new ArrayBlockingQueue(100);
            weights_index = 0;
            index = 0;
            isStopping = false;
            weights_list = new ArrayList<>();
            weights_index_list = new ArrayList<>();
            frame_cache = new ArrayList<>();
            frame_id_cache = new ArrayList<>();
            weights_id_cache = new ArrayList<>();
            TIME_STAMP = new ArrayList<>();
        }
        public void run() {
            Looper.prepare();
            AbstractUVCCameraHandler handler = null;
            try {
                final Constructor<? extends AbstractUVCCameraHandler> constructor = mHandlerClass.getDeclaredConstructor(CameraThread.class);
                handler = constructor.newInstance(this);
            } catch (final NoSuchMethodException e) {
                Log.w(TAG, e);
            } catch (final IllegalAccessException e) {
                Log.w(TAG, e);
            } catch (final InstantiationException e) {
                Log.w(TAG, e);
            } catch (final InvocationTargetException e) {
                Log.w(TAG, e);
            }
            if (handler != null) {
                synchronized (mSync) {
                    mHandler = handler;
                    mSync.notifyAll();
                }
                Looper.loop();
                if (mHandler != null) {
                    mHandler.mReleased = true;
                }
            }
            mCallbacks.clear();
            synchronized (mSync) {
                mHandler = null;
                mSync.notifyAll();
            }
        }

        private void callOnOpen() {
            for (final CameraCallback callback : mCallbacks) {
                try {
                    callback.onOpen();
                } catch (final Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG, e);
                }
            }
        }

        private void callOnClose() {
            for (final CameraCallback callback : mCallbacks) {
                try {
                    callback.onClose();
                } catch (final Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG, e);
                }
            }
        }

        private void callOnStartPreview() {
            for (final CameraCallback callback : mCallbacks) {
                try {
                    callback.onStartPreview();
                } catch (final Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG, e);
                }
            }
        }

        private void callOnStopPreview() {
            for (final CameraCallback callback : mCallbacks) {
                try {
                    callback.onStopPreview();
                } catch (final Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG, e);
                }
            }
        }

        private void callOnStartRecording() {
            for (final CameraCallback callback : mCallbacks) {
                try {
                    callback.onStartRecording();
                } catch (final Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG, e);
                }
            }
        }

        private void callOnStopRecording() {
            for (final CameraCallback callback : mCallbacks) {
                try {
                    callback.onStopRecording();
                } catch (final Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG, e);
                }
            }
        }

        private void callOnError(final Exception e) {
            for (final CameraCallback callback : mCallbacks) {
                try {
                    callback.onError(e);
                } catch (final Exception e1) {
                    mCallbacks.remove(callback);
                    Log.w(TAG, e);
                }
            }
        }
    }

}
