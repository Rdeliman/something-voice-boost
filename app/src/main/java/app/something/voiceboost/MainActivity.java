package app.something.voiceboost;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** One screen: an on/off switch, a gain slider, and what the booster is doing now. */
public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView gainLabel;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            statusView.setText(statusText());
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        // Keep the content clear of the status and navigation bars, and off the screen edges.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            return insets;
        });


        TextView about = new TextView(this);
        about.setText("Because the Nothing speaker sounded like nothing. Makes voice chats and internet calls louder"
                + " on the speaker.\n\nRegular phone calls get louder only on Google Fi while you are on Wi-Fi."
                + " Calls on the cell network cannot be boosted by any app.");
        about.setPadding(0, pad / 2, 0, pad);
        root.addView(about);

        Switch onOff = new Switch(this);
        onOff.setText("Boost voice chats");
        onOff.setTextSize(18);
        onOff.setChecked(Prefs.enabled(this));
        onOff.setOnCheckedChangeListener((v, on) -> {
            Prefs.setEnabled(this, on);
            applyService();
        });
        root.addView(onOff);

        gainLabel = new TextView(this);
        gainLabel.setTextSize(18);
        gainLabel.setPadding(0, pad, 0, 0);
        root.addView(gainLabel);

        SeekBar gain = new SeekBar(this);
        gain.setMax(Prefs.MAX_GAIN_DB);
        gain.setProgress(Prefs.gainDb(this));
        gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                Prefs.setGainDb(MainActivity.this, value);
                showGain();
                if (fromUser) applyService();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(gain, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setPadding(0, pad, 0, 0);
        root.addView(statusView);

        setContentView(root);
        showGain();

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        applyService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    private void showGain() {
        int db = Prefs.gainDb(this);
        gainLabel.setText("Extra loudness: +" + db + " dB" + (db > 15 ? " (may distort)" : ""));
    }

    private String statusText() {
        if (checkSelfPermission(Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            return "Setup needed: the one-time permission has not been granted from the computer yet.";
        }
        return Prefs.enabled(this) ? BoostService.status : "Off";
    }

    /** Starts the booster (or pokes it to re-read the gain), or stops it when switched off. */
    private void applyService() {
        Intent svc = new Intent(this, BoostService.class);
        if (Prefs.enabled(this)) {
            startForegroundService(svc.setAction(BoostService.ACTION_REFRESH));
        } else {
            stopService(svc);
        }
    }
}
