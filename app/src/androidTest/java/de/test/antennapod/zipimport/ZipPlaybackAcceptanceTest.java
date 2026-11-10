package de.test.antennapod.zipimport;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionResult;
import androidx.media3.session.SessionToken;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.common.util.concurrent.ListenableFuture;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.SleepTimerType;
import de.danoeh.antennapod.playback.service.Media3PlaybackService;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.playback.service.internal.MediaLibrarySessionCallback;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.zipimport.SeriesPlayback;
import de.danoeh.antennapod.zipimport.SeriesStore;
import de.danoeh.antennapod.zipimport.ZipSeriesImporter;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ZipPlaybackAcceptanceTest {
    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class, false, false);

    @Test
    public void realAudioResumeSpeedLockScreenSleepAndAutomaticNext() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File archive = new File(context.getFilesDir(), "test-fixtures/kitchen-26.zip");
        assertTrue(archive.isFile());
        ZipSeriesImporter.Report report = ZipSeriesImporter.importArchive(context, archive, "kitchen-26.zip",
                (stage, current, total, imported) -> { }, () -> false);
        Feed feed = DBReader.getFeed(report.feedId(), false, 0, Integer.MAX_VALUE);
        SeriesStore.Series series = SeriesStore.load(feed);
        FeedItem first = feed.getItems().stream()
                .filter(item -> item.getItemIdentifier().equals(series.episodes().get(0).key())).findFirst().orElseThrow();
        FeedItem second = feed.getItems().stream()
                .filter(item -> item.getItemIdentifier().equals(series.episodes().get(1).key())).findFirst().orElseThrow();
        first.getMedia().setPosition(45000);
        first.getMedia().setLastPlayedTimeHistory(new Date(0));
        first.getMedia().setLastPlayedTimeStatistics(System.currentTimeMillis());
        DBWriter.setFeedMediaPlaybackInformation(first.getMedia()).get();
        UserPreferences.setFollowQueue(true);
        SleepTimerPreferences.setAutoEnable(false);
        SleepTimerPreferences.setShakeToReset(false);
        SleepTimerPreferences.setVibrate(false);
        activityRule.launchActivity(new Intent());
        FeedItem prepared = SeriesPlayback.prepare(context, series, series.episodes().get(0).key());
        assertEquals(26, DBReader.getQueue().size());
        assertEquals(first.getId(), DBReader.getQueue().get(0).getId());
        assertEquals(second.getId(), DBReader.getQueue().get(1).getId());
        callMain(() -> {
            new PlaybackServiceStarter(context, prepared.getMedia()).callEvenIfRunning(true).start();
            return null;
        });
        ListenableFuture<MediaController> future = callMain(() -> new MediaController.Builder(context,
                new SessionToken(context, new ComponentName(context, Media3PlaybackService.class))).buildAsync());
        MediaController controller = future.get(20, TimeUnit.SECONDS);
        try {
            await("First imported MP3 must actually play", 20000, () -> callMain(controller::isPlaying));
            long resumedAt = callMain(controller::getCurrentPosition);
            assertTrue("Resume position " + resumedAt, resumedAt >= 40000 && resumedAt < 55000);
            assertEquals(Long.toString(first.getMedia().getId()), callMain(() -> controller.getCurrentMediaItem().mediaId));
            callMain(() -> {
                controller.setPlaybackSpeed(1.5f);
                return null;
            });
            await("Playback speed", 5000,
                    () -> callMain(() -> Math.abs(controller.getPlaybackParameters().speed - 1.5f) < 0.01));
            long beforeLock = callMain(controller::getCurrentPosition);
            shell("input keyevent 3");
            shell("input keyevent 223");
            SystemClock.sleep(3000);
            assertTrue("Audio must continue with display off", callMain(controller::isPlaying));
            long afterLock = callMain(controller::getCurrentPosition);
            assertTrue(afterLock > beforeLock + 2000);
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            callMain(() -> {
                context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                controller.setPlaybackSpeed(1.0f);
                return null;
            });
            SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
            SleepTimerPreferences.setLastTimer("1");
            ListenableFuture<SessionResult> sleep = callMain(() -> controller.sendCustomCommand(
                    MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY));
            assertEquals(SessionResult.RESULT_SUCCESS, sleep.get(5, TimeUnit.SECONDS).resultCode);
            await("Sleep timer must arm", 5000, () -> {
                SleepTimerUpdatedEvent event = EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent.class);
                return event != null && !event.isCancelled() && !event.isOver();
            });
            long timerStarted = SystemClock.elapsedRealtime();
            await("One-minute sleep timer must pause actual playback", 75000, () -> !callMain(controller::isPlaying));
            long timerElapsed = SystemClock.elapsedRealtime() - timerStarted;
            assertTrue("Timer elapsed " + timerElapsed, timerElapsed >= 55000 && timerElapsed < 75000);
            callMain(() -> {
                controller.sendCustomCommand(MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER,
                        Bundle.EMPTY);
                controller.play();
                controller.seekTo(controller.getDuration() - 1500);
                return null;
            });
            await("Episode must advance automatically", 15000, () -> callMain(() ->
                    controller.getCurrentMediaItem() != null && controller.getCurrentMediaItem().mediaId
                            .equals(Long.toString(second.getMedia().getId())) && controller.isPlaying()));
            callMain(() -> {
                controller.seekTo(23567);
                return null;
            });
            SystemClock.sleep(1500);
            callMain(() -> {
                controller.pause();
                return null;
            });
            await("Player position must persist to database", 10000,
                    () -> DBReader.getFeedItem(second.getId()).getMedia().getPosition() >= 20000);
            FeedItem saved = DBReader.getFeedItem(second.getId());
            assertNotNull(saved);
            JSONObject playback = new JSONObject().put("mp3ActuallyPlayed", true).put("resumedAtMs", resumedAt)
                    .put("speed", 1.5).put("backgroundDisplayOffAdvancedMs", afterLock - beforeLock)
                    .put("sleepTimerActualElapsedMs", timerElapsed).put("automaticNext", true)
                    .put("savedItemId", saved.getId()).put("savedMediaId", saved.getMedia().getId())
                    .put("savedPositionMs", saved.getMedia().getPosition());
            File resultsFile = new File(context.getFilesDir(), "acceptance-results.json");
            JSONObject results = resultsFile.isFile() ? new JSONObject(new String(Files.readAllBytes(resultsFile.toPath()), StandardCharsets.UTF_8))
                    : new JSONObject();
            results.put("playback", playback);
            Files.write(resultsFile.toPath(), results.toString(2).getBytes(StandardCharsets.UTF_8));
            Log.i("ZIP_ACCEPTANCE", "playback: " + playback);
        } finally {
            callMain(() -> {
                controller.pause();
                controller.release();
                return null;
            });
        }
    }

    private <T> T callMain(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
        return task.get();
    }

    private void shell(String command) throws Exception {
        try (var descriptor = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command)) {
            assertNotNull(descriptor);
        }
    }

    private void await(String message, long timeout, Check check) throws Exception {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < end) {
            if (check.get()) {
                return;
            }
            SystemClock.sleep(100);
        }
        assertTrue(message, check.get());
    }

    private interface Check {
        boolean get() throws Exception;
    }
}
