package de.danoeh.antennapod.zipimport;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import com.bumptech.glide.Glide;
import java.io.File;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;
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
    private LinearLayout header;
    private LinearLayout continuation;
    private LinearLayout importPanel;
    private MaterialToolbar toolbar;
    private MaterialButton emptyImport;
    private MaterialButton retry;
    private TextView status;
    private TextView description;
    private TextView title;
    private ImageView programmeCover;
    private OnBackPressedCallback programmeBack;
    private TextView empty;
    private ProgressBar progress;
    private ChipGroup categories;
    private EditText search;
    private LibraryAdapter adapter;
    private String category = "";
    private String query = "";
    private boolean running;
    private boolean rebuilding;
    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null
                        || result.getData().getData() == null) {
                    return;
                }
                Intent data = result.getData();
                Context context = requireContext().getApplicationContext();
                String uri = data.getData().toString();
                try {
                    context.getContentResolver().takePersistableUriPermission(data.getData(), data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                } catch (SecurityException error) {
                    showError(error);
                    return;
                }
                executor.execute(() -> {
                    String name = data.getData().getLastPathSegment();
                    try (Cursor cursor = context.getContentResolver().query(data.getData(),
                            new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            name = cursor.getString(0);
                        }
                    } catch (RuntimeException ignored) {
                        name = data.getData().getLastPathSegment();
                    }
                    enqueue(context, uri, name);
                });
            });

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        if (state != null) {
            selectedId = state.getLong("selected");
            category = state.getString("category", "");
            query = state.getString("query", "");
        }
        Context context = requireContext();
        root = PaperUi.column(context);
        toolbar = PaperUi.toolbar(this, root, R.string.paper_programmes, false);
        toolbar.getMenu().add(R.string.paper_import_short).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            chooseArchive();
            return true;
        });
        toolbar.setNavigationOnClickListener(view -> showProgrammes());
        importPanel = PaperUi.column(context);
        PaperUi.inset(importPanel);
        status = PaperUi.text(context, "", 15, false);
        importPanel.addView(status);
        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        importPanel.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                PaperUi.dp(context, 8)));
        LinearLayout importActions = new LinearLayout(context);
        MaterialButton cancel = PaperUi.button(context, R.string.paper_cancel, false);
        cancel.setOnClickListener(view -> WorkManager.getInstance(context).cancelUniqueWork(ZipImportWorker.WORK_NAME));
        retry = PaperUi.button(context, R.string.zip_retry, false);
        retry.setOnClickListener(view -> {
            String uri = ImportHistory.preferences(context).getString("uri", null);
            if (uri != null) {
                executor.execute(() -> enqueue(context, uri, ImportHistory.preferences(context).getString("name", "")));
            }
        });
        importActions.addView(cancel);
        importActions.addView(retry);
        importPanel.addView(importActions);
        importPanel.setVisibility(View.GONE);
        root.addView(importPanel);
        continuation = PaperUi.column(context);
        root.addView(continuation);
        header = PaperUi.column(context);
        PaperUi.inset(header);
        title = PaperUi.text(context, "", 25, false);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        LinearLayout programmeHeading = new LinearLayout(context);
        programmeHeading.setGravity(Gravity.CENTER_VERTICAL);
        programmeCover = PaperUi.cover(this, "", 64);
        programmeHeading.addView(programmeCover);
        title.setMaxLines(2);
        title.setPadding(PaperUi.dp(context, 14), 0, 0, 0);
        programmeHeading.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(programmeHeading);
        description = PaperUi.text(context, "", 14, true);
        description.setMaxLines(2);
        description.setPadding(0, PaperUi.dp(context, 8), 0, 0);
        header.addView(description);
        MaterialButton expand = PaperUi.button(context, R.string.paper_expand, false);
        expand.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        expand.setPadding(PaperUi.dp(context, 4), 0, PaperUi.dp(context, 12), 0);
        expand.setOnClickListener(view -> {
            boolean collapsed = description.getMaxLines() == 2;
            description.setMaxLines(collapsed ? 5 : 2);
            expand.setText(collapsed ? R.string.paper_collapse : R.string.paper_expand);
        });
        header.addView(expand, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                PaperUi.dp(context, 48)));
        HorizontalScrollView categoryScroll = new HorizontalScrollView(context);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categories = new ChipGroup(context);
        categories.setSingleLine(true);
        categories.setSingleSelection(true);
        categories.setSelectionRequired(true);
        categoryScroll.addView(categories);
        header.addView(categoryScroll);
        search = new EditText(context);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint(R.string.paper_search_title);
        search.setText(query);
        search.setPadding(PaperUi.dp(context, 15), PaperUi.dp(context, 10), PaperUi.dp(context, 15), PaperUi.dp(context, 10));
        search.setBackground(PaperUi.shape(context, PaperUi.color(context,
                R.attr.colorSecondaryContainer), 15));
        header.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header);
        empty = PaperUi.text(context, getString(R.string.paper_empty), 18, true);
        empty.setPadding(PaperUi.dp(context, 25), PaperUi.dp(context, 36), PaperUi.dp(context, 25), PaperUi.dp(context, 20));
        root.addView(empty);
        emptyImport = PaperUi.button(context, R.string.paper_import, true);
        emptyImport.setOnClickListener(view -> chooseArchive());
        root.addView(emptyImport);
        ListView list = new ListView(context);
        list.setDividerHeight(0);
        list.setBackgroundColor(PaperUi.color(context, android.R.attr.colorBackground));
        adapter = new LibraryAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                query = text.toString();
                filter();
            }

            @Override
            public void afterTextChanged(Editable text) { }
        });
        categories.setOnCheckedStateChangeListener((group, checked) -> {
            if (rebuilding || checked.isEmpty()) {
                return;
            }
            View chip = group.findViewById(checked.get(0));
            category = String.valueOf(chip.getTag());
            filter();
        });
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(ZipImportWorker.WORK_NAME)
                .observe(getViewLifecycleOwner(), this::updateWork);
        programmeBack = new OnBackPressedCallback(selectedId != 0) {
            @Override
            public void handleOnBackPressed() {
                showProgrammes();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), programmeBack);
        return root;
    }

    private void chooseArchive() {
        if (running) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed",
                        "application/octet-stream"})
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        picker.launch(intent);
    }

    public static synchronized boolean enqueueIfIdle(Context context, String uri, String name) throws Exception {
        WorkManager manager = WorkManager.getInstance(context);
        for (WorkInfo info : manager.getWorkInfosForUniqueWork(ZipImportWorker.WORK_NAME).get()) {
            if (!info.getState().isFinished()) {
                return false;
            }
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ZipImportWorker.class).setInputData(
                new Data.Builder().putString(ZipImportWorker.URI, uri).putString(ZipImportWorker.NAME, name).build()).build();
        ImportHistory.preferences(context).edit().putString("active_work", request.getId().toString())
                .putString("uri", uri).putString("name", name).commit();
        try {
            manager.enqueueUniqueWork(ZipImportWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request).getResult().get();
            return true;
        } catch (Exception error) {
            ImportHistory.preferences(context).edit().remove("active_work").commit();
            throw error;
        }
    }

    private void enqueue(Context context, String uri, String name) {
        try {
            enqueueIfIdle(context, uri, name);
        } catch (Exception error) {
            if (root != null) {
                root.post(() -> showError(error));
            }
        }
    }

    private void updateWork(List<WorkInfo> work) {
        if (root == null || work.isEmpty()) {
            return;
        }
        WorkInfo current = null;
        for (WorkInfo info : work) {
            if (!info.getState().isFinished()) {
                current = info;
                break;
            }
        }
        running = current != null;
        importPanel.setVisibility(running ? View.VISIBLE : View.GONE);
        emptyImport.setEnabled(!running);
        toolbar.getMenu().getItem(0).setEnabled(!running);
        if (current != null) {
            Data data = current.getProgress();
            int count = data.getInt(ZipImportWorker.CURRENT, 0);
            int total = data.getInt(ZipImportWorker.TOTAL, 0);
            progress.setIndeterminate(total <= 0);
            progress.setMax(Math.max(1, total));
            progress.setProgress(count);
            status.setText(total > 0 ? getString(R.string.paper_import_progress, count, total)
                    : getString(R.string.paper_import_reading));
            retry.setVisibility(View.GONE);
            return;
        }
        for (WorkInfo info : work) {
            if (!info.getState().isFinished() || !ImportHistory.consumeCompletion(requireContext(), info.getId().toString())) {
                continue;
            }
            String message = info.getState() == WorkInfo.State.SUCCEEDED
                    ? getString(R.string.zip_done, info.getOutputData().getInt(ZipImportWorker.IMPORTED, 0))
                    : getString(info.getState() == WorkInfo.State.CANCELLED
                            ? R.string.paper_import_cancelled : R.string.paper_import_failed);
            Snackbar result = Snackbar.make(root, message, Snackbar.LENGTH_LONG).setAction(R.string.paper_view,
                    view -> ((MainActivity) requireActivity()).loadChildFragment(new ImportHistoryFragment()));
            result.show();
            if (info.getState() == WorkInfo.State.SUCCEEDED) {
                PaperUi.completed(result.getView());
            }
            reload();
        }
    }

    private void showProgrammes() {
        selectedId = 0;
        selected = null;
        category = "";
        query = "";
        search.setText("");
        render(null);
    }

    private void openProgramme(SeriesStore.Series series) {
        selectedId = series.feedId();
        selected = series;
        category = "";
        query = "";
        search.setText("");
        reload();
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

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlaybackPosition(PlaybackPositionEvent event) {
        long id = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        for (FeedItem item : items.values()) {
            if (item.getMedia() != null && item.getMedia().getId() == id) {
                int old = item.getMedia().getPosition() / 3000;
                item.getMedia().setPosition(event.getPosition());
                if (old != event.getPosition() / 3000) {
                    adapter.notifyDataSetChanged();
                }
                break;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEpisodeChanged(FeedItemEvent event) {
        if (selected != null) {
            reload();
        }
    }

    private void reload() {
        if (!isAdded() || root == null) {
            return;
        }
        Context context = requireContext().getApplicationContext();
        LinearLayout target = root;
        long id = selectedId;
        executor.execute(() -> {
            try {
                List<SeriesStore.Series> loaded = SeriesStore.list(context);
                Map<String, FeedItem> loadedItems = new HashMap<>();
                Feed feed = id == 0 ? null : DBReader.getFeed(id, false, 0, Integer.MAX_VALUE);
                if (feed != null) {
                    for (FeedItem item : feed.getItems()) {
                        loadedItems.put(item.getItemIdentifier(), item);
                    }
                }
                FeedMedia current = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
                target.post(() -> {
                    if (root != target || getView() == null || id != selectedId) {
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
                        }
                    }
                    render(current);
                });
            } catch (Exception error) {
                target.post(() -> showError(error));
            }
        });
    }

    private void render(FeedMedia current) {
        boolean programme = selected != null;
        header.setVisibility(programme ? View.VISIBLE : View.GONE);
        programmeBack.setEnabled(programme);
        toolbar.setTitle(programme ? R.string.paper_programme : R.string.paper_programmes);
        if (programme) {
            toolbar.setNavigationIcon(R.drawable.paper_back);
        } else {
            toolbar.setNavigationIcon(null);
        }
        continuation.removeAllViews();
        if (programme) {
            title.setText(selected.title());
            if (!selected.episodes().isEmpty() && !selected.episodes().get(0).cover().isEmpty()) {
                Glide.with(this).load(new File(selected.episodes().get(0).cover())).into(programmeCover);
            } else {
                programmeCover.setImageResource(R.drawable.kitchen_mark_foreground);
            }
            description.setText(selected.description());
            rebuilding = true;
            categories.removeAllViews();
            List<String> names = new ArrayList<>();
            names.add("");
            names.addAll(selected.categories());
            for (String name : names) {
                Chip chip = new Chip(requireContext());
                chip.setId(View.generateViewId());
                chip.setTag(name);
                chip.setText(name.isEmpty() ? getString(R.string.paper_all) : name);
                chip.setCheckable(true);
                chip.setChipStrokeWidth(0);
                chip.setShapeAppearanceModel(chip.getShapeAppearanceModel().toBuilder()
                        .setAllCornerSizes(PaperUi.dp(requireContext(), 24)).build());
                int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
                chip.setChipBackgroundColor(new ColorStateList(states, new int[]{
                        PaperUi.color(requireContext(), R.attr.colorSecondaryContainer), Color.TRANSPARENT}));
                chip.setTextColor(new ColorStateList(states, new int[]{
                        PaperUi.color(requireContext(), R.attr.paperSecondaryAccent),
                        PaperUi.color(requireContext(), android.R.attr.textColorSecondary)}));
                chip.setCheckedIconVisible(false);
                chip.setChecked(name.equals(category));
                categories.addView(chip);
            }
            rebuilding = false;
        } else if (current != null && current.getItem() != null) {
            for (SeriesStore.Series series : seriesList) {
                if (series.feedId() != current.getItem().getFeedId()) {
                    continue;
                }
                TextView heading = PaperUi.text(requireContext(), getString(R.string.paper_continue), 18, false);
                heading.setPadding(PaperUi.dp(requireContext(), 22), PaperUi.dp(requireContext(), 12), 0, 0);
                continuation.addView(heading);
                LinearLayout row = PaperUi.row(requireContext());
                row.setBackground(PaperUi.shape(requireContext(), PaperUi.color(requireContext(),
                        R.attr.colorSecondaryContainer), 22));
                row.addView(PaperUi.cover(this, current.getItem().getImageUrl(), 64));
                TextView label = PaperUi.text(requireContext(), current.getEpisodeTitle(), 16, false);
                label.setPadding(PaperUi.dp(requireContext(), 12), 0, PaperUi.dp(requireContext(), 8), 0);
                label.setMaxLines(2);
                row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                MaterialButton play = PaperUi.play(requireContext());
                play.setOnClickListener(view -> play(series, current.getItem().getItemIdentifier()));
                row.addView(play);
                LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                parameters.setMargins(PaperUi.dp(requireContext(), 20), PaperUi.dp(requireContext(), 12),
                        PaperUi.dp(requireContext(), 20), PaperUi.dp(requireContext(), 10));
                continuation.addView(row, parameters);
                break;
            }
        }
        filter();
    }

    private void filter() {
        if (adapter == null) {
            return;
        }
        displayed.clear();
        if (selected != null) {
            for (SeriesStore.EpisodeData episode : selected.episodes()) {
                if ((category.isEmpty() || category.equals(episode.category()))
                        && (episode.number() + " " + episode.title()).toLowerCase(Locale.ROOT)
                        .contains(query.toLowerCase(Locale.ROOT))) {
                    displayed.add(episode);
                }
            }
        }
        boolean isEmpty = selected == null ? seriesList.isEmpty() : displayed.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        empty.setText(selected == null ? R.string.paper_empty : R.string.zip_no_results);
        emptyImport.setVisibility(selected == null && seriesList.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void play(SeriesStore.Series series, String key) {
        Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                SeriesPlayback.play(context, series, key);
            } catch (Exception error) {
                if (root != null) {
                    root.post(() -> showError(error));
                }
            }
        });
    }

    private void showError(Exception error) {
        if (root != null && isAdded()) {
            Snackbar.make(root, getString(R.string.zip_failed, error.getMessage()), Snackbar.LENGTH_LONG).show();
        }
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
            Context context = requireContext();
            LinearLayout row = PaperUi.row(context);
            String image;
            String primary;
            String secondary;
            int percent = 0;
            SeriesStore.EpisodeData episode = selected == null ? null : displayed.get(position);
            SeriesStore.Series series = selected == null ? seriesList.get(position) : selected;
            if (episode == null) {
                primary = series.title();
                secondary = getString(R.string.paper_episode_count, series.episodes().size());
                image = series.episodes().isEmpty() ? "" : series.episodes().get(0).cover();
                row.setOnClickListener(view -> openProgramme(series));
            } else {
                primary = episode.number() + " · " + episode.title();
                secondary = episode.category() + " · " + getString(R.string.zip_duration,
                        episode.durationMs() / 60000, episode.durationMs() / 1000 % 60);
                image = episode.cover();
                FeedItem item = items.get(episode.key());
                percent = item == null || item.getMedia() == null ? 0 : item.isPlayed() ? 100
                        : (int) Math.min(100L, item.getMedia().getPosition() * 100L / Math.max(1, episode.durationMs()));
                row.setOnClickListener(view -> ((MainActivity) requireActivity()).loadChildFragment(
                        SeriesEpisodeFragment.newInstance(series.feedId(), episode.key())));
            }
            row.addView(PaperUi.cover(SeriesLibraryFragment.this, image, episode == null ? 72 : 52));
            LinearLayout labels = PaperUi.column(context);
            labels.setPadding(PaperUi.dp(context, 14), 0, PaperUi.dp(context, 10), 0);
            labels.setBackgroundColor(Color.TRANSPARENT);
            TextView heading = PaperUi.text(context, primary, episode == null ? 18 : 16, false);
            heading.setTypeface(Typeface.create(episode == null ? "serif" : "sans-serif", Typeface.BOLD));
            labels.addView(heading);
            labels.addView(PaperUi.text(context, secondary, 13, true));
            if (episode != null) {
                ProgressBar listened = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
                listened.setMax(100);
                listened.setProgress(percent);
                labels.addView(listened, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        PaperUi.dp(context, 4)));
            }
            row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (episode != null) {
                MaterialButton play = PaperUi.play(context);
                play.setOnClickListener(view -> play(series, episode.key()));
                row.addView(play);
            }
            return row;
        }
    }
}
