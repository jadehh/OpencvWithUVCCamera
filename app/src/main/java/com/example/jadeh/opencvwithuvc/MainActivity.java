package com.example.jadeh.opencvwithuvc;

import android.app.Activity;
import android.os.Environment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;


import java.io.File;
import java.io.IOException;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.jadehh.JavaBridgeAbstractUVC;


import com.serenegiant.usb.widget.UVCCameraTextureView;

import com.example.jade.jade_tools;
import com.example.motiondet.testMotion;

public class MainActivity extends AppCompatActivity {


    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    reflashUI();
            }
        }
    };
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            reflashUI();
            mHandler.postDelayed(runnable, 1000);
        }
    };
    private TextView mFpsTv;

    private static UVCCameraTextureView mTextView;
    private static JavaBridgeAbstractUVC uvcJava;
    private jade_tools jTools = new jade_tools();
    private JavaBridgeAbstractUVC.CameraInterface cameraListener = new JavaBridgeAbstractUVC.CameraInterface() {
        @Override
        public void onConnectCamera(boolean result) {
            if (result) {
                System.out.println("打开摄像头成功");
            } else {
                System.out.println("打开摄像头失败");
            }

        }

        @Override
        public void onDisconnectCamera(String path) {
            System.out.println(path);

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);




        mTextView = findViewById(R.id.camera_view);
        Button initBtn = findViewById(R.id.init);
        Button openDoorBtn = findViewById(R.id.open_door);
        Button recordVideoBtn = findViewById(R.id.record_video);
        Button releaseBtn = findViewById(R.id.release_btn);
        Button testAlgorithm = findViewById(R.id.test_algorithm_btn);
        Button closeBtn = findViewById(R.id.close_door);
        Button changeWightBtn = findViewById(R.id.change_weight);
        Button changeWeightValueBtn = findViewById(R.id.change_weight_value);

        mFpsTv = findViewById(R.id.fps_textview);




        //初始化按钮,这些值应该请求后台接口
        initBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uvcJava = new JavaBridgeAbstractUVC(MainActivity.this, cameraListener);
                uvcJava.init(mTextView, 50, 0, 410, 760, 720);
            }
        });


        //测试摄像头能够正常打开
        openDoorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //打开摄像头就不需要初始化了
                double[] weight_init = {1000.0, 2000.0, 3000.0, 4000.0, 5000.0};
                JSONObject jsonObject = new JSONObject();
                JSONObject userInfo = new JSONObject();
                try {
                    String dircPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                            + File.separator + "anzhi/download/demo.jpg";
                    userInfo.put("deviceType", 5);
                    userInfo.put("deviceKey", "ZJLd981556745");
                    userInfo.put("userId", 9);
                    List<String> goodsKey = new ArrayList<>();
                    goodsKey.add("yb-ybcjs-pz-yw-555ml");
                    goodsKey.add("yy-yylght-gz-ht-240ml");
                    jsonObject.put("GOODS_KEY",goodsKey);
                    List<Double> goodsWeight = new ArrayList<>();
                    goodsWeight.add(581.0);
                    goodsWeight.add(280.1);
                    jsonObject.put("GOODS_WEIGHT", goodsWeight);
                    jsonObject.put("USER_CONFIG", userInfo);
                    jsonObject.put("DRIC_AREAS_V0",dircPath);
                    uvcJava.open_camera(0,100, weight_init, jsonObject);
                } catch (JSONException e) {

                }
            }
        });


        //录制视频
        recordVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uvcJava.recordVideo();
            }
        });

        //关闭摄像头
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                double[] weight_end = {100.0, 200.0, 300.0, 400.0, 500.0};
                uvcJava.stop(weight_end);
            }
        });

        //释放所有资源，下一次的参数需要初始化
        releaseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                uvcJava.release();
            }
        });
        //改变重量
        changeWightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uvcJava.change_weight();
            }
        });
        changeWeightValueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double[] weight = {1.0, 2.0, 3.0, 4.0, 5.0};
                uvcJava.change_weight_value(weight);
            }
        });
        testAlgorithm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //先将文件拷贝到文件目录
                //开启文件访问权限
                try {
                    jTools.copyAssertFile(MainActivity.this, "video_2019-06-19_10-42-41.avi", "");
                } catch (IOException e) {
                    System.out.println(e);
                }
                test();
            }
        });
    }
    public void test() {
        testMotion test = new testMotion(MainActivity.this);
        Thread readImageThread = test.readVideo("video_2019-06-19_10-42-41.avi");
        readImageThread.start();
        Thread detThread = test.detFrame();
        detThread.start();
    }
    @Override
    protected void onStart() {
        super.onStart();
        mHandler.post(runnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        uvcJava.release();
    }

    private void reflashUI() {
        float srcFps, totalFps;
        if (mTextView != null) {
            mTextView.updateFps();
            srcFps = mTextView.getFps();
            totalFps = mTextView.getTotalFps();
        } else {
            srcFps = 0.0f;
            totalFps = 0.0f;
        }
        mFpsTv.setText(String.format(Locale.US, "FPS:%4.1f->%4.1f", srcFps, totalFps));
    }
}