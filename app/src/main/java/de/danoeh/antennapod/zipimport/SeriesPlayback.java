package de.danoeh.antennapod.zipimport;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SeriesPlayback {
    private SeriesPlayback() { }

    public static FeedItem prepare(Context context, SeriesStore.Series series, String episodeKey) throws Exception {
        Feed feed = DBReader.getFeed(series.feedId(), false, 0, Integer.MAX_VALUE);
        Map<String, FeedItem> byKey = new HashMap<>();
        if (feed != null) {
            for (FeedItem item : feed.getItems()) {
                byKey.put(item.getItemIdentifier(), item);
            }
        }
        List<FeedItem> queue = new ArrayList<>();
        boolean include = false;
        for (SeriesStore.EpisodeData item : series.episodes()) {
            include |= item.key().equals(episodeKey);
            if (include && byKey.containsKey(item.key())) {
                queue.add(byKey.get(item.key()));
            }
        }
        if (queue.isEmpty() || !queue.get(0).getMedia().fileExists()) {
            throw new IllegalStateException(context.getString(R.string.zip_missing_media));
        }
        DBWriter.setImportedSeriesQueue(queue).get();
        return queue.get(0);
    }

    public static void play(Context context, SeriesStore.Series series, String episodeKey) throws Exception {
        FeedItem item = prepare(context, series, episodeKey);
        new Handler(Looper.getMainLooper()).post(() ->
                new PlaybackServiceStarter(context, item.getMedia()).callEvenIfRunning(true).start());
    }
}
