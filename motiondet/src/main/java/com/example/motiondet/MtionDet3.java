package com.example.motiondet;


import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;




public class MtionDet3 {
    private boolean DEBUG = true;
    private final static String TAG = "MtionDet3";
    private int m_line_y;
    private int m_xmin;
    private int m_ymin;
    private int m_xmax;
    private int m_ymax;
    private int m_delta_threshold;
    private ArrayList<Integer>  frame_id_cache;
    private ArrayList<Integer> m_num_cache;
    private int background[];
    private int last_in_id;
    private int last_append_id = -1;

    public void init(int line_y, int xmin, int ymin, int xmax, int ymax){
        frame_id_cache = new ArrayList<>();
        m_num_cache = new ArrayList<>();
        m_line_y = line_y;
        m_xmin = xmin;
        m_ymin = ymin;
        m_xmax = xmax;
        m_ymax = ymax;
        m_delta_threshold = 5;
        background = null;
        for(int i=0;i<11;i++){
            m_num_cache.add(0,0);
        }
        last_in_id = 0;
    }

    public List<Integer> detect(Mat frame, int idx){
        List<Integer> result_id = new ArrayList<>();
        frame_id_cache.add(idx);
        if (frame_id_cache.size() > 11){
            frame_id_cache.remove(0);
        }

        Mat frame_block = new Mat();
        Size size = new Size( m_xmax-m_xmin,m_ymax-m_ymin);
        Point point = new Point(  m_xmin+(size.width/2),m_ymin+(size.height/2));
        Imgproc.getRectSubPix(frame,size,point,frame_block,-1);
        Mat frame_block_gray = new Mat();
        Imgproc.cvtColor(frame_block,frame_block_gray,Imgproc.COLOR_BGR2GRAY);
        Mat frame_block_gray_Gaussian = new Mat();
        Size kSize = new Size(5,5);
        Imgproc.GaussianBlur(frame_block_gray,frame_block_gray_Gaussian,kSize,0);
        int num_pixel = process(frame_block_gray);
//        Log.e(TAG,"正在处理第---"+String.valueOf(idx)+"---帧,num_pixel="+String.valueOf(num_pixel));
        m_num_cache.add(num_pixel);
        m_num_cache.remove(0);
        int num_frame = frame_id_cache.size();
        if (num_frame < 10){
            return result_id;
        }

        if(m_num_cache.get(2) == 0 && m_num_cache.get(3) == 0 && m_num_cache.get(4)==0){
            if (m_num_cache.get(5)> 0 && m_num_cache.get(6)>0 && m_num_cache.get(7)>0&&(m_num_cache.get(5)+m_num_cache.get(6)+m_num_cache.get(7))>15){
                last_in_id = frame_id_cache.get(8);
                int stat_index = 2;
                int stop_index = 9;
                if (num_frame < 9){
                    stat_index = 0;
                    stop_index = num_frame;
                }
                for (int i=stat_index;i<stop_index;i++){
                    int frame_id = frame_id_cache.get(i);
                    if (!result_id.contains(frame_id)&&frame_id > last_append_id){
                        last_append_id = frame_id;
                        result_id.add(frame_id);
                    }
                }
            }
        }
        if(m_num_cache.get(2) > 0 && m_num_cache.get(3) > 0 && m_num_cache.get(4) >0 && (m_num_cache.get(2) + m_num_cache.get(3) + m_num_cache.get(4)) > 15) {
            if (m_num_cache.get(5) == 0 && m_num_cache.get(6) == 0 && m_num_cache.get(7) == 0) {
                for (int i=2;i<9;i++){
                    int frame_id = frame_id_cache.get(i);
                    if (!result_id.contains(frame_id)&&frame_id > last_append_id){
                        last_append_id = frame_id;
                        result_id.add(frame_id);
                    }
                }
            }
        }
        int arg_max = 0;
        int arg_max_index = -1;
        for (int i=2;i<9;i++){
            if (arg_max < m_num_cache.get(i)){
                arg_max = m_num_cache.get(i);
                arg_max_index = i;
            }
        }

        if (arg_max_index == 5 && arg_max >= 25 ){
            int start_index = 1;
            int stop_index = 10;
            if ((frame_id_cache.get(5) - last_in_id) <=5){
                start_index = 1;
                stop_index = 6;
            }
            if (num_frame<10){
                start_index = 0;
                stop_index = num_frame;
            }
            for (int i = start_index; i < stop_index; i ++){
                int frame_id = frame_id_cache.get(i);
                if (!result_id.contains(frame_id)&&frame_id > last_append_id){
                    last_append_id = frame_id;
                    result_id.add(frame_id);
                }
            }
        }

        return result_id;
    }


    public int process(Mat img){
        Mat img_row = img.row(m_line_y);
        int size = (int) (img_row.total() * img_row.channels());
        int[] temp = new int[size];
        img_row.convertTo(img_row,CvType.CV_32S);
        img_row.get(0, 0, temp);
        int[] pixel_delta = new int[size];
        int delta[] = new int[size];
        for(int i=0;i<size-1;i++){
            pixel_delta[i] = temp[i+1] - temp[i];
        }
        pixel_delta[size-1] = 0;

        if (background == null){
            background = pixel_delta.clone();
            delta = new int[size];
        }else {
            for (int i=0;i<size;i++){
                delta[i] = pixel_delta[i] - background[i];
            }
            background = pixel_delta.clone();
        }
        int d_mask[] = new int[size];
        int foregrount[] = new int[size];
        int num_pixel = 0;

        for(int i=0;i<size;i++){
            if (Math.abs(delta[i]) > m_delta_threshold){
                d_mask[i] = 1;
                num_pixel = num_pixel + 1;
            }
            foregrount[i] = (d_mask[i]-0)*255;
        }
        return num_pixel;
    }

    public void clear(){
        last_append_id = -1;
        frame_id_cache.clear();
        m_num_cache.clear();
    }

}
