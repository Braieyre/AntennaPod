package de.danoeh.antennapod.zipimport;

import android.content.Context;
import android.util.AtomicFile;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.database.DBReader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SeriesStore {
    public static final String PREFIX = Feed.PREFIX_LOCAL_FOLDER + "zip:";

    public record EpisodeData(String key, String category, String number, String title,
                              String audio, String cover, String transcript, int durationMs) { }

    public record Series(long feedId, String key, String title, String description,
                         List<String> categories, List<EpisodeData> episodes, String metadataPath) { }

    private SeriesStore() { }

    public static boolean isImported(Feed feed) {
        return feed != null && feed.getDownloadUrl() != null && feed.getDownloadUrl().startsWith(PREFIX);
    }

    public static File directory(Context context) {
        return new File(context.getFilesDir(), "zip-library");
    }

    public static List<Series> list(Context context) throws IOException, JSONException {
        List<Series> result = new ArrayList<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getDownloadUrl() != null && feed.getDownloadUrl().startsWith(PREFIX)) {
                result.add(load(feed));
            }
        }
        result.sort((a, b) -> ZipArchiveScanner.NATURAL_ORDER.compare(a.title(), b.title()));
        return result;
    }

    public static Series load(Feed feed) throws IOException, JSONException {
        JSONObject json = new JSONObject(readText(new File(feed.getLocalFileUrl())));
        if (json.getInt("formatVersion") != 1) {
            throw new IOException("formatVersion");
        }
        List<String> categories = new ArrayList<>();
        JSONArray categoryArray = json.getJSONArray("categories");
        for (int i = 0; i < categoryArray.length(); i++) {
            categories.add(categoryArray.getString(i));
        }
        List<EpisodeData> episodes = new ArrayList<>();
        JSONArray episodeArray = json.getJSONArray("episodes");
        for (int i = 0; i < episodeArray.length(); i++) {
            JSONObject item = episodeArray.getJSONObject(i);
            episodes.add(new EpisodeData(item.getString("key"), item.getString("category"),
                    item.getString("number"), item.getString("title"), item.getString("audio"),
                    item.optString("cover"), item.optString("transcript"), item.getInt("durationMs")));
        }
        return new Series(feed.getId(), json.getString("key"), json.getString("title"),
                json.optString("description"), categories, episodes, feed.getLocalFileUrl());
    }

    public static void write(Series series, File file) throws IOException, JSONException {
        JSONObject json = new JSONObject();
        json.put("formatVersion", 1);
        json.put("key", series.key());
        json.put("title", series.title());
        json.put("description", series.description());
        json.put("categories", new JSONArray(series.categories()));
        JSONArray episodes = new JSONArray();
        for (EpisodeData item : series.episodes()) {
            JSONObject episode = new JSONObject();
            episode.put("key", item.key());
            episode.put("category", item.category());
            episode.put("number", item.number());
            episode.put("title", item.title());
            episode.put("audio", item.audio());
            episode.put("cover", item.cover());
            episode.put("transcript", item.transcript());
            episode.put("durationMs", item.durationMs());
            episodes.put(episode);
        }
        json.put("episodes", episodes);
        writeJson(file, json);
    }

    public static void writeJson(File file, JSONObject json) throws IOException {
        File parent = file.getParentFile();
        if (parent == null || (!parent.mkdirs() && !parent.isDirectory())) {
            throw new IOException(file.toString());
        }
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream stream = atomic.startWrite();
        try {
            stream.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(stream);
        } catch (IOException | JSONException error) {
            atomic.failWrite(stream);
            throw new IOException(error);
        }
    }

    public static String readText(File file) throws IOException {
        if (file.length() > ZipArchiveScanner.MAX_TEXT_BYTES) {
            throw new IOException(ZipArchiveScanner.Problem.LIMIT.name());
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (bytes.size() + read > ZipArchiveScanner.MAX_TEXT_BYTES) {
                    throw new IOException(ZipArchiveScanner.Problem.LIMIT.name());
                }
                bytes.write(buffer, 0, read);
            }
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString().replace("\uFEFF", "");
        }
    }

    static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
