package com.serenegiant.usb.encoder;

import com.google.gson.JsonObject;

import org.json.JSONObject;

/** 录制参数
 *
 * Created by jiangdongguo on 2017/10/19.
 */

public class RecordParams {
    private JSONObject jsonObject;
    private String recordPath;
    private int recordDuration;
    private boolean voiceClose;
    private boolean isAutoSave;
    private String savePath;
    private String videoName;
    public boolean isVoiceClose() {
        return voiceClose;
    }

    public void setVoiceClose(boolean voiceClose) {
        this.voiceClose = voiceClose;
    }

    public String getRecordPath() {
        return recordPath;
    }
    public String getSavePath(){
        return savePath;
    }
    public JSONObject getJsonObject(){
        return jsonObject;
    }

    public String getVideoName() {
        return videoName;
    }

    public void setRecordPath(String recordPath) {
        this.recordPath = recordPath;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

    public void setJsonObject(JSONObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public int getRecordDuration() {
        return recordDuration;
    }

    public void setRecordDuration(int recordDuration) {
        this.recordDuration = recordDuration;
    }

    public boolean isAutoSave() {
        return isAutoSave;
    }

    public void setAutoSave(boolean autoSave) {
        isAutoSave = autoSave;
    }
}
