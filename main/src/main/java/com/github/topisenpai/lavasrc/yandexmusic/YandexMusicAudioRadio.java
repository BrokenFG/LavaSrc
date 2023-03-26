package com.github.topisenpai.lavasrc.yandexmusic;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.track.TrackMarker;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

public class YandexMusicAudioRadio implements AudioRadio {

    private final String name;
    private final String station;
    private final String batchId;
    private List<AudioTrack> stationTracks;
    private final YandexMusicSourceManager sourceManager;
    private AudioTrack currentTrack;
    private int index = 0;

    public YandexMusicAudioRadio(String name, String station, String batchId, List<AudioTrack> stationTracks, YandexMusicSourceManager sourceManager) {
        this.name = name;
        this.station = station;
        this.batchId = batchId;
        this.stationTracks = stationTracks;
        this.sourceManager = sourceManager;
    }


    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getStation() {
        return this.station;
    }

    @Override
    public String getBatchId() {
        return this.batchId;
    }

    @Override
    public AudioTrack nextTrack() throws URISyntaxException, IOException {
        if (Objects.isNull(currentTrack)) {
            this.sendStartRadio();
        }
        else {
            this.sendPlayEndRadio();
            this.index += 1;
            if (this.index >= stationTracks.size()) {
                this.updateRadioBatch();
            }
        }
        currentTrack = this.updateCurrentTrack();
        return currentTrack;
    }

    @Override
    public AudioTrack skipTrack() throws URISyntaxException, IOException {
        if (Objects.isNull(currentTrack)) {
            this.sendStartRadio();
            this.updateCurrentTrack();
        } else {
            this.sendPlaySkipRadio();
            this.index += 1;
            if (this.index >= stationTracks.size()) {
                this.updateRadioBatch();
            }
            currentTrack = updateCurrentTrack();
        }
        return currentTrack;
    }

    @Override
    public AudioTrack getCurrent() {
        return currentTrack;
    }

    @Override
    public List<AudioTrack> getStationTracks() {
        return stationTracks;
    }

    private void updateRadioBatch() throws IOException, URISyntaxException {
        this.index = 0;
        AudioRadioBatch radioBatch = this.sourceManager.getRadioBatch(this.station, currentTrack != null ? currentTrack.getIdentifier() : null);
        this.stationTracks = radioBatch.getAudioTracks();
        this.sendStartRadio();
    }

    private AudioTrack updateCurrentTrack() throws URISyntaxException, IOException {
        currentTrack = stationTracks.get(index);
        this.sendPlayStartRadio();
        return currentTrack;
    }

    private void sendStartRadio() throws IOException, URISyntaxException {
        sourceManager.radioFeedBackRadioStarted(station, batchId);
    }

    private void sendPlayEndRadio() throws URISyntaxException, IOException {
        sourceManager.radioFeedBackTrackFinished(station, currentTrack, currentTrack.getPosition() / 1000, batchId);
    }

    private void sendPlayStartRadio() throws URISyntaxException, IOException {
        sourceManager.radioFeedBackTrackStarted(station, currentTrack, batchId);
    }

    private void sendPlaySkipRadio() throws URISyntaxException, IOException {
        sourceManager.radioFeedBackTrackSkipped(station, currentTrack, currentTrack.getPosition() / 1000, batchId);
    }

    @Override
    public AudioTrackInfo getInfo() {
        return currentTrack.getInfo();
    }

    @Override
    public String getIdentifier() {
        return currentTrack.getIdentifier();
    }

    @Override
    public AudioTrackState getState() {
        return currentTrack.getState();
    }

    @Override
    public void stop() {
        currentTrack.stop();
    }

    @Override
    public boolean isSeekable() {
        return false;
    }

    @Override
    public long getPosition() {
        return currentTrack.getPosition();
    }

    @Override
    public void setPosition(long position) {
        currentTrack.setPosition(position);
    }

    @Override
    public void setMarker(TrackMarker marker) {
        currentTrack.setMarker(marker);
    }

    @Override
    public long getDuration() {
        return currentTrack.getDuration();
    }

    @Override
    public AudioTrack makeClone() {
        return currentTrack.makeClone();
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return currentTrack.getSourceManager();
    }

    @Override
    public void setUserData(Object userData) {
        currentTrack.setUserData(userData);
    }

    @Override
    public Object getUserData() {
        return currentTrack.getUserData();
    }

    @Override
    public <T> T getUserData(Class<T> klass) {
        return currentTrack.getUserData(klass);
    }
}
