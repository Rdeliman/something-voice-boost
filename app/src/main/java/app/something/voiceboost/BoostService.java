package app.something.voiceboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds gain to other apps' voice-mode audio (voice chats and internet calls).
 *
 * Android lets an effect be attached to another app's audio session by its id. The ids of what is playing are read from
 * `dumpsys audio`, which needs the DUMP permission (granted once over adb). Carrier calls never pass through an app
 * audio session, so they are out of reach.
 */
public class BoostService extends Service {
    static final String ACTION_REFRESH = "app.something.voiceboost.REFRESH";
    private static final String TAG = "VoiceBoost";
    private static final String CHANNEL = "boost";
    private static final Pattern SESSION = Pattern.compile("sessionId:(\\d+)");
    private static final long RECHECK_MS = 4000;

    /** What the last scan saw, for the app screen. */
    static volatile String status = "Not running";

    private final Map<Integer, LoudnessEnhancer> effects = new HashMap<>();
    private HandlerThread thread;
    private Handler handler;
    private AudioManager audio;

    private final Runnable scan = this::scan;

    private final AudioManager.AudioPlaybackCallback playbackCallback = new AudioManager.AudioPlaybackCallback() {
        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            scanSoon(150);
        }
    };

    private final AudioManager.OnModeChangedListener modeListener = mode -> {
        if (mode == AudioManager.MODE_IN_CALL) callBoost();
        scanSoon(300);
    };

    /**
     * Carrier calls: asks the Qualcomm audio layer for its call "volume boost" tuning. An experiment: it only does
     * something when the phone maker shipped that tuning, and the log line shows what the audio layer answered.
     */
    private void callBoost() {
        try {
            String before = audio.getParameters("volume_boost");
            audio.setParameters("volume_boost=on");
            Log.i(TAG, "call boost asked; before=[" + before + "] after=[" + audio.getParameters("volume_boost") + "]");
        } catch (RuntimeException e) {
            Log.w(TAG, "call boost failed: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("boost");
        thread.start();
        handler = new Handler(thread.getLooper());
        audio = getSystemService(AudioManager.class);
        audio.registerAudioPlaybackCallback(playbackCallback, handler);
        audio.addOnModeChangedListener(handler::post, modeListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        scanSoon(0);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        audio.unregisterAudioPlaybackCallback(playbackCallback);
        audio.removeOnModeChangedListener(modeListener);
        handler.post(() -> {
            releaseAll();
            thread.quitSafely();
        });
        status = "Not running";
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void scanSoon(long delayMs) {
        handler.removeCallbacks(scan);
        handler.postDelayed(scan, delayMs);
    }

    /** Reads what is playing, boosts every started voice-mode session, and drops the ones that ended. */
    private void scan() {
        Set<Integer> live;
        try {
            live = voiceSessions();
        } catch (Exception e) {
            status = "Cannot read audio sessions: " + e.getMessage();
            Log.w(TAG, status);
            return;
        }
        int gainMb = Prefs.gainDb(this) * 100;
        for (Iterator<Map.Entry<Integer, LoudnessEnhancer>> it = effects.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, LoudnessEnhancer> e = it.next();
            if (!live.contains(e.getKey())) {
                release(e.getValue());
                it.remove();
            }
        }
        String error = null;
        for (int session : live) {
            try {
                LoudnessEnhancer fx = effects.get(session);
                if (fx == null) {
                    fx = new LoudnessEnhancer(session);
                    effects.put(session, fx);
                    Log.i(TAG, "attached to session " + session);
                }
                fx.setTargetGain(gainMb);
                fx.setEnabled(true);
            } catch (RuntimeException e) {
                error = "Could not boost session " + session + ": " + e.getMessage();
                Log.w(TAG, error);
                LoudnessEnhancer bad = effects.remove(session);
                if (bad != null) release(bad);
            }
        }
        if (error != null) {
            status = error;
        } else if (effects.isEmpty()) {
            status = "Waiting for a voice chat";
        } else {
            status = "Boosting " + effects.size() + " voice stream" + (effects.size() == 1 ? "" : "s")
                    + " by +" + Prefs.gainDb(this) + " dB";
        }
        // While a voice chat is up, look again: apps open new streams mid-chat without a callback every time.
        if (!live.isEmpty() || audio.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
            handler.removeCallbacks(scan);
            handler.postDelayed(scan, RECHECK_MS);
        }
    }

    /** Session ids of other apps' voice-mode players that are playing now, from `dumpsys audio`. */
    private Set<Integer> voiceSessions() throws Exception {
        Set<Integer> out = new HashSet<>();
        Process p = new ProcessBuilder("dumpsys", "audio").redirectErrorStream(true).start();
        boolean sawPlayers = false;
        String denied = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("Permission Denial")) denied = line.trim();
                if (!line.contains("AudioPlaybackConfiguration")) continue;
                sawPlayers = true;
                if (!line.contains("usage=USAGE_VOICE_COMMUNICATION") || !(line.contains("state:started") || line.contains("state:paused") || line.contains("state:stopped"))) continue;
                Matcher m = SESSION.matcher(line);
                if (m.find()) {
                    int id = Integer.parseInt(m.group(1));
                    if (id > 0) out.add(id);
                }
            }
        } finally {
            p.destroy();
        }
        if (denied != null) throw new IllegalStateException("permission not granted yet");
        if (!sawPlayers) throw new IllegalStateException("no player list in the audio report");
        return out;
    }

    private void releaseAll() {
        for (LoudnessEnhancer fx : effects.values()) release(fx);
        effects.clear();
    }

    private static void release(LoudnessEnhancer fx) {
        try {
            fx.release();
        } catch (RuntimeException ignored) {
            // The session is already gone.
        }
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Something", NotificationManager.IMPORTANCE_MIN));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Something is on")
                .setContentText("Voice chats on the speaker play louder")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }
}
