package de.danoeh.antennapod.core.service.download;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.PodDBAdapter;
import de.danoeh.antennapod.core.util.gui.NotificationUtils;

public class NewEpisodesNotification {
    static final String GROUP_KEY = "de.danoeh.antennapod.EPISODES";

    private final int lastEpisodeCount;

    public NewEpisodesNotification(Long feedId) {
        lastEpisodeCount = getNewEpisodeCount(feedId);
    }

    public void showIfNeeded(Context context, Feed feed) {
        long feedId = feed.getId();
        int newEpisodes = getNewEpisodeCount(feedId) - lastEpisodeCount;

        if (newEpisodes > 0) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            showNotification(newEpisodes, feed, context, notificationManager);
        }
    }

    private static void showNotification(int newEpisodes, Feed feed, Context context,
                                         NotificationManagerCompat notificationManager) {
        Resources res = context.getResources();
        String text = res.getQuantityString(
                R.plurals.new_episode_notification_message, newEpisodes, newEpisodes, feed.getTitle()
        );
        String title = res.getQuantityString(R.plurals.new_episode_notification_title, newEpisodes);

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context, "de.danoeh.antennapod.activity.MainActivity"));

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("fragment_feed_id", feed.getId());
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        Notification notification = new NotificationCompat.Builder(
                context, NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS, feed.hashCode(), notification);
    }

    private static int getNewEpisodeCount(long feedId) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        int episodeCount = adapter.getFeedCounters(UserPreferences.FEED_COUNTER_SHOW_NEW, feedId)
                .get(feedId);
        adapter.close();

        return episodeCount;
    }
}
