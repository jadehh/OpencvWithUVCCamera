package com.example.motiondet;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.example.jade.jade_tools;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class testMotion {
    private ArrayList<Mat> frame_cache;
    private ArrayList<Integer> frame_id_cache;
    private String TAG = "testMotion";
    public static final String ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
            + File.separator + "test/";
    private jade_tools jTools;
    private VideoWriter mVideoWriter;
    private VideoWriter mVideoWriterAll;
    private boolean isOpened;
    private  MtionDet3 det;
    private BlockingQueue<QueueObject> _img_to_movement_detect_que;
    private Activity activity;


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
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, activity, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void init(){
        loadOpencv();
        isOpened = true;
        det = new MtionDet3();
        //65
        det.init(50, 0, 410, 760, 720);
        jTools = new jade_tools();
        frame_cache = new ArrayList<>();
        frame_id_cache = new ArrayList<>();
        _img_to_movement_detect_que = new  ArrayBlockingQueue(100);
        try {
            jTools.createDir(ROOT_PATH);
        }catch (IOException e){
            Log.e(TAG,e.getMessage()+"创建文件失败");
        }
        Size size = new Size(1280, 720);
        mVideoWriter = new VideoWriter(ROOT_PATH + "det.avi", VideoWriter.fourcc('M', 'J', 'P', 'G'), 30, size);
        mVideoWriter.open(ROOT_PATH+ "det.avi", VideoWriter.fourcc('M', 'J', 'P', 'G'), 30, size);
        mVideoWriterAll = new VideoWriter(ROOT_PATH + "all.avi", VideoWriter.fourcc('M', 'J', 'P', 'G'), 30, size);
        mVideoWriterAll.open(ROOT_PATH + "all.avi", VideoWriter.fourcc('M', 'J', 'P', 'G'), 30, size);
    }
    public Thread readImage(){
        Thread readImageThread = new Thread() {
            @Override
            public void run() {
                super.run();
                int idx = 0;
                for (int i = 1; i < 245; i++) {
                    long start_time = jTools.getTimeStamp();
                    final Mat a = Imgcodecs.imread(Environment.getExternalStorageDirectory().getAbsolutePath()
                            + File.separator + "images/" + String.valueOf(i) + ".bmp");
                    QueueObject object = new QueueObject(a, idx, 0);
                    _img_to_movement_detect_que.offer(object);
                    long end_time = jTools.getTimeStamp();
//                    Log.i(TAG, "获取图片成功,index:" + String.valueOf(idx) + ",耗时:" + String.valueOf(end_time - start_time) + "ms");
                    idx = idx + 1;
                }
                isOpened = false;
            }
        };
        return readImageThread;
    }
    public Thread readVideo(final String video_path){
        Thread readVideoThread = new Thread() {
            @Override
            public void run() {
                super.run();
                int idx = 0;
                Log.e(TAG, "正在读取" + jTools.ROOT_PATH + "videos/" + video_path);
                VideoCapture capture = new VideoCapture(jTools.ROOT_PATH + "videos/" + video_path);
                Mat img = new Mat();
                while (capture.read(img)){
//                    Log.e(TAG,"正在获取第"+String.valueOf(idx)+"帧");
                    QueueObject object = new QueueObject(img, idx, 0);
                    _img_to_movement_detect_que.offer(object);
                    idx = idx + 1;
                }
                isOpened = false;
                Log.e(TAG,"读取视频完成");

            }
        };
        return readVideoThread;

    }
    public Thread readFFPegVideo(final String video_path){
        Thread readVideoThread = new Thread() {
            @Override
            public void run() {
                super.run();
                int idx = 0;
                Log.e(TAG,"正在读取"+jTools.ROOT_PATH+"videos/"+video_path);
                FFmpegMediaMetadataRetriever mmr = new FFmpegMediaMetadataRetriever();
                File file = new File(jTools.ROOT_PATH+"videos/"+video_path);

                mmr.setDataSource(file.getAbsolutePath());
                String duration = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);
                String rate = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE);

                int num = (int)(Float.parseFloat(duration)*Float.parseFloat(rate)/1000.0);
                long timespeed = (long) ( 1000 / (Float.parseFloat(rate)));
                try {
                    jTools.createDir(jTools.ROOT_PATH+"images/");
                }catch (IOException e){

                }
                for (int i = 0; i <num ; i++) {
                    Bitmap bitmap = mmr.getFrameAtTime(i*timespeed*1000,FFmpegMediaMetadataRetriever.OPTION_CLOSEST);
                    Mat dst = new Mat();
                    Utils.bitmapToMat(bitmap,dst);
                    Mat img = new Mat();
                    Imgproc.cvtColor(dst,img,Imgproc.COLOR_RGBA2BGR);
//                    Imgcodecs.imwrite(jTools.ROOT_PATH+"images/"+String.valueOf(i)+".jpg",img);
                    Log.e(TAG,"正在获取第"+String.valueOf(i)+"帧");
                    QueueObject object = new QueueObject(img, i, 0);
                    _img_to_movement_detect_que.offer(object);
                }
                Log.e(TAG,"视频读取完成");
                isOpened = false;
            }
        };
        return readVideoThread;
    }


    //写入视频的线程
    public Thread detFrame() {
        final Thread thread = new Thread() {
            @Override
            public void run() {
                while (true) {
                    QueueObject object = _img_to_movement_detect_que.poll();
                    if (object != null) {
                        Mat mat = object.img;
                        int index = object.index;
                        frame_cache.add(mat);
                        frame_id_cache.add(index);
                        if (frame_cache.size() > 11) {
                            frame_cache.remove(0);
                            frame_id_cache.remove(0);
                        }
                        List<Integer> frame_id_list = det.detect(mat, index);
                        for (int j = 0; j < frame_id_list.size(); j++) {
                            int frame_id = frame_id_list.get(j);
                            int id = frame_id_cache.indexOf(frame_id);
                            Mat b = frame_cache.get(id).clone();
                            writeVideo(b,frame_id);
                        }
//                        Log.e(TAG, "第" + String.valueOf(index) + "帧满足条件,写入视频,耗时:" + String.valueOf(end_time - start_time) + "ms");
                    }
                    if (object == null && !isOpened) {
                        break;
                    }
                }

            }
        };
        return thread;
    }
    private void writeVideo(Mat mat,int frame_id) {
        long start_time = jTools.getTimeStamp();
        mVideoWriter.write(mat);
        long end_time = jTools.getTimeStamp();
        Log.e(TAG, "从队列中取出第----"+String.valueOf(frame_id)+"------帧满足并且写入视频中,写入耗时" + String.valueOf(end_time - start_time) + "ms");
    }

    public testMotion(Activity MainActivity){
        activity = MainActivity;
        init();
    }
}
