package com.uiptv.model;

import java.util.Map;
import java.util.Objects;

import static com.uiptv.util.StringUtils.safeGetString;

class AudioInfo extends BaseJson {
    private String codecName, codecLongName, codecType, codecTagString, sampleRate, channels, bitRate, bitsPerSample, channelLayout;


    public AudioInfo(Map map) {
        if (map == null) return;
        this.codecName = safeGetString(map, "codec_name");
        this.codecLongName = safeGetString(map, "codec_long_name");
        this.codecType = safeGetString(map, "codec_type");
        this.codecTagString = safeGetString(map, "codec_tag_string");
        this.sampleRate = safeGetString(map, "sample_rate");
        this.channels = safeGetString(map, "channels");
        this.channelLayout = safeGetString(map, "channel_layout");
        this.bitsPerSample = safeGetString(map, "bits_per_sample");
        this.bitRate = safeGetString(map, "bit_rate");
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

    public String getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(String sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getChannels() {
        return channels;
    }

    public void setChannels(String channels) {
        this.channels = channels;
    }

    public String getBitRate() {
        return bitRate;
    }

    public void setBitRate(String bitRate) {
        this.bitRate = bitRate;
    }

    public String getBitsPerSample() {
        return bitsPerSample;
    }

    public void setBitsPerSample(String bitsPerSample) {
        this.bitsPerSample = bitsPerSample;
    }

    public String getChannelLayout() {
        return channelLayout;
    }

    public void setChannelLayout(String channelLayout) {
        this.channelLayout = channelLayout;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioInfo audioInfo = (AudioInfo) o;
        return Objects.equals(codecName, audioInfo.codecName) && Objects.equals(codecLongName, audioInfo.codecLongName) && Objects.equals(codecType, audioInfo.codecType) && Objects.equals(codecTagString, audioInfo.codecTagString) && Objects.equals(sampleRate, audioInfo.sampleRate) && Objects.equals(channels, audioInfo.channels) && Objects.equals(bitRate, audioInfo.bitRate) && Objects.equals(bitsPerSample, audioInfo.bitsPerSample) && Objects.equals(channelLayout, audioInfo.channelLayout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codecName, codecLongName, codecType, codecTagString, sampleRate, channels, bitRate, bitsPerSample, channelLayout);
    }

    @Override
    public String toString() {
        return "AudioInfo{" +
                "codecName='" + codecName + '\'' +
                ", codecLongName='" + codecLongName + '\'' +
                ", codecType='" + codecType + '\'' +
                ", codecTagString='" + codecTagString + '\'' +
                ", sampleRate='" + sampleRate + '\'' +
                ", channels='" + channels + '\'' +
                ", bitRate='" + bitRate + '\'' +
                ", bitsPerSample='" + bitsPerSample + '\'' +
                ", channelLayout='" + channelLayout + '\'' +
                '}';
    }
}
