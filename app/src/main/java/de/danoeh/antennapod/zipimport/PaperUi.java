package de.danoeh.antennapod.zipimport;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import java.io.File;

public final class PaperUi {
    private PaperUi() { }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int color(Context context, int attribute) {
        return ThemeUtils.getColorFromAttr(context, attribute);
    }

    public static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackgroundColor(color(context, android.R.attr.colorBackground));
        return view;
    }

    public static TextView text(Context context, CharSequence value, float size, boolean secondary) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(context, secondary ? android.R.attr.textColorSecondary : android.R.attr.textColorPrimary));
        view.setLineSpacing(dp(context, 3), 1.1f);
        return view;
    }

    public static MaterialToolbar toolbar(Fragment fragment, LinearLayout root, int title, boolean back) {
        Context context = fragment.requireContext();
        MaterialToolbar toolbar = new MaterialToolbar(context);
        toolbar.setTitle(title);
        toolbar.setTitleTextColor(color(context, android.R.attr.textColorPrimary));
        toolbar.setBackgroundColor(color(context, android.R.attr.colorBackground));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 64)));
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()).top;
            view.setPadding(dp(context, 8), top, dp(context, 8), 0);
            view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 64) + top));
            return insets;
        });
        ((MainActivity) fragment.requireActivity()).setupToolbarToggle(toolbar, back);
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            if (toolbar.getChildAt(i) instanceof TextView text) {
                text.setTextSize(back ? 20 : 28);
                text.setTypeface(Typeface.create("serif", Typeface.BOLD));
            }
        }
        return toolbar;
    }

    public static MaterialButton button(Context context, int label, boolean primary) {
        MaterialButton button = new MaterialButton(context);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setCornerRadius(dp(context, 25));
        button.setMinHeight(dp(context, 48));
        button.setInsetTop(dp(context, 3));
        button.setInsetBottom(dp(context, 3));
        int background = color(context, primary ? R.attr.colorPrimary
                : R.attr.colorSecondaryContainer);
        int foreground = color(context, primary ? R.attr.colorOnPrimary
                : android.R.attr.textColorPrimary);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(foreground);
        button.setIconTint(ColorStateList.valueOf(foreground));
        button.setElevation(0);
        return button;
    }

    public static MaterialButton play(Context context) {
        MaterialButton button = button(context, R.string.paper_play, true);
        button.setText("");
        button.setIconResource(R.drawable.ic_play_24dp);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setContentDescription(context.getString(R.string.paper_play));
        button.setCornerRadius(dp(context, 28));
        button.setPadding(dp(context, 13), 0, dp(context, 13), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)));
        return button;
    }

    public static ImageView cover(Fragment fragment, String path, int size) {
        Context context = fragment.requireContext();
        ImageView image = new ImageView(context);
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(context, size), dp(context, size)));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(shape(context, color(context, R.attr.colorSecondaryContainer), 12));
        image.setClipToOutline(true);
        if (path != null && !path.isEmpty()) {
            Glide.with(fragment).load(new File(path)).into(image);
        } else {
            image.setImageResource(R.drawable.kitchen_mark_foreground);
        }
        return image;
    }

    public static GradientDrawable shape(Context context, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 14));
        row.setMinimumHeight(dp(context, 76));
        return row;
    }

    public static void inset(LinearLayout content) {
        int horizontal = dp(content.getContext(), 22);
        content.setPadding(horizontal, dp(content.getContext(), 12), horizontal, dp(content.getContext(), 22));
    }

    public static void completed(View view) {
        boolean enabled = Build.VERSION.SDK_INT >= 26 ? ValueAnimator.areAnimatorsEnabled()
                : Settings.Global.getFloat(view.getContext().getContentResolver(),
                        Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
        if (enabled) {
            view.setAlpha(0);
            view.setTranslationY(dp(view.getContext(), 8));
            view.animate().alpha(1).translationY(0).setDuration(220).start();
        }
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }
}
