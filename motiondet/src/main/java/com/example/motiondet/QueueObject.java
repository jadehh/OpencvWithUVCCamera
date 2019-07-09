package com.example.motiondet;

import org.opencv.core.Mat;

public class QueueObject {
    public Mat img;
    public int index;
    public int weight_index;

    public void setWeight_index(int weight_index) {
        this.weight_index = weight_index;
    }

    public void setImg(Mat img) {
        this.img = img;
    }

    public void setIndex(int index) {
        this.index = index;
    }


    public QueueObject(Mat  img, int index, int weight_index){
        setImg(img);
        setIndex(index);
        setWeight_index(weight_index);
    }
}
