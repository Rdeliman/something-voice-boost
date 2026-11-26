package app.something.voiceboost;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the booster after a reboot when it was left switched on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && Prefs.enabled(context)) {
            try {
                context.startForegroundService(new Intent(context, BoostService.class));
            } catch (RuntimeException e) {
                // The system refused a start from boot; opening the app starts it instead.
            }
        }
    }
}
