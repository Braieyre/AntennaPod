package de.danoeh.antennapod.zipimport;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import de.danoeh.antennapod.ui.screen.home.HomeFragment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SeriesLibraryFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<SeriesStore.Series> seriesList = new ArrayList<>();
    private final List<SeriesStore.EpisodeData> displayed = new ArrayList<>();
    private final Map<String, FeedItem> items = new HashMap<>();
    private SeriesStore.Series selected;
    private long selectedId;
    private LinearLayout root;
    private Button importButton;
    private Button cancelButton;
    private Button retryButton;
    private Button backButton;
    private TextView status;
    private TextView description;
    private TextView empty;
    private ProgressBar progress;
    private Spinner categories;
    private EditText search;
    private ListView list;
    private LibraryAdapter adapter;
    private String lastWorkId;
    private String category = "";
    private String query = "";
    private boolean running;
    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null
                        || result.getData().getData() == null) {
                    return;
                }
                Intent data = result.getData();
                Uri uri = data.getData();
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(uri,
                            data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                } catch (SecurityException error) {
                    Toast.makeText(requireContext(), getString(R.string.zip_failed, error.getMessage()),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Context context = requireContext().getApplicationContext();
                executor.execute(() -> {
                    String name = uri.getLastPathSegment();
                    try (Cursor cursor = context.getContentResolver().query(uri,
                            new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            name = cursor.getString(0);
                        }
                    } catch (Exception ignored) {
                        name = uri.getLastPathSegment();
                    }
                    context.getSharedPreferences("zip-import", Context.MODE_PRIVATE).edit()
                            .putString("uri", uri.toString()).putString("name", name).apply();
                    enqueue(context, uri.toString(), name);
                });
            });

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        if (savedState != null) {
            selectedId = savedState.getLong("selected");
            category = savedState.getString("category", "");
            query = savedState.getString("query", "");
        }
        root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new MaterialToolbar(requireContext());
        toolbar.setTitle(R.string.zip_library);
        toolbar.getMenu().add(R.string.zip_original_home).setOnMenuItemClickListener(item -> {
            ((MainActivity) requireActivity()).loadChildFragment(new HomeFragment());
            return true;
        });
        root.addView(toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout()).top;
            view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), 0);
            view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (56 * getResources().getDisplayMetrics().density) + top));
            return insets;
        });
        ViewCompat.requestApplyInsets(toolbar);
        ((MainActivity) requireActivity()).setupToolbarToggle(toolbar, false);
        LinearLayout actions = new LinearLayout(requireContext());
        importButton = button(R.string.zip_import);
        actions.addView(importButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button report = button(R.string.zip_report);
        actions.addView(report);
        root.addView(actions);
        progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        root.addView(progress);
        status = new TextView(requireContext());
        root.addView(status);
        LinearLayout workActions = new LinearLayout(requireContext());
        cancelButton = button(R.string.zip_cancel);
        retryButton = button(R.string.zip_retry);
        workActions.addView(cancelButton);
        workActions.addView(retryButton);
        root.addView(workActions);
        cancelButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        backButton = button(R.string.zip_back);
        root.addView(backButton);
        description = new TextView(requireContext());
        description.setTextSize(15);
        int pad = dp(12);
        description.setPadding(pad, pad, pad, pad);
        description.setMaxLines(7);
        root.addView(description);
        categories = new Spinner(requireContext());
        root.addView(categories);
        search = new EditText(requireContext());
        search.setSingleLine(true);
        search.setHint(R.string.zip_search);
        search.setText(query);
        root.addView(search);
        empty = new TextView(requireContext());
        empty.setPadding(pad, pad, pad, pad);
        root.addView(empty);
        list = new ListView(requireContext());
        adapter = new LibraryAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        importButton.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed",
                    "application/octet-stream"});
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            picker.launch(intent);
        });
        report.setOnClickListener(view -> showReport());
        cancelButton.setOnClickListener(view -> WorkManager.getInstance(requireContext())
                .cancelUniqueWork(ZipImportWorker.WORK_NAME));
        retryButton.setOnClickListener(view -> {
            Context context = requireContext().getApplicationContext();
            String uri = context.getSharedPreferences("zip-import", Context.MODE_PRIVATE).getString("uri", null);
            String name = context.getSharedPreferences("zip-import", Context.MODE_PRIVATE).getString("name", null);
            if (uri != null) {
                enqueue(context, uri, name);
            }
        });
        backButton.setOnClickListener(view -> {
            selectedId = 0;
            selected = null;
            category = "";
            query = "";
            search.setText("");
            render();
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (selected == null) {
                selected = seriesList.get(position);
                selectedId = selected.feedId();
                category = "";
                reload();
            } else {
                showEpisode(displayed.get(position));
            }
        });
        categories.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                category = position == 0 || selected == null ? "" : selected.categories().get(position - 1);
                filter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                query = value.toString();
                filter();
            }

            @Override
            public void afterTextChanged(Editable value) { }
        });
        WorkManager.getInstance(requireContext()).getWorkInfosForUniqueWorkLiveData(ZipImportWorker.WORK_NAME)
                .observe(getViewLifecycleOwner(), this::updateWork);
        return root;
    }

    private static void enqueue(Context context, String uri, String name) {
        Data.Builder input = new Data.Builder().putString(ZipImportWorker.URI, uri);
        if (name != null) {
            input.putString(ZipImportWorker.NAME, name);
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ZipImportWorker.class)
                .setInputData(input.build()).build();
        WorkManager.getInstance(context).enqueueUniqueWork(ZipImportWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP, request);
    }

    private void updateWork(List<WorkInfo> work) {
        if (work.isEmpty()) {
            progress.setVisibility(View.GONE);
            return;
        }
        WorkInfo info = work.get(work.size() - 1);
        for (WorkInfo candidate : work) {
            if (!candidate.getState().isFinished()) {
                info = candidate;
                break;
            }
        }
        running = !info.getState().isFinished();
        importButton.setEnabled(!running);
        cancelButton.setVisibility(running ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(!running && info.getState() != WorkInfo.State.SUCCEEDED ? View.VISIBLE : View.GONE);
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        Data data = info.getProgress();
        String stage = data.getString(ZipImportWorker.STAGE);
        int current = data.getInt(ZipImportWorker.CURRENT, 0);
        int total = data.getInt(ZipImportWorker.TOTAL, 0);
        progress.setIndeterminate(total == 0);
        progress.setMax(Math.max(1, total));
        progress.setProgress(current);
        if (running) {
            if ("extract".equals(stage)) {
                status.setText(getString(R.string.zip_extracting, current, total));
            } else if ("validate".equals(stage)) {
                status.setText(getString(R.string.zip_validating, current, total,
                        data.getInt(ZipImportWorker.IMPORTED, 0)));
            } else if ("commit".equals(stage)) {
                status.setText(R.string.zip_committing);
            } else {
                status.setText(R.string.zip_copying);
            }
        } else {
            if (info.getState() == WorkInfo.State.SUCCEEDED) {
                status.setText(getString(R.string.zip_done, info.getOutputData().getInt(ZipImportWorker.IMPORTED, 0)));
            } else if (info.getState() == WorkInfo.State.CANCELLED) {
                status.setText(R.string.zip_cancelled);
            } else {
                status.setText(info.getOutputData().getString(ZipImportWorker.ERROR));
            }
            String identity = info.getId().toString() + info.getState();
            if (!identity.equals(lastWorkId)) {
                lastWorkId = identity;
                reload();
                if (info.getState() != WorkInfo.State.CANCELLED) {
                    showReport();
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlaybackPosition(PlaybackPositionEvent event) {
        if (selected == null || adapter == null) {
            return;
        }
        long currentId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        for (FeedItem item : items.values()) {
            if (item.getMedia() != null && item.getMedia().getId() == currentId) {
                int duration = Math.max(1, item.getMedia().getDuration());
                int before = (int) (item.getMedia().getPosition() * 100L / duration);
                item.getMedia().setPosition(event.getPosition());
                int after = (int) (event.getPosition() * 100L / duration);
                if (before != after) {
                    adapter.notifyDataSetChanged();
                }
                break;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEpisodeChanged(FeedItemEvent event) {
        if (selected == null) {
            return;
        }
        for (FeedItem item : event.items) {
            if (item.getFeedId() == selected.feedId()) {
                reload();
                break;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        Context context = requireContext().getApplicationContext();
        LinearLayout currentRoot = root;
        long targetId = selectedId;
        executor.execute(() -> {
            try {
                List<SeriesStore.Series> loaded = SeriesStore.list(context);
                Map<String, FeedItem> loadedItems = new HashMap<>();
                if (targetId != 0) {
                    Feed feed = DBReader.getFeed(targetId, false, 0, Integer.MAX_VALUE);
                    if (feed != null) {
                        for (FeedItem item : feed.getItems()) {
                            loadedItems.put(item.getItemIdentifier(), item);
                        }
                    }
                }
                currentRoot.post(() -> {
                    if (root != currentRoot || getView() != currentRoot) {
                        return;
                    }
                    seriesList.clear();
                    seriesList.addAll(loaded);
                    items.clear();
                    items.putAll(loadedItems);
                    selected = null;
                    for (SeriesStore.Series series : loaded) {
                        if (series.feedId() == selectedId) {
                            selected = series;
                            break;
                        }
                    }
                    render();
                });
            } catch (Exception error) {
                currentRoot.post(() -> {
                    if (root == currentRoot && isAdded()) {
                        status.setText(getString(R.string.zip_load_failed, error.getMessage()));
                    }
                });
            }
        });
    }

    private void render() {
        boolean episodeView = selected != null;
        backButton.setVisibility(episodeView ? View.VISIBLE : View.GONE);
        description.setVisibility(episodeView ? View.VISIBLE : View.GONE);
        categories.setVisibility(episodeView ? View.VISIBLE : View.GONE);
        search.setVisibility(episodeView ? View.VISIBLE : View.GONE);
        if (selected != null) {
            description.setText(selected.title() + "\n" + selected.description());
            List<String> names = new ArrayList<>();
            names.add(getString(R.string.zip_all_categories));
            names.addAll(selected.categories());
            categories.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, names));
            int position = selected.categories().indexOf(category);
            categories.setSelection(position < 0 ? 0 : position + 1);
        }
        filter();
    }

    private void filter() {
        displayed.clear();
        if (selected != null) {
            for (SeriesStore.EpisodeData episode : selected.episodes()) {
                if ((category.isEmpty() || category.equals(episode.category()))
                        && (episode.number() + " " + episode.title()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                    displayed.add(episode);
                }
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            boolean isEmpty = selected == null ? seriesList.isEmpty() : displayed.isEmpty();
            empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            empty.setText(selected == null ? R.string.zip_empty : R.string.zip_no_results);
        }
    }

    private void showEpisode(SeriesStore.EpisodeData episode) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(episode.number() + " · " + episode.title())
                .setMessage(R.string.zip_queue_note)
                .setPositiveButton(R.string.zip_play_order, (dialog, which) -> play(episode))
                .setNeutralButton(R.string.zip_transcript, (dialog, which) ->
                        ((MainActivity) requireActivity()).loadChildFragment(
                                SeriesTranscriptFragment.newInstance(episode.title(), episode.transcript())))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void play(SeriesStore.EpisodeData episode) {
        SeriesStore.Series series = selected;
        Context context = requireContext().getApplicationContext();
        LinearLayout currentRoot = root;
        executor.execute(() -> {
            try {
                FeedItem first = SeriesPlayback.prepare(context, series, episode.key());
                currentRoot.post(() -> {
                    if (root == currentRoot && isAdded()) {
                        new PlaybackServiceStarter(requireContext(), first.getMedia())
                                .callEvenIfRunning(true).start();
                    }
                });
            } catch (Exception error) {
                currentRoot.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), getString(R.string.zip_failed, error.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showReport() {
        Context context = requireContext().getApplicationContext();
        LinearLayout currentRoot = root;
        executor.execute(() -> {
            String message;
            String sourceUri = "";
            try {
                JSONObject report = new JSONObject(SeriesStore.readText(new File(SeriesStore.directory(context),
                        "last-report.json")));
                JSONArray issues = report.getJSONArray("issues");
                StringBuilder detail = new StringBuilder();
                for (int i = 0; i < issues.length(); i++) {
                    detail.append(issues.getString(i)).append('\n');
                }
                int imported = report.optInt("imported");
                sourceUri = imported > 0 ? report.optString("sourceUri") : "";
                message = context.getString(R.string.zip_report_summary, imported, report.optInt("retained"),
                        report.optInt("categories"), formatDuration(context, report.optLong("durationMs")),
                        report.optLong("elapsedMs") / 1000.0, issues.length() == 0
                                ? context.getString(R.string.zip_report_clean) : detail.toString());
            } catch (Exception error) {
                message = context.getString(R.string.zip_no_report);
            }
            String loaded = message;
            String deletableSource = sourceUri;
            currentRoot.post(() -> {
                if (root != currentRoot || !isAdded()) {
                    return;
                }
                TextView text = new TextView(requireContext());
                text.setText(loaded);
                text.setTextIsSelectable(true);
                text.setPadding(dp(20), dp(12), dp(20), dp(12));
                ScrollView scroll = new ScrollView(requireContext());
                scroll.addView(text);
                MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.zip_report).setView(scroll).setPositiveButton(android.R.string.ok, null);
                if (!deletableSource.isEmpty() && !running) {
                    dialog.setNeutralButton(R.string.zip_delete_source,
                            (ignored, which) -> confirmDeleteSource(deletableSource));
                }
                dialog.show();
            });
        });
    }

    private void confirmDeleteSource(String source) {
        new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.zip_delete_prompt)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.zip_delete_source, (dialog, which) -> {
                    Context context = requireContext().getApplicationContext();
                    executor.execute(() -> {
                        boolean deleted;
                        try {
                            deleted = DocumentsContract.deleteDocument(context.getContentResolver(), Uri.parse(source));
                        } catch (Exception error) {
                            deleted = false;
                        }
                        int message = deleted ? R.string.zip_deleted : R.string.zip_delete_failed;
                        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
                    });
                }).show();
    }

    private Button button(int label) {
        Button button = new Button(requireContext());
        button.setText(label);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static String formatDuration(Context context, long milliseconds) {
        return context.getString(R.string.zip_duration, milliseconds / 60000, milliseconds / 1000 % 60);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        state.putLong("selected", selectedId);
        state.putString("category", category);
        state.putString("query", query);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onDestroyView() {
        root = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private class LibraryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return selected == null ? seriesList.size() : displayed.size();
        }

        @Override
        public Object getItem(int position) {
            return selected == null ? seriesList.get(position) : displayed.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            ImageView cover = new ImageView(requireContext());
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(cover, new LinearLayout.LayoutParams(dp(72), dp(72)));
            LinearLayout labels = new LinearLayout(requireContext());
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12), 0, 0, 0);
            row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView title = new TextView(requireContext());
            title.setTextSize(18);
            labels.addView(title);
            TextView summary = new TextView(requireContext());
            labels.addView(summary);
            String image = "";
            if (selected == null) {
                SeriesStore.Series series = seriesList.get(position);
                title.setText(series.title());
                long duration = 0;
                for (SeriesStore.EpisodeData episode : series.episodes()) {
                    duration += episode.durationMs();
                    if (image.isEmpty() && !episode.cover().isEmpty()) {
                        image = episode.cover();
                    }
                }
                summary.setText(getString(R.string.zip_series_summary, series.episodes().size(),
                        series.categories().size(), formatDuration(requireContext(), duration)));
            } else {
                SeriesStore.EpisodeData episode = displayed.get(position);
                FeedItem item = items.get(episode.key());
                int percent = item == null || item.getMedia() == null ? 0 : item.isPlayed() ? 100
                        : (int) Math.min(100L, item.getMedia().getPosition() * 100L / Math.max(1, episode.durationMs()));
                title.setText(episode.number() + " · " + episode.title());
                summary.setText(getString(R.string.zip_episode_summary, episode.category(),
                        formatDuration(requireContext(), episode.durationMs()), percent));
                ProgressBar played = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
                played.setMax(100);
                played.setProgress(percent);
                labels.addView(played);
                image = episode.cover();
            }
            if (!image.isEmpty()) {
                Glide.with(SeriesLibraryFragment.this).load(new File(image)).into(cover);
            }
            return row;
        }
    }
}
