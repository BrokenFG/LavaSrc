package com.github.topisenpai.lavasrc.yandexmusic;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public interface AudioRadio extends AudioItem, AudioTrack {

    String getName();

    String getStation();
    String getBatchId();

    AudioTrack nextTrack() throws URISyntaxException, IOException;
    AudioTrack skipTrack() throws URISyntaxException, IOException;

    AudioTrack getCurrent();

    List<AudioTrack> getStationTracks();
}
