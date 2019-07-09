package com.serenegiant.usb.common;

import org.opencv.core.Mat;

public class QueueObject {
    public Mat img;
    public int index;
    public int weight_index;
    public byte[] data;

    public void setWeight_index(int weight_index) {
        this.weight_index = weight_index;
    }

    public void setImg(Mat img) {
        this.img = img;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public QueueObject(Mat img,byte[] data,int index, int weight_index){
        setData(data);
        setImg(img);
        setIndex(index);
        setWeight_index(weight_index);
    }
}
