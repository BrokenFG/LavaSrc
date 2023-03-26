package com.github.topisenpai.lavasrc.yandexmusic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class YandexMusicSourceManager implements AudioSourceManager, HttpConfigurable {
    public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?music\\.yandex\\.ru/(?<type1>artist|album)/(?<identifier>[0-9]+)/?((?<type2>track/)(?<identifier2>[0-9]+)/?)?");
    public static final Pattern URL_PLAYLIST_PATTERN = Pattern.compile("(https?://)?music\\.yandex\\.ru/users/(?<identifier>[0-9A-Za-z@.-]+)/playlists/(?<identifier2>[0-9]+)/?");
    public static final String SEARCH_PREFIX = "ymsearch:";
    public static final Pattern RADIO_PATTERN = Pattern.compile("ymradio:(?<identifier>[0-9A-Za-z@.-]+):(?<identifier2>[0-9A-Za-z@.-]+)");
    public static final String PUBLIC_API_BASE = "https://api.music.yandex.net";

    private static final Logger log = LoggerFactory.getLogger(YandexMusicSourceManager.class);

    private final HttpInterfaceManager httpInterfaceManager;

    private final String accessToken;

    public YandexMusicSourceManager(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalArgumentException("Yandex Music accessToken must be set");
        }
        this.accessToken = accessToken;
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "yandexmusic";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        try {
            if (reference.identifier.startsWith(SEARCH_PREFIX)) {
                return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()));
            }

            var matcher = URL_PATTERN.matcher(reference.identifier);
            if (matcher.find()) {
                switch (matcher.group("type1")) {
                    case "album":
                        if (matcher.group("type2") != null) {
                            var trackId = matcher.group("identifier2");
                            return this.getTrack(trackId);
                        }
                        var albumId = matcher.group("identifier");
                        return this.getAlbum(albumId);
                    case "artist":
                        var artistId = matcher.group("identifier");
                        return this.getArtist(artistId);
                }
                return null;
            }
            matcher = URL_PLAYLIST_PATTERN.matcher(reference.identifier);
            if (matcher.find()) {
                var userId = matcher.group("identifier");
                var playlistId = matcher.group("identifier2");
                return this.getPlaylist(userId, playlistId);
            }
            matcher = RADIO_PATTERN.matcher(reference.identifier);
            if (matcher.find()) {
                var type = matcher.group("identifier");
                var tag = matcher.group("identifier2");
                return this.getRadio(type, tag);
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private AudioItem getSearch(String query) throws IOException, URISyntaxException {
        var json = this.getJson(PUBLIC_API_BASE + "/search?text=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=track&page=0");
        if (json.isNull() || json.get("result").get("tracks").isNull()) {
            return AudioReference.NO_TRACK;
        }
        var tracks = this.parseTracks(json.get("result").get("tracks").get("results"));
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        return new BasicAudioPlaylist("Yandex Music Search: " + query, tracks, null, true);
    }

    private AudioItem getAlbum(String id) throws IOException, URISyntaxException {
        var json = this.getJson(PUBLIC_API_BASE + "/albums/" + id + "/with-tracks");
        if (json.isNull() || json.get("result").isNull()) {
            return AudioReference.NO_TRACK;
        }
        var tracks = new ArrayList<AudioTrack>();
        for (var volume : json.get("result").get("volumes").values()) {
            for (var track : volume.values()) {
                tracks.add(this.parseTrack(track));
            }
        }
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        return new BasicAudioPlaylist(json.get("result").get("title").text(), tracks, null, false);
    }

    private AudioItem getTrack(String id) throws IOException, URISyntaxException {
        var json = this.getJson(PUBLIC_API_BASE + "/tracks/" + id);
        if (json.isNull() || json.get("result").values().get(0).get("available").text().equals("false")) {
            return AudioReference.NO_TRACK;
        }
        return this.parseTrack(json.get("result").values().get(0));
    }

    private AudioItem getArtist(String id) throws IOException, URISyntaxException {
        var json = this.getJson(PUBLIC_API_BASE + "/artists/" + id + "/tracks?page-size=10");
        if (json.isNull() || json.get("result").values().isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        var artistName = this.getJson(PUBLIC_API_BASE + "/artists/" + id).get("result").get("artist").get("name").text();
        var tracks = this.parseTracks(json.get("result").get("tracks"));
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        return new BasicAudioPlaylist(artistName + "'s Top Tracks", tracks, null, false);
    }

    public AudioItem getPlaylist(String userString, String id) throws IOException, URISyntaxException {
        var json = this.getJson(PUBLIC_API_BASE + "/users/" + userString + "/playlists/" + id);
        if (json.isNull() || json.get("result").isNull() || json.get("result").get("tracks").values().isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        var tracks = new ArrayList<AudioTrack>();
        for (var track : json.get("result").get("tracks").values()) {
            tracks.add(this.parseTrack(track.get("track")));
        }
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        var playlist_title = json.get("result").get("kind").text().equals("3") ? "Liked songs" : json.get("result").get("title").text();
        return new BasicAudioPlaylist(playlist_title, tracks, null, false);
    }

    public AudioItem getRadio(String type, String tag) throws IOException, URISyntaxException {
        var station = type + ":" + tag;
        var json = this.getJson(PUBLIC_API_BASE + "/rotor/station/" + station + "/tracks");
        if (json.isNull() || json.get("result").isNull() || json.get("result").get("sequence").values().isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        var tracks = this.parseTracks(json.get("result").get("sequence"), true);
        var batchId = json.get("result").get("batchId").text();
        var station_json = this.getJson(PUBLIC_API_BASE + "/rotor/station/" + station + "/info");
        var station_name = station_json.get("result").values().get(0).get("rupTitle").text();
        return new YandexMusicAudioRadio(station_name, station, batchId, tracks, this);
    }

    public JsonBrowser getJson(String uri) throws IOException, URISyntaxException {
        return getJson(new URI(uri));
    }

    public JsonBrowser getJson(String uri, List<NameValuePair> params) throws IOException, URISyntaxException {
        return getJson(buildUriWithParams(uri, params));
    }

    public JsonBrowser getJson(URI uri) throws IOException, URISyntaxException {
        var request = new HttpGet(uri);
        request.setHeader("Accept", "application/json");
        request.setHeader("Authorization", "OAuth " + this.accessToken);
        return HttpClientTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
    }

    public JsonBrowser getJson(URI uri, Map<String, String> entity) throws IOException {
        var request = new HttpPost(uri);
        request.setHeader("Accept", "application/json");
        request.setHeader("Authorization", "OAuth " + this.accessToken);
        request.setHeader("Content-Type", "application/json");
        ObjectMapper mapper = new ObjectMapper();
        request.setEntity(new StringEntity(mapper.writeValueAsString(entity), ContentType.APPLICATION_JSON));
        System.out.println(mapper.writeValueAsString(entity));
        return HttpClientTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
    }

    private URI buildUriWithParams(String uri, List<NameValuePair> params) throws URISyntaxException {
        return new URIBuilder(uri).addParameters(params).build();
    }

    public String getDownloadStrings(String uri) throws IOException {
        var request = new HttpGet(uri);
        request.setHeader("Accept", "application/json");
        request.setHeader("Authorization", "OAuth " + this.accessToken);
        return HttpClientTools.fetchResponseLines(this.httpInterfaceManager.getInterface(), request, "downloadinfo-xml-page")[0];
    }

    private List<AudioTrack> parseTracks(JsonBrowser json) {
        return parseTracks(json, false);
    }

    private List<AudioTrack> parseTracks(JsonBrowser json, boolean isRadio) {
        var tracks = new ArrayList<AudioTrack>();
        for (var track : json.values()) {
            var parsedTrack = isRadio ? this.parseTrack(track.get("track")) : this.parseTrack(track);
            if (parsedTrack != null) {
                tracks.add(parsedTrack);
            }
        }
        System.out.println(tracks.size());
        return tracks;
    }

    private AudioTrack parseTrack(JsonBrowser json) {
        if (!json.get("available").asBoolean(false) || json.get("albums").values().isEmpty()) {
            return null;
        }
        System.out.println(2);
        var id = json.get("id").text();
        var artist = json.get("major").get("name").text().equals("PODCASTS") ? json.get("albums").values().get(0).get("title").text() : json.get("artists").values().get(0).get("name").text();
        var coverUri = json.get("albums").values().get(0).get("coverUri").text();
        return new YandexMusicAudioTrack(new AudioTrackInfo(
                json.get("title").text(),
                artist,
                json.get("durationMs").as(Long.class),
                id,
                false,
                "https://music.yandex.ru/album/" + json.get("albums").values().get(0).get("id").text() + "/track/" + id),
                coverUri != null ? "https://" + coverUri.replace("%%", "400x400") : null,
                this
        );
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        var yandexMusicAudioTrack = ((YandexMusicAudioTrack) track);
        DataFormatTools.writeNullableText(output, yandexMusicAudioTrack.getArtworkURL());
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new YandexMusicAudioTrack(trackInfo, DataFormatTools.readNullableText(input), this);
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        this.httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        this.httpInterfaceManager.configureBuilder(configurator);
    }

    @Override
    public void shutdown() {
        try {
            this.httpInterfaceManager.close();
        } catch (IOException e) {
            log.error("Failed to close HTTP interface manager", e);
        }
    }

    public HttpInterface getHttpInterface() {
        return this.httpInterfaceManager.getInterface();
    }

    public AudioRadioBatch getRadioBatch(String station, String identifier) throws IOException, URISyntaxException {
        var json = this.getJson(PUBLIC_API_BASE + "/rotor/station/" + station + "/tracks", new ArrayList<>() {{
            add(new BasicNameValuePair("queue", identifier));
        }});

        System.out.println(json.text());

        if (json.isNull() || json.get("result").isNull() || json.get("result").get("sequence").values().isEmpty()) {
            return null;
        }
        var tracks = this.parseTracks(json.get("result").get("sequence"), true);
        var batchId = json.get("result").get("batchId").text();
        return new AudioRadioBatch(batchId, tracks);
    }

    public void radioFeedBack(String station,
                              String action,
                              String trackId,
                              String timestamp,
                              String batchId,
                              String totalPlayedSeconds) throws URISyntaxException, IOException {
        var url = PUBLIC_API_BASE + "/rotor/station/" + station + "/feedback";
        if (timestamp == null) {
            timestamp = String.valueOf(System.currentTimeMillis());
        }
        var data = new HashMap<String, String>();
        var params = new ArrayList<NameValuePair>();
        data.put("type", action);
        data.put("timestamp", timestamp);
        if (batchId != null) {
            params.add(new BasicNameValuePair("batch-id", batchId));
        }
        if (trackId != null) {
            data.put("trackId", trackId);
        }
        if (totalPlayedSeconds != null) {
            data.put("totalPlayedSeconds", totalPlayedSeconds);
        }
        var URI = this.buildUriWithParams(url, params);
        System.out.println(URI);
        System.out.println(data);
        var result = getJson(URI, data);
        System.out.println(result.text());
    }

    public void radioFeedBackRadioStarted(String station, String batchId) throws IOException, URISyntaxException {
        //radioFeedBack(station, "radioStarted", null, null, batchId, null);
    }

    public void radioFeedBackTrackFinished(String station,
                                           AudioTrack currentTrack,
                                           long totalPlayedSeconds,
                                           String batchId) throws URISyntaxException, IOException {
        //radioFeedBack(station, "trackFinished", currentTrack.getIdentifier(), null, batchId, String.valueOf(totalPlayedSeconds));
    }

    public void radioFeedBackTrackStarted(String station, AudioTrack currentTrack, String batchId) throws URISyntaxException, IOException {
        //radioFeedBack(station, "trackStarted", currentTrack.getIdentifier(), null, batchId, null);

    }

    public void radioFeedBackTrackSkipped(String station, AudioTrack currentTrack, long totalPlayedSeconds, String batchId) throws URISyntaxException, IOException {
        //radioFeedBack(station, "skip", currentTrack.getIdentifier(), null, batchId, String.valueOf(totalPlayedSeconds));
    }
}