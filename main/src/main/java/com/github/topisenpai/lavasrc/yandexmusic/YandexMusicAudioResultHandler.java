package com.github.topisenpai.lavasrc.yandexmusic;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

public interface YandexMusicAudioResultHandler extends AudioLoadResultHandler {
    void radioLoaded(AudioRadio radio);
}
