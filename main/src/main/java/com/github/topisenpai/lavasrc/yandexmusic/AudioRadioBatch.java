package com.github.topisenpai.lavasrc.yandexmusic;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class AudioRadioBatch {

    private final String batchId;
    private final List<AudioTrack> audioTracks;

    AudioRadioBatch(String batchId, List<AudioTrack> audioTracks) {
        this.batchId = batchId;
        this.audioTracks = audioTracks;
    }

    public String getBatchId() {
        return batchId;
    }

    public List<AudioTrack> getAudioTracks() {
        return audioTracks;
    }
}
