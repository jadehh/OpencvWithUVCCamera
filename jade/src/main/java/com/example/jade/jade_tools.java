package com.example.jade;

import android.app.Activity;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class jade_tools {
    private String TAG = "JadeTools";
    public  String ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
            + File.separator ;
    //获取当前时间戳
    public long getTimeStamp(){
        return System.currentTimeMillis();
    }

    //获取当前时间
    public  String getTime(){
        System.currentTimeMillis();
        SimpleDateFormat formatter   =   new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        Date curDate =  new Date(System.currentTimeMillis());
        String str   =   formatter.format(curDate);
        return str;
    }

    //拷贝Assert文件
    public void copyAssertFile(Activity c, String Name, String desPath) throws IOException {
        String NEW_ROOT_PATH = ROOT_PATH + "videos/";
        try {
            createDir(NEW_ROOT_PATH);
        }catch (IOException e){
            System.out.println(e);
        }

        File outfile = null;
        if( desPath != null )
            outfile = new File(  NEW_ROOT_PATH+Name);
        else
            outfile = new File(  NEW_ROOT_PATH+Name);
        if (!outfile.exists()) {
            outfile.createNewFile();
            FileOutputStream out = new FileOutputStream(outfile);
            byte[] buffer = new byte[1024];
            InputStream in;
            int readLen = 0;
            if( desPath != null )
                in = c.getAssets().open(desPath+Name);
            else
                in = c.getAssets().open(Name);
            while((readLen = in.read(buffer)) != -1){
                out.write(buffer, 0, readLen);
            }
            out.flush();
            in.close();
            out.close();
        }
    }

    public void copyFile(String oldPath, String newPath) {
        try {
            int bytesum = 0;
            int byteread = 0;
            File oldfile = new File(oldPath);
            if (oldfile.exists()) { //文件存在时
                InputStream inStream = new FileInputStream(oldPath); //读入原文件
                FileOutputStream fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[1444];
                while ( (byteread = inStream.read(buffer)) != -1) {
                    bytesum += byteread; //字节数 文件大小
                    fs.write(buffer, 0, byteread);
                }
                inStream.close();
                Log.e(TAG,"复制文件成功");
            }
        }
        catch (Exception e) {
            Log.e(TAG,"复制单个文件操作出错");
            e.printStackTrace();
        }

    }


    public void createDir(String ROOT_PATH) throws IOException {
        File saveDir = new File(ROOT_PATH.substring(0,ROOT_PATH.length()-1));
        if (!saveDir.exists()){
            saveDir.mkdirs();
        }
    }

    public void writeTxtToFile(String strcontent, String filePath, String fileName) {
        //生成文件夹之后，再生成文件，不然会出错
        makeFilePath(filePath, fileName);
        String strFilePath = filePath + fileName;
        // 每次写入时，都换行写
        String strContent = strcontent + "\r\n";
        try {
            File file = new File(strFilePath);
            if (!file.exists()) {
                Log.d(TAG, "Create the file:" + strFilePath);
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            RandomAccessFile raf = new RandomAccessFile(file, "rwd");
            raf.seek(file.length());
            raf.write(strContent.getBytes());
            raf.close();
        } catch (Exception e) {
            Log.e("TestFile", "Error on write File:" + e);
        }
    }
//生成文件
    private File makeFilePath(String filePath, String fileName) {
        File file = null;
        makeRootDirectory(filePath);
        try {
            file = new File(filePath + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

//生成文件夹

    private static void makeRootDirectory(String filePath) {
        File file = null;
        try {
            file = new File(filePath);
            if (!file.exists()) {
                file.mkdir();
            }
        } catch (Exception e) {
            Log.i("error:", e + "");
        }
    }

    //删除文件夹
    public static void deleteDirWihtFile(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory())
            return;
        for (File file : dir.listFiles()) {
            if (file.isFile())
                file.delete(); // 删除所有文件
            else if (file.isDirectory())
                deleteDirWihtFile(file); // 递规的方式删除文件夹
        }
        dir.delete();// 删除目录本身
    }

    public String listToString( ArrayList<double []> weights_list){
        String weights_string = "[";
        for (int i = 0; i < weights_list.size(); i++) {
            String weights_list_string = "[";
            double[] weights = weights_list.get(i);
            for (int j = 0;j<weights.length;j++){
                String ping;
                if (j==weights.length-1){ ping = "]";
                }else{ ping = ","; }
                weights_list_string = weights_list_string + String.valueOf(weights[j])+ping;
            }
            String ping;
            if (i==weights_list.size()-1){ ping = "]";
            }else{ ping = ","; }
            weights_string = weights_string+weights_list_string+ping;
        }
        return weights_string;
    }

}
