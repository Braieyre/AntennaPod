package de.danoeh.antennapod.zipimport;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.text.Html;
import android.text.TextUtils;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.playback.service.Media3PlaybackService;
import de.danoeh.antennapod.playback.service.PlaybackService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZipSeriesImporter {
    public record Report(long feedId, int imported, int retained, int categories, long durationMs,
                         long elapsedMs, List<String> issues) { }

    public interface Progress {
        void update(String stage, int current, int total, int imported);
    }

    private ZipSeriesImporter() { }

    public static synchronized Report importArchive(Context context, File archive, String fallbackName,
                                                    Progress progress, ZipArchiveScanner.Cancellation cancellation)
            throws Exception {
        long started = System.currentTimeMillis();
        File directory = SeriesStore.directory(context);
        File[] abandoned = directory.listFiles(file -> file.getName().startsWith("staging-"));
        if (abandoned != null) {
            for (File file : abandoned) {
                SeriesStore.deleteTree(file);
            }
        }
        File staging = new File(directory, "staging-" + UUID.randomUUID());
        File generation = null;
        boolean committed = false;
        try {
            ZipArchiveScanner.Scan scan = ZipArchiveScanner.extract(archive, staging,
                    (done, total) -> progress.update("extract", done, total, 0), cancellation);
            String rootName = scan.root().equals(staging)
                    ? fallbackName.replaceFirst("(?i)\\.zip$", "") : scan.root().getName();
            if (rootName.trim().isEmpty()) {
                rootName = archive.getName().replaceFirst("(?i)\\.zip$", "");
            }
            String identity = digest(Normalizer.normalize(rootName, Normalizer.Form.NFC));
            Feed existingFeed = null;
            SeriesStore.Series existing = null;
            for (Feed feed : DBReader.getFeedList()) {
                if ((SeriesStore.PREFIX + identity).equals(feed.getDownloadUrl())) {
                    existingFeed = feed;
                    existing = SeriesStore.load(feed);
                    break;
                }
            }
            Map<String, SeriesStore.EpisodeData> previous = new LinkedHashMap<>();
            if (existing != null) {
                for (SeriesStore.EpisodeData item : existing.episodes()) {
                    previous.put(item.key(), item);
                }
            }
            List<ZipArchiveScanner.Issue> issues = new ArrayList<>(scan.issues());
            validateManifest(scan.root(), issues);
            Map<String, Integer> durations = playlistDurations(scan.root());
            List<SeriesStore.EpisodeData> valid = new ArrayList<>();
            Map<String, String> texts = new HashMap<>();
            int scanned = 0;
            for (ZipArchiveScanner.Episode episode : scan.episodes()) {
                ZipArchiveScanner.checkCancelled(cancellation);
                int duration;
                try {
                    duration = duration(episode.audio());
                } catch (Exception error) {
                    issues.add(new ZipArchiveScanner.Issue(ZipArchiveScanner.Problem.BAD_AUDIO, episode.key()));
                    progress.update("validate", ++scanned, scan.episodes().size(), valid.size());
                    continue;
                }
                Integer reference = durations.get(episode.category() + "/" + episode.number());
                if (reference != null && Math.abs(duration - reference * 1000L) > 2000) {
                    issues.add(new ZipArchiveScanner.Issue(ZipArchiveScanner.Problem.DURATION,
                            episode.key() + " (" + duration + "ms / " + reference + "s)"));
                }
                SeriesStore.EpisodeData old = previous.get(episode.key());
                String cover = old == null ? "" : old.cover();
                String transcript = old == null ? "" : old.transcript();
                if (episode.cover().isFile()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(episode.cover().getAbsolutePath(), options);
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        cover = episode.cover().getAbsolutePath();
                    } else {
                        issues.add(new ZipArchiveScanner.Issue(ZipArchiveScanner.Problem.BAD_COVER, episode.key()));
                    }
                }
                if (episode.transcript().isFile()) {
                    try {
                        String text = SeriesStore.readText(episode.transcript());
                        if (text.trim().isEmpty()) {
                            throw new IOException();
                        }
                        transcript = episode.transcript().getAbsolutePath();
                        texts.put(episode.key(), text);
                    } catch (IOException error) {
                        issues.add(new ZipArchiveScanner.Issue(ZipArchiveScanner.Problem.BAD_TRANSCRIPT, episode.key()));
                    }
                }
                valid.add(new SeriesStore.EpisodeData(episode.key(), episode.category(), episode.number(),
                        episode.title(), episode.audio().getAbsolutePath(), cover, transcript, duration));
                progress.update("validate", ++scanned, scan.episodes().size(), valid.size());
            }
            if (valid.isEmpty()) {
                List<String> messages = issueMessages(context, issues);
                throw new IOException(context.getString(R.string.zip_no_valid_audio) + "\n" + TextUtils.join("\n", messages));
            }
            ZipArchiveScanner.checkCancelled(cancellation);
            File seriesDirectory = new File(directory, identity);
            if (!seriesDirectory.mkdirs() && !seriesDirectory.isDirectory()) {
                throw new IOException(seriesDirectory.toString());
            }
            generation = new File(seriesDirectory, UUID.randomUUID().toString());
            String oldRoot = staging.getAbsolutePath();
            String newRoot = generation.getAbsolutePath();
            String title = rootName;
            String description = "";
            try {
                File outline = new File(scan.root(), "系列总纲.md");
                File playlist = new File(scan.root(), "播放清单.md");
                String markdown = outline.isFile() ? SeriesStore.readText(outline)
                        : playlist.isFile() ? SeriesStore.readText(playlist) : "";
                Matcher titleMatcher = Pattern.compile("(?m)^#\\s*《([^》]+)》").matcher(markdown);
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1);
                }
                Matcher descriptionMatcher = Pattern.compile("(?ms)^##\\s*定位\\s*\\n(.*?)(?=^##|\\z)").matcher(markdown);
                description = descriptionMatcher.find() ? descriptionMatcher.group(1).trim()
                        : markdown.split("(?m)^##", 2)[0].replaceFirst("(?m)^#[^\\n]*\\n?", "").trim();
            } catch (Exception error) {
                description = rootName;
            }
            if (!staging.renameTo(generation)) {
                throw new IOException(generation.toString());
            }
            Map<String, SeriesStore.EpisodeData> merged = new LinkedHashMap<>(previous);
            for (SeriesStore.EpisodeData item : valid) {
                merged.put(item.key(), new SeriesStore.EpisodeData(item.key(), item.category(), item.number(),
                        item.title(), movedPath(item.audio(), oldRoot, newRoot),
                        movedPath(item.cover(), oldRoot, newRoot), movedPath(item.transcript(), oldRoot, newRoot),
                        item.durationMs()));
            }
            List<SeriesStore.EpisodeData> episodes = new ArrayList<>(merged.values());
            episodes.sort((a, b) -> {
                int category = ZipArchiveScanner.NATURAL_ORDER.compare(a.category(), b.category());
                return category != 0 ? category : ZipArchiveScanner.NATURAL_ORDER.compare(a.number(), b.number());
            });
            Set<String> categorySet = new HashSet<>(scan.categories());
            for (SeriesStore.EpisodeData item : episodes) {
                categorySet.add(item.category());
            }
            List<String> categories = new ArrayList<>(categorySet);
            categories.sort(ZipArchiveScanner.NATURAL_ORDER);
            File metadata = new File(generation, "series.json");
            SeriesStore.Series series = new SeriesStore.Series(existingFeed == null ? 0 : existingFeed.getId(),
                    identity, title, description, categories, episodes, metadata.getAbsolutePath());
            SeriesStore.write(series, metadata);
            Feed feed = new Feed(SeriesStore.PREFIX + identity, null, title);
            feed.setLocalFileUrl(metadata.getAbsolutePath());
            feed.setDescription(description);
            feed.setSortOrder(SortOrder.DATE_OLD_NEW);
            FeedPreferences preferences = new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.DISABLED,
                    FeedPreferences.AutoDeleteAction.NEVER, VolumeAdaptionSetting.OFF,
                    FeedPreferences.NewEpisodesAction.NOTHING, null, null);
            preferences.setKeepUpdated(false);
            feed.setPreferences(preferences);
            List<FeedItem> items = new ArrayList<>();
            for (int index = 0; index < episodes.size(); index++) {
                SeriesStore.EpisodeData episode = episodes.get(index);
                FeedItem item = new FeedItem();
                item.setFeed(feed);
                item.setTitle(episode.number() + " · " + episode.title());
                item.setItemIdentifier(episode.key());
                item.setPubDate(new Date(946684800000L + index * 1000L));
                item.setImageUrl(episode.cover().isEmpty() ? null : episode.cover());
                String text = texts.get(episode.key());
                if (text == null && !episode.transcript().isEmpty()) {
                    text = SeriesStore.readText(new File(episode.transcript()));
                }
                item.setDescriptionIfLonger(text == null ? "" : "<pre>" + Html.escapeHtml(text) + "</pre>");
                FeedMedia media = new FeedMedia(item, episode.audio(), new File(episode.audio()).length(), "audio/mpeg");
                media.setLocalFileUrl(episode.audio());
                media.setDuration(episode.durationMs());
                media.setDownloaded(true, System.currentTimeMillis());
                media.setHasEmbeddedPicture(false);
                item.setMedia(media);
                item.disableAutoDownload();
                items.add(item);
                if (feed.getImageUrl() == null && !episode.cover().isEmpty()) {
                    feed.setImageUrl(episode.cover());
                }
            }
            feed.setItems(items);
            ZipArchiveScanner.checkCancelled(cancellation);
            progress.update("commit", valid.size(), valid.size(), 0);
            long feedId = DBWriter.importLocalSeries(feed).get();
            committed = true;
            long duration = 0;
            for (SeriesStore.EpisodeData episode : episodes) {
                duration += episode.durationMs();
            }
            Report report = new Report(feedId, valid.size(), episodes.size() - valid.size(), categories.size(),
                    duration, System.currentTimeMillis() - started, issueMessages(context, issues));
            saveReport(context, report);
            progress.update("done", valid.size(), valid.size(), valid.size());
            cleanupGenerations(context, seriesDirectory, series);
            return report;
        } finally {
            SeriesStore.deleteTree(staging);
            if (!committed && generation != null) {
                SeriesStore.deleteTree(generation);
            }
        }
    }

    private static String movedPath(String path, String source, String target) {
        return path.startsWith(source + File.separator) ? target + path.substring(source.length()) : path;
    }

    private static int duration(File audio) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            extractor.setDataSource(audio.getAbsolutePath());
            boolean found = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    if (extractor.readSampleData(ByteBuffer.allocate(1024 * 1024), 0) <= 0) {
                        throw new IOException();
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IOException();
            }
            retriever.setDataSource(audio.getAbsolutePath());
            long result = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (result <= 0 || result > Integer.MAX_VALUE) {
                throw new IOException();
            }
            return (int) result;
        } finally {
            extractor.release();
            retriever.release();
        }
    }

    private static Map<String, Integer> playlistDurations(File root) {
        Map<String, Integer> result = new HashMap<>();
        try {
            String category = "";
            Pattern linePattern = Pattern.compile("^-\\s+(\\S+)\\s+.*?[（(](\\d+)\\s*秒[）)]");
            for (String line : SeriesStore.readText(new File(root, "播放清单.md")).split("\\n")) {
                if (line.startsWith("## ")) {
                    category = line.substring(3).trim();
                }
                Matcher matcher = linePattern.matcher(line);
                if (matcher.find()) {
                    result.put(category + "/" + matcher.group(1), Integer.parseInt(matcher.group(2)));
                }
            }
        } catch (Exception error) {
            result.clear();
        }
        return result;
    }

    private static void validateManifest(File root, List<ZipArchiveScanner.Issue> issues) {
        File manifest = new File(root, "manifest.json");
        if (!manifest.isFile()) {
            return;
        }
        try {
            JSONObject json = new JSONObject(SeriesStore.readText(manifest));
            validateManifestValues(root, json, issues);
            if (json.has("episodeCount")) {
                int count = ZipArchiveScanner.scan(root, Collections.emptyList()).episodes().size();
                if (json.getInt("episodeCount") != count) {
                    issues.add(new ZipArchiveScanner.Issue(ZipArchiveScanner.Problem.MANIFEST, "episodeCount"));
                }
            }
        } catch (Exception error) {
            issues.add(new ZipArchiveScanner.Issue(ZipArchiveScanner.Problem.MANIFEST, "manifest.json"));
        }
    }

    private static void validateManifestValues(File root, Object value, List<ZipArchiveScanner.Issue> issues)
            throws JSONException, IOException {
        if (value instanceof JSONObject object) {
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                validateManifestValues(root, object.get(keys.next()), issues);
            }
        } else if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                validateManifestValues(root, array.get(i), issues);
            }
        } else if (value instanceof String path && path.matches("(?i).+\\.(mp3|webp|txt)$")) {
            File target = new File(root, path);
            if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator) || !target.isFile()) {
                issues.add(new ZipArchiveScanner.Issue(ZipArchiveScanner.Problem.MANIFEST, path));
            }
        }
    }

    private static List<String> issueMessages(Context context, List<ZipArchiveScanner.Issue> issues) {
        List<String> result = new ArrayList<>();
        String[] labels = context.getResources().getStringArray(R.array.zip_issue_labels);
        for (ZipArchiveScanner.Issue issue : issues) {
            result.add(labels[issue.problem().ordinal()] + ": " + issue.path());
        }
        return result;
    }

    public static void saveReport(Context context, Report report) throws IOException, JSONException {
        JSONObject json = new JSONObject();
        json.put("feedId", report.feedId());
        json.put("imported", report.imported());
        json.put("retained", report.retained());
        json.put("categories", report.categories());
        json.put("durationMs", report.durationMs());
        json.put("elapsedMs", report.elapsedMs());
        json.put("issues", new JSONArray(report.issues()));
        SeriesStore.writeJson(new File(SeriesStore.directory(context), "last-report.json"), json);
    }

    private static String digest(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return result.toString();
    }

    private static boolean playbackServiceActive(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return true;
            }
            List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
            if (services == null) {
                return true;
            }
            for (ActivityManager.RunningServiceInfo service : services) {
                String name = service.service.getClassName();
                if (context.getPackageName().equals(service.service.getPackageName())
                        && (Media3PlaybackService.class.getName().equals(name)
                        || PlaybackService.class.getName().equals(name))) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException error) {
            return true;
        }
    }

    private static void cleanupGenerations(Context context, File directory, SeriesStore.Series series) {
        if (playbackServiceActive(context)) {
            return;
        }
        Set<String> paths = new HashSet<>();
        paths.add(series.metadataPath());
        for (SeriesStore.EpisodeData item : series.episodes()) {
            paths.add(item.audio());
            paths.add(item.cover());
            paths.add(item.transcript());
        }
        File[] generations = directory.listFiles();
        if (generations == null) {
            return;
        }
        for (File generation : generations) {
            boolean used = false;
            for (String path : paths) {
                if (path.startsWith(generation.getAbsolutePath() + File.separator)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                SeriesStore.deleteTree(generation);
            }
        }
    }
}
