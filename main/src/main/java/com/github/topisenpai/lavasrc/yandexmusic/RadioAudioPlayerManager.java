package com.github.topisenpai.lavasrc.yandexmusic;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import java.util.concurrent.Future;

public class RadioAudioPlayerManager extends DefaultAudioPlayerManager {

    @Override
    public Future<Void> loadItem(AudioReference reference, AudioLoadResultHandler resultHandler) {
        return super.loadItem(reference, resultHandler);
    }

    @Override
    public Future<Void> loadItemOrdered(Object orderingKey, AudioReference reference, AudioLoadResultHandler resultHandler) {
        return super.loadItemOrdered(orderingKey, reference, resultHandler);
    }
}
