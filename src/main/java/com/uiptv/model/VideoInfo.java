package com.uiptv.model;

import java.util.Map;

import static com.uiptv.util.StringUtils.safeGetString;

class VideoInfo extends BaseJson {
    String index, codecName, codecLongName, profile, codecType, codecTagString, codecTag, width, height,
            codedWidth, codedHeight, closedCaptions, filmGrain, hasBFrames,
            sampleAspectRatio, displayAspectRatio, pixFmt, avgFrameRate, rFrameRate,
            startTime, durationTs, duration, bitRate, bitsPerRawSample, nbFrames, extradataSize;


    public VideoInfo(Map map) {
        if (map == null) return;
        this.index = safeGetString(map, "index");
        this.codecName = safeGetString(map, "codec_name");
        this.codecLongName = safeGetString(map, "codec_long_name");
        this.profile = safeGetString(map, "profile");
        this.codecType = safeGetString(map, "codec_type");
        this.codecTagString = safeGetString(map, "codec_tag_string");
        this.codecTag = safeGetString(map, "codec_tag");
        this.width = safeGetString(map, "width");
        this.height = safeGetString(map, "height");
        this.codedWidth = safeGetString(map, "coded_width");
        this.codedHeight = safeGetString(map, "coded_height");
        this.closedCaptions = safeGetString(map, "closed_captions");
        this.filmGrain = safeGetString(map, "film_grain");
        this.hasBFrames = safeGetString(map, "has_b_frames");
        this.sampleAspectRatio = safeGetString(map, "sample_aspect_ratio");
        this.displayAspectRatio = safeGetString(map, "display_aspect_ratio");
        this.pixFmt = safeGetString(map, "pix_fmt");
        this.avgFrameRate = safeGetString(map, "avg_frame_rate");
        this.rFrameRate = safeGetString(map, "r_frame_rate");
        this.startTime = safeGetString(map, "start_time");
        this.durationTs = safeGetString(map, "duration_ts");
        this.duration = safeGetString(map, "duration");
        this.bitRate = safeGetString(map, "bit_rate");
        this.bitsPerRawSample = safeGetString(map, "bits_per_raw_sample");
        this.nbFrames = safeGetString(map, "nb_frames");
        this.extradataSize = safeGetString(map, "extradata_size");
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getCodecName() {
        return codecName;
    }

    public void setCodecName(String codecName) {
        this.codecName = codecName;
    }

    public String getCodecLongName() {
        return codecLongName;
    }

    public void setCodecLongName(String codecLongName) {
        this.codecLongName = codecLongName;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getCodecType() {
        return codecType;
    }

    public void setCodecType(String codecType) {
        this.codecType = codecType;
    }

    public String getCodecTagString() {
        return codecTagString;
    }

    public void setCodecTagString(String codecTagString) {
        this.codecTagString = codecTagString;
    }

    public String getCodecTag() {
        return codecTag;
    }

    public void setCodecTag(String codecTag) {
        this.codecTag = codecTag;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getCodedWidth() {
        return codedWidth;
    }

    public void setCodedWidth(String codedWidth) {
        this.codedWidth = codedWidth;
    }

    public String getCodedHeight() {
        return codedHeight;
    }

    public void setCodedHeight(String codedHeight) {
        this.codedHeight = codedHeight;
    }

    public String getClosedCaptions() {
        return closedCaptions;
    }

    public void setClosedCaptions(String closedCaptions) {
        this.closedCaptions = closedCaptions;
    }

    public String getFilmGrain() {
        return filmGrain;
    }

    public void setFilmGrain(String filmGrain) {
        this.filmGrain = filmGrain;
    }

    public String getHasBFrames() {
        return hasBFrames;
    }

    public void setHasBFrames(String hasBFrames) {
        this.hasBFrames = hasBFrames;
    }

    public String getSampleAspectRatio() {
        return sampleAspectRatio;
    }

    public void setSampleAspectRatio(String sampleAspectRatio) {
        this.sampleAspectRatio = sampleAspectRatio;
    }

    public String getDisplayAspectRatio() {
        return displayAspectRatio;
    }

    public void setDisplayAspectRatio(String displayAspectRatio) {
        this.displayAspectRatio = displayAspectRatio;
    }

    public String getPixFmt() {
        return pixFmt;
    }

    public void setPixFmt(String pixFmt) {
        this.pixFmt = pixFmt;
    }

    public String getAvgFrameRate() {
        return avgFrameRate;
    }

    public void setAvgFrameRate(String avgFrameRate) {
        this.avgFrameRate = avgFrameRate;
    }

    public String getrFrameRate() {
        return rFrameRate;
    }

    public void setrFrameRate(String rFrameRate) {
        this.rFrameRate = rFrameRate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getDurationTs() {
        return durationTs;
    }

    public void setDurationTs(String durationTs) {
        this.durationTs = durationTs;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getBitRate() {
        return bitRate;
    }

    public void setBitRate(String bitRate) {
        this.bitRate = bitRate;
    }

    public String getBitsPerRawSample() {
        return bitsPerRawSample;
    }

    public void setBitsPerRawSample(String bitsPerRawSample) {
        this.bitsPerRawSample = bitsPerRawSample;
    }

    public String getNbFrames() {
        return nbFrames;
    }

    public void setNbFrames(String nbFrames) {
        this.nbFrames = nbFrames;
    }

    public String getExtradataSize() {
        return extradataSize;
    }

    public void setExtradataSize(String extradataSize) {
        this.extradataSize = extradataSize;
    }
}
