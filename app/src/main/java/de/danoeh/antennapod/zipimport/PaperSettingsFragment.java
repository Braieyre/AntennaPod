package de.danoeh.antennapod.zipimport;

import android.content.Intent;
import android.util.TypedValue;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.screen.home.HomeFragment;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import de.danoeh.antennapod.ui.screen.subscriptions.SubscriptionFragment;

public class PaperSettingsFragment extends Fragment {
    public static final String TAG = "PaperSettings";

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        LinearLayout root = PaperUi.column(requireContext());
        PaperUi.toolbar(this, root, R.string.paper_settings, false);
        LinearLayout content = PaperUi.column(requireContext());
        PaperUi.inset(content);
        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        section(content, R.string.paper_listening);
        action(content, R.string.paper_playback_settings, () -> preferences(R.xml.preferences_playback));
        action(content, R.string.paper_appearance, () -> preferences(R.xml.preferences_user_interface));
        section(content, R.string.paper_local_content);
        action(content, R.string.paper_import_history, () -> open(new ImportHistoryFragment()));
        action(content, R.string.paper_rss, () -> open(new SubscriptionFragment()));
        action(content, R.string.paper_rss_more, () -> open(new HomeFragment()));
        section(content, R.string.paper_application);
        action(content, R.string.paper_about, () -> startActivity(new Intent(requireContext(), PreferenceActivity.class)
                .putExtra(PreferenceActivity.OPEN_ABOUT, true)));
        action(content, R.string.paper_advanced, () -> startActivity(new Intent(requireContext(), PreferenceActivity.class)));
        return root;
    }

    private void open(Fragment fragment) {
        ((MainActivity) requireActivity()).loadChildFragment(fragment);
    }

    private void preferences(int screen) {
        startActivity(new Intent(requireContext(), PreferenceActivity.class).putExtra(PreferenceActivity.OPEN_SCREEN, screen));
    }

    private void section(LinearLayout content, int label) {
        TextView title = PaperUi.text(requireContext(), getString(label), 13, true);
        title.setPadding(0, PaperUi.dp(requireContext(), 22), 0, PaperUi.dp(requireContext(), 9));
        content.addView(title);
    }

    private void action(LinearLayout content, int label, Runnable action) {
        TextView row = PaperUi.text(requireContext(), getString(label), 17, false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(PaperUi.dp(requireContext(), 58));
        row.setPadding(PaperUi.dp(requireContext(), 4), PaperUi.dp(requireContext(), 12),
                PaperUi.dp(requireContext(), 4), PaperUi.dp(requireContext(), 12));
        TypedValue ripple = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setBackgroundResource(ripple.resourceId);
        row.setOnClickListener(view -> action.run());
        content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
