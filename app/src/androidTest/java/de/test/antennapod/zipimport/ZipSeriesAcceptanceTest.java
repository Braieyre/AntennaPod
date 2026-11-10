package de.test.antennapod.zipimport;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.zipimport.SeriesStore;
import de.danoeh.antennapod.zipimport.ZipSeriesImporter;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ZipSeriesAcceptanceTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Copy external test fixtures into app files/test-fixtures first",
                fixture("kitchen-26.zip").isFile());
    }

    @Test
    public void a_importRealArchiveAndMetadata() throws Exception {
        ZipSeriesImporter.Report report = importFile("kitchen-26.zip");
        assertEquals(26, report.imported());
        assertEquals(5, report.categories());
        Feed feed = DBReader.getFeed(report.feedId(), false, 0, Integer.MAX_VALUE);
        SeriesStore.Series series = SeriesStore.load(feed);
        assertEquals(26, feed.getItems().size());
        assertEquals(26, series.episodes().size());
        assertEquals("01-主线", series.categories().get(0));
        assertEquals("05-支线D-厨房实验室", series.categories().get(4));
        assertEquals("01", series.episodes().get(0).number());
        assertEquals("08", series.episodes().get(7).number());
        assertEquals("A1", series.episodes().get(8).number());
        assertEquals("D4", series.episodes().get(25).number());
        assertTrue(series.description().contains("烹饪"));
        Map<String, FeedItem> items = itemsByKey(feed);
        int covers = 0;
        int transcripts = 0;
        long duration = 0;
        try (ZipFile zip = new ZipFile(fixture("kitchen-26.zip"))) {
            for (SeriesStore.EpisodeData episode : series.episodes()) {
                FeedItem item = items.get(episode.key());
                assertNotNull(episode.key(), item);
                assertTrue(item.getMedia().fileExists());
                assertTrue(item.getMedia().isDownloaded());
                assertTrue(item.getMedia().getDuration() > 0);
                assertEquals(episode.durationMs(), item.getMedia().getDuration());
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(episode.cover(), options);
                assertTrue(episode.cover(), options.outWidth > 0 && options.outHeight > 0);
                assertEquals(episode.cover(), item.getImageUrl());
                covers++;
                String transcript = new String(Files.readAllBytes(new File(episode.transcript()).toPath()), StandardCharsets.UTF_8);
                assertTrue(episode.key(), transcript.contains("Muse:") && transcript.contains("Alex:"));
                String prefix = "六分钟厨房课-26集/" + episode.key();
                try (var input = zip.getInputStream(zip.getEntry(prefix + ".mp3"))) {
                    assertArrayEquals(input.readAllBytes(), Files.readAllBytes(new File(episode.audio()).toPath()));
                }
                try (var input = zip.getInputStream(zip.getEntry(prefix + "-封面.webp"))) {
                    assertArrayEquals(input.readAllBytes(), Files.readAllBytes(new File(episode.cover()).toPath()));
                }
                try (var input = zip.getInputStream(zip.getEntry(prefix + "-文字稿.txt"))) {
                    assertArrayEquals(input.readAllBytes(), Files.readAllBytes(new File(episode.transcript()).toPath()));
                }
                transcripts++;
                duration += episode.durationMs();
            }
        }
        assertEquals(26, covers);
        assertEquals(26, transcripts);
        assertEquals(duration, report.durationMs());
        assertTrue(duration > 8_500_000 && duration < 8_700_000);
        record("import", new JSONObject().put("elapsedMs", report.elapsedMs()).put("episodes", 26)
                .put("categories", 5).put("coversByteIdentical", covers)
                .put("transcriptsByteIdentical", transcripts).put("durationMs", duration)
                .put("issues", report.issues()).put("feedId", report.feedId()));
    }

    @Test
    public void b_reimportKeepsProgressAndIds() throws Exception {
        ZipSeriesImporter.Report first = importFile("kitchen-26.zip");
        Feed before = DBReader.getFeed(first.feedId(), false, 0, Integer.MAX_VALUE);
        SeriesStore.Series series = SeriesStore.load(before);
        Map<String, FeedItem> original = itemsByKey(before);
        FeedItem firstItem = original.get(series.episodes().get(0).key());
        FeedMedia media = firstItem.getMedia();
        media.setPosition(12345);
        media.setLastPlayedTimeHistory(new Date(0));
        DBWriter.setFeedMediaPlaybackInformation(media).get();
        FeedItem last = original.get(series.episodes().get(25).key());
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, List.of(last)).get();
        ZipSeriesImporter.Report repeated = importFile("kitchen-26.zip");
        assertEquals(first.feedId(), repeated.feedId());
        Feed after = DBReader.getFeed(repeated.feedId(), false, 0, Integer.MAX_VALUE);
        assertEquals(26, after.getItems().size());
        Map<String, FeedItem> updated = itemsByKey(after);
        for (String key : original.keySet()) {
            assertEquals(original.get(key).getId(), updated.get(key).getId());
            assertEquals(original.get(key).getMedia().getId(), updated.get(key).getMedia().getId());
        }
        assertEquals(12345, updated.get(firstItem.getItemIdentifier()).getMedia().getPosition());
        assertTrue(updated.get(last.getItemIdentifier()).isPlayed());
        record("reimport", new JSONObject().put("episodes", 26).put("idsPreserved", true)
                .put("positionMs", 12345).put("playedPreserved", true));
    }

    @Test
    public void c_secondSeriesIsIndependent() throws Exception {
        ZipSeriesImporter.Report kitchen = importFile("kitchen-26.zip");
        ZipSeriesImporter.Report second = importFile("second-series.zip");
        assertNotEquals(kitchen.feedId(), second.feedId());
        Feed secondFeed = DBReader.getFeed(second.feedId(), false, 0, Integer.MAX_VALUE);
        assertEquals(1, secondFeed.getItems().size());
        Feed kitchenFeed = DBReader.getFeed(kitchen.feedId(), false, 0, Integer.MAX_VALUE);
        assertEquals(26, kitchenFeed.getItems().size());
        assertEquals(5, SeriesStore.load(kitchenFeed).categories().size());
        assertFalse(secondFeed.getLocalFileUrl().equals(kitchenFeed.getLocalFileUrl()));
        record("multipleSeries", new JSONObject().put("firstEpisodes", 26).put("secondEpisodes", 1)
                .put("separateFeedIds", true));
    }

    @Test
    public void d_badEntriesAndBrokenArchive() throws Exception {
        ZipSeriesImporter.Report partial = importFile("partial-invalid.zip");
        assertEquals(1, partial.imported());
        String issues = String.join("\n", partial.issues());
        assertTrue(issues, issues.contains("X2"));
        assertTrue(issues, issues.contains("X3"));
        assertTrue(issues, issues.contains("02-空分类"));
        assertEquals(1, DBReader.getFeed(partial.feedId(), false, 0, Integer.MAX_VALUE).getItems().size());
        int feedCount = DBReader.getFeedList().size();
        for (String name : List.of("corrupt.zip", "zip-slip.zip")) {
            try {
                importFile(name);
                fail("Expected failed import for " + name);
            } catch (Exception expected) {
                assertEquals(feedCount, DBReader.getFeedList().size());
            }
        }
        assertFalse(new File(context.getFilesDir(), "escape.txt").exists());
        record("badArchives", new JSONObject().put("validEpisodeRetained", 1)
                .put("perEntryIssues", partial.issues()).put("corruptRejected", true)
                .put("pathTraversalRejected", true).put("databaseUnchangedOnFailure", true));
    }

    @Test
    public void e_cancellationDoesNotChangeLibrary() throws Exception {
        ZipSeriesImporter.Report before = importFile("kitchen-26.zip");
        String metadata = DBReader.getFeed(before.feedId(), false, 0, Integer.MAX_VALUE).getLocalFileUrl();
        try {
            ZipSeriesImporter.importArchive(context, fixture("kitchen-26.zip"), "kitchen-26.zip",
                    (stage, current, total, imported) -> { }, () -> true);
            fail("Expected cancellation");
        } catch (Exception expected) {
            assertEquals(metadata, DBReader.getFeed(before.feedId(), false, 0, Integer.MAX_VALUE).getLocalFileUrl());
        }
        record("cancel", new JSONObject().put("priorMetadataPreserved", true));
    }

    private File fixture(String name) {
        return new File(new File(context.getFilesDir(), "test-fixtures"), name);
    }

    private ZipSeriesImporter.Report importFile(String name) throws Exception {
        return ZipSeriesImporter.importArchive(context, fixture(name), name,
                (stage, current, total, imported) -> { }, () -> false);
    }

    private Map<String, FeedItem> itemsByKey(Feed feed) {
        Map<String, FeedItem> result = new HashMap<>();
        for (FeedItem item : feed.getItems()) {
            result.put(item.getItemIdentifier(), item);
        }
        return result;
    }

    private void record(String key, JSONObject value) throws Exception {
        File file = new File(context.getFilesDir(), "acceptance-results.json");
        JSONObject results = file.isFile() ? new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)) : new JSONObject();
        results.put(key, value);
        Files.write(file.toPath(), results.toString(2).getBytes(StandardCharsets.UTF_8));
        Log.i("ZIP_ACCEPTANCE", key + ": " + value);
    }
}
