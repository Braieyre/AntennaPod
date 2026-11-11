package de.danoeh.antennapod.zipimport;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SeriesEpisodeFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout content;

    public static SeriesEpisodeFragment newInstance(long feed, String key) {
        SeriesEpisodeFragment fragment = new SeriesEpisodeFragment();
        Bundle arguments = new Bundle();
        arguments.putLong("feed", feed);
        arguments.putString("key", key);
        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        LinearLayout root = PaperUi.column(requireContext());
        PaperUi.toolbar(this, root, R.string.paper_episode, true);
        ScrollView scroll = new ScrollView(requireContext());
        content = PaperUi.column(requireContext());
        PaperUi.inset(content);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Context context = requireContext().getApplicationContext();
        LinearLayout target = content;
        long feedId = requireArguments().getLong("feed");
        String key = requireArguments().getString("key");
        executor.execute(() -> {
            try {
                Feed feed = DBReader.getFeed(feedId, false, 0, Integer.MAX_VALUE);
                SeriesStore.Series series = SeriesStore.load(feed);
                for (SeriesStore.EpisodeData episode : series.episodes()) {
                    if (!episode.key().equals(key)) {
                        continue;
                    }
                    FeedItem item = null;
                    for (FeedItem candidate : feed.getItems()) {
                        if (key.equals(candidate.getItemIdentifier())) {
                            item = candidate;
                            break;
                        }
                    }
                    String transcript = episode.transcript().isEmpty() ? ""
                            : SeriesStore.readText(new File(episode.transcript()));
                    FeedItem queuedItem = item;
                    target.post(() -> {
                        if (target == content && isAdded()) {
                            render(series, episode, queuedItem, transcript);
                        }
                    });
                    return;
                }
            } catch (Exception error) {
                target.post(() -> {
                    if (target == content && isAdded()) {
                        content.addView(PaperUi.text(requireContext(), context.getString(R.string.zip_load_failed,
                                error.getMessage()), 16, true));
                    }
                });
            }
        });
        return root;
    }

    private void render(SeriesStore.Series series, SeriesStore.EpisodeData episode, FeedItem item, String transcript) {
        Context context = requireContext();
        content.addView(PaperUi.cover(this, episode.cover(), 210));
        TextView title = PaperUi.text(context, episode.number() + " · " + episode.title(), 25, false);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setPadding(0, PaperUi.dp(context, 22), 0, PaperUi.dp(context, 8));
        content.addView(title);
        content.addView(PaperUi.text(context, series.title() + " · " + episode.category() + " · "
                + getString(R.string.zip_duration, episode.durationMs() / 60000, episode.durationMs() / 1000 % 60), 13, true));
        LinearLayout actions = new LinearLayout(context);
        MaterialButton play = PaperUi.button(context, R.string.paper_play, true);
        play.setIconResource(R.drawable.ic_play_24dp);
        play.setOnClickListener(view -> executor.execute(() -> {
            try {
                SeriesPlayback.play(context.getApplicationContext(), series, episode.key());
            } catch (Exception error) {
                play.post(() -> {
                    if (isAdded() && content != null) {
                        Snackbar.make(play, getString(R.string.zip_failed, error.getMessage()), Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        }));
        actions.addView(play, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton queue = PaperUi.button(context, R.string.paper_add_queue, false);
        queue.setOnClickListener(view -> {
            if (item != null) {
                DBWriter.addQueueItem(context.getApplicationContext(), item);
                Snackbar.make(queue, R.string.paper_queued, Snackbar.LENGTH_SHORT).show();
            }
        });
        actions.addView(queue);
        content.addView(actions);
        MaterialButton full = PaperUi.button(context, R.string.paper_read_transcript, false);
        full.setOnClickListener(view -> ((MainActivity) requireActivity()).loadChildFragment(
                SeriesTranscriptFragment.newInstance(episode.title(), episode.transcript())));
        content.addView(full, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!transcript.isEmpty()) {
            TextView preview = PaperUi.text(context, transcript, 16, true);
            preview.setMaxLines(5);
            preview.setPadding(0, PaperUi.dp(context, 22), 0, 0);
            content.addView(preview);
        }
    }

    @Override
    public void onDestroyView() {
        content = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
