package com.example.jadeh.opencvwithuvc;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.example.jade.ApacheZipTools;

import com.example.jade.ZipTools;
import com.example.jade.jade_tools;

import org.apache.tools.ant.BuildException;

import java.io.IOException;


public class testActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_main);


        Button testApacheZipBtn = findViewById(R.id.test_apache_zip_btn);
        Button testSystemZipBtn = findViewById(R.id.test_system_zip_btn);

        testApacheZipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(){
                    @Override
                    public void run() {
                        super.run();
                        try{
                            jade_tools jTools = new jade_tools();
                            ApacheZipTools zipTools = new ApacheZipTools();
                            System.out.println(jTools.ROOT_PATH+"video");
                            try{
                                jTools.createDir(jTools.ROOT_PATH + "zip_apache/");
                                long start_time = jTools.getTimeStamp();
                                zipTools.zip(jTools.ROOT_PATH+"video",jTools.ROOT_PATH + "zip_apache/");
                                long end_time = jTools.getTimeStamp();
                                System.out.println("Apache压缩完成,总共耗时:"+String.valueOf(end_time-start_time)+"ms");
                            }catch (IOException e){
                                System.out.println(e.getMessage());
                            }

                        }catch (BuildException e){
                            System.out.println(e.getMessage());
                        }

                    }
                }.start();
            }
        });


        testSystemZipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(){
                    @Override
                    public void run() {
                        super.run();
                        try{
                            jade_tools jTools = new jade_tools();
                            ZipTools zipTools  = new ZipTools();
                            try {
                                jTools.createDir(jTools.ROOT_PATH + "zip_system/");
                                System.out.println(jTools.ROOT_PATH+"video");
                                long start_time = jTools.getTimeStamp();
                                zipTools.zip(jTools.ROOT_PATH+"video",jTools.ROOT_PATH + "zip_system/");
                                long end_time = jTools.getTimeStamp();
                                System.out.println("系统方法压缩完成,总共耗时:"+String.valueOf(end_time-start_time)+"ms");
                            }catch (IOException e){
                                System.out.println(e.getMessage());
                            }
                        }catch (Exception e){
                            System.out.println(e.getMessage());
                        }
                    }
                }.start();
            }
        });
    }


}