package com.example.motiondet;


import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;




import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;




/////////////////////////////////
import java.util.Deque;
/////////////////////////////////


public class MtionDet {

    private int m_line_y;
    private int m_xmin;
    private int m_ymin;
    private int m_xmax;
    private int m_ymax;

    private int m_delta_threshold;

    ///////////////////////////////////////////////

    private ArrayList<Integer> frame_id_cache;
    private ArrayList<Integer> m_num_cache;
    private List<Integer> wrote_frame_id_set;

    ///////////////////////////////////////////////

    private int[] background;
    private int last_in_id;

    public void init(int line_y, int xmin, int ymin, int xmax, int ymax){
        m_line_y = line_y;
        m_xmin = xmin;
        m_ymin = ymin;
        m_xmax = xmax;
        m_ymax = ymax;
        m_delta_threshold = 5;
        background = null;
        frame_id_cache = new ArrayList<Integer>();
        m_num_cache = new ArrayList<Integer>();
        //////////////////////////////////////////////////////

        //////////////////////////////////////////////////////
        wrote_frame_id_set = new ArrayList<>();
        last_in_id = 0;
    }



    public List<Integer> detect(Mat frame, int idx)
    {
        Mat frame_block = new Mat();
        Size size = new Size( m_xmax-m_xmin,m_ymax-m_ymin);
        Point point = new Point(  m_xmin+(size.width/2),m_ymin+(size.height/2));
        Imgproc.getRectSubPix(frame,size,point,frame_block,-1);
        Mat frame_block_gray = new Mat();
        Imgproc.cvtColor(frame_block,frame_block_gray,Imgproc.COLOR_BGR2GRAY);
        Mat frame_block_gray_Gaussian = new Mat();
        Size kSize = new Size(5,5);
        Imgproc.GaussianBlur(frame_block_gray,frame_block_gray_Gaussian,kSize,0);
        int num_pixel = process(frame_block_gray_Gaussian);

        frame_id_cache.add(idx);
        m_num_cache.add(num_pixel);

        if (frame_id_cache.size()>11)
        {
            frame_id_cache.remove(0);
            m_num_cache.remove(0);
        }
        else if (frame_id_cache.size()!=11)
        {
            return wrote_frame_id_set;
        }

        int start_index = 0;
        int stop_index = 0;

        if(m_num_cache.get(2) == 0 && m_num_cache.get(3) == 0 && m_num_cache.get(4)==0)
        {
            if (m_num_cache.get(5)> 0 && m_num_cache.get(6)>0 && m_num_cache.get(7)>0&&
                    (m_num_cache.get(5)+m_num_cache.get(6)+m_num_cache.get(7))>16)
            {
                last_in_id = frame_id_cache.get(8);
                start_index = 2;
                stop_index = 9;
            }
        }

        if(m_num_cache.get(2) > 0 && m_num_cache.get(3) > 0 && m_num_cache.get(4) >0 &&
                (m_num_cache.get(2) + m_num_cache.get(3) + m_num_cache.get(4)) > 16)
        {
            if (m_num_cache.get(5) == 0 && m_num_cache.get(6) == 0 && m_num_cache.get(7) == 0)
            {
                start_index = 2;
                stop_index = 9;
            }
        }

        int arg_max = 0;
        int arg_max_index = -1;
        for (int i=2;i<9;++i)
        {
            if (arg_max < m_num_cache.get(i))
            {
                arg_max = m_num_cache.get(i);
                arg_max_index = i;
            }
        }
        if (arg_max_index == 5 && arg_max >= 25 )
        {
            start_index = 1;
            stop_index = 10;
            if ((frame_id_cache.get(5) - last_in_id) <=5)
            {
                start_index = 1;
                stop_index = 6;
            }
        }

        for (int i =start_index;i<stop_index;++i)
        {
            int  fid = frame_id_cache.get(i);
           if (!wrote_frame_id_set.contains(fid)){
               wrote_frame_id_set.add(fid);
           }
        }

        return wrote_frame_id_set;
    }

    public int process(Mat img)
    {
        //img is CvType.CV_8UC1
        int size = img.cols();
        int[] pixel_delta = new int[size];
        for (int j=0; j<size-1;++j)
        {
            double[] one_get = img.get(m_line_y,j+1);
            double[] two_get = img.get(m_line_y,j);
            pixel_delta[j] = (int)one_get[0]-(int)two_get[0];;
        }
        pixel_delta[size-1] = 0;
        int[] delta = new int[size];
        if (background == null)
        {
            background = pixel_delta.clone();
        }
        else
        {
            for (int j=0;j<size;++j)
            {
                delta[j] = pixel_delta[j] - background[j];
            }
            background = pixel_delta.clone();
        }

        int num_pixel = 0;
        for(int j=0;j<size;++j)
        {
            if (-m_delta_threshold<=delta[j]&&delta[j]<=m_delta_threshold)
            {
                num_pixel = num_pixel + 1;
            }
        }
        return num_pixel;
    }
    public void clear(){
        frame_id_cache.clear();
        m_num_cache.clear();
    }


}
