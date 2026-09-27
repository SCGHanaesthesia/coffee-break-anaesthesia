package app.coffeebreak.anaesthesia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

final class Notifier {

    static final String CHANNEL_ISSUES = "new_issue";
    static final String CHANNEL_HEALTH = "feed_health";

    private Notifier() {}

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel issues = new NotificationChannel(CHANNEL_ISSUES, "New issues",
                NotificationManager.IMPORTANCE_DEFAULT);
        issues.setDescription("When this week's Coffee Break arrives");
        nm.createNotificationChannel(issues);
        NotificationChannel health = new NotificationChannel(CHANNEL_HEALTH, "Missing issue alerts",
                NotificationManager.IMPORTANCE_LOW);
        health.setDescription("If a weekly issue doesn't turn up");
        nm.createNotificationChannel(health);
    }

    private static Notification.Builder builder(Context c, String channel) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(c, channel);
        return new Notification.Builder(c);
    }

    private static PendingIntent openIntent(Context c, String issueId, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (issueId != null) i.putExtra("issue_id", issueId);
        return PendingIntent.getActivity(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void notifyIssue(Context c, JSONObject issue) {
        ensureChannels(c);
        String id = issue.optString("id");
        int number = issue.optInt("number", 0);
        String title = issue.optString("title", "This week's issue");
        StringBuilder big = new StringBuilder(title);
        JSONArray tldr = issue.optJSONArray("tldr");
        if (tldr != null && tldr.length() > 0) {
            big.append("\n\n").append(tldr.optString(0));
        }
        Notification n = builder(c, CHANNEL_ISSUES)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFB5652B)
                .setContentTitle(number > 0 ? "Coffee Break #" + number + " is ready" : "Coffee Break is ready")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(big.toString()))
                .setContentIntent(openIntent(c, id, 1))
                .setAutoCancel(true)
                .build();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1001, n);
    }

    static void notifyMissing(Context c, String lastIssueDate) {
        ensureChannels(c);
        Notification n = builder(c, CHANNEL_HEALTH)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFB5652B)
                .setContentTitle("This week's Coffee Break hasn't arrived")
                .setContentText("The last issue was " + lastIssueDate + ". The weekly run may have failed.")
                .setContentIntent(openIntent(c, null, 2))
                .setAutoCancel(true)
                .build();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1002, n);
    }
}
