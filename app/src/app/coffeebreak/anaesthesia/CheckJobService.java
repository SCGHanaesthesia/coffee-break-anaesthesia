package app.coffeebreak.anaesthesia;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Periodic background check: downloads the feed and posts a notification when a new issue lands. */
public class CheckJobService extends JobService {

    static final int JOB_ID = 4201;
    private static final long INTERVAL_MS = 3L * 60 * 60 * 1000;   // every ~3 hours
    private static final long FLEX_MS = 60L * 60 * 1000;           // within the last hour of each window
    private static final long STALE_MS = 9L * 24 * 60 * 60 * 1000; // no new issue for 9 days

    static void ensureScheduled(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, CheckJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(INTERVAL_MS, FLEX_MS)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runCheck(getApplicationContext());
                } finally {
                    jobFinished(params, false);
                }
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    /** Shared by the background job and the app's own refresh. Posts notifications as needed. */
    static JSONObject runCheck(Context c) {
        JSONObject result = FeedStore.sync(c);
        SharedPreferences p = FeedStore.prefs(c);
        JSONObject latest = FeedStore.latestIssue(c);
        if (latest == null) return result;
        String latestId = latest.optString("id");
        String notified = p.getString("notified_id", "");
        if (result.optBoolean("ok") && latestId.compareTo(notified) > 0) {
            p.edit().putString("notified_id", latestId).apply();
            if (!MainActivity.isInForeground()) {
                Notifier.notifyIssue(c, latest);
            }
        }
        // If nothing new has arrived for 9 days, flag it once per missing week.
        try {
            String pub = latest.optString("published", latest.optString("id"));
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(pub.substring(0, 10));
            long age = System.currentTimeMillis() - d.getTime();
            if (age > STALE_MS && !latestId.equals(p.getString("missing_warned_for", ""))) {
                p.edit().putString("missing_warned_for", latestId).apply();
                Notifier.notifyMissing(c, pub.substring(0, 10));
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
