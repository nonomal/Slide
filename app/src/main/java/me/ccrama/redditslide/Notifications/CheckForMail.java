package me.ccrama.redditslide.Notifications;

/**
 * Created by carlo_000 on 10/13/2015.
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.NotificationCompat;
import android.text.Html;

import net.dean.jraw.models.Message;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.InboxPaginator;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.SubredditPaginator;
import net.dean.jraw.paginators.TimePeriod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.ccrama.redditslide.Activities.CancelSubNotifs;
import me.ccrama.redditslide.Activities.Inbox;
import me.ccrama.redditslide.Activities.ModQueue;
import me.ccrama.redditslide.Activities.OpenContent;
import me.ccrama.redditslide.Adapters.MarkAsReadService;
import me.ccrama.redditslide.Authentication;
import me.ccrama.redditslide.R;
import me.ccrama.redditslide.Reddit;
import me.ccrama.redditslide.Visuals.Palette;
import me.ccrama.redditslide.util.NetworkUtil;

public class CheckForMail extends BroadcastReceiver {

    public static final String MESSAGE_EXTRA = "MESSAGE_FULLNAMES";
    public static final String SUBS_TO_GET   = "SUBREDDIT_NOTIFS";
    private Context c;

    @Override
    public void onReceive(Context context, Intent intent) {
        c = context;
        if (NetworkUtil.isConnected(c)) {
            new AsyncGetMail().execute();
            if (Authentication.mod) new AsyncGetModmail().execute();
            if (!Reddit.appRestart.getString(SUBS_TO_GET, "").isEmpty()) {
                new AsyncGetSubs().execute();
            }
        }
        new NotificationJobScheduler(context).start(context);
    }


    private class AsyncGetMail extends AsyncTask<Void, Void, List<Message>> {

        @Override
        public void onPostExecute(List<Message> messages) {
            Resources res = c.getResources();
            if (messages != null && !messages.isEmpty()) {
                if (Reddit.isPackageInstalled(c, "com.teslacoilsw.notifier")) {
                    try {

                        ContentValues cv = new ContentValues();

                        cv.put("tag", "me.ccrama.redditslide/me.ccrama.redditslide.MainActivity");

                        cv.put("count", messages.size());

                        c.getContentResolver()
                                .insert(Uri.parse(
                                        "content://com.teslacoilsw.notifier/unread_count"), cv);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                //create arraylist of the messages fullName for markasread action
                String[] messageNames = new String[messages.size()];
                int counter = 0;
                for (Message x : messages) {
                    messageNames[counter] = x.getFullName();
                    counter++;
                }

                NotificationManager notificationManager =
                        (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);


                Intent notificationIntent = new Intent(c, Inbox.class);
                notificationIntent.putExtra(Inbox.EXTRA_UNREAD, true);

                notificationIntent.setFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                PendingIntent intent = PendingIntent.getActivity(c, 0, notificationIntent, 0);

                //Intent for mark as read notification action
                Intent readIntent = new Intent(c, MarkAsReadService.class);
                readIntent.putExtra(MESSAGE_EXTRA, messageNames);
                PendingIntent readPI = PendingIntent.getService(c, 2, readIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);


                if (messages.size() == 1) {

                    NotificationCompat.BigTextStyle notiStyle =
                            new NotificationCompat.BigTextStyle();
                    notiStyle.setBigContentTitle(c.getString(R.string.mail_notification_msg,
                            messages.get(0).getAuthor()));
                    notiStyle.bigText(Html.fromHtml(messages.get(0).getBody()));

                    Notification notification =
                            new NotificationCompat.Builder(c).setContentIntent(intent)
                                    .setSmallIcon(R.drawable.notif)
                                    .setTicker(
                                            res.getQuantityString(R.plurals.mail_notification_title,
                                                    1, 1))
                                    .setWhen(System.currentTimeMillis())
                                    .setAutoCancel(true)
                                    .setContentTitle(c.getString(R.string.mail_notification_author,
                                            messages.get(0).getSubject(),
                                            messages.get(0).getAuthor()))
                                    .setContentText(Html.fromHtml(messages.get(0).getBody()))
                                    .setStyle(notiStyle)
                                    .addAction(R.drawable.ic_check_all_black,
                                            c.getString(R.string.mail_mark_read), readPI)
                                    .build();
                    notificationManager.notify(0, notification);
                } else {
                    int amount = messages.size();

                    NotificationCompat.InboxStyle notiStyle = new NotificationCompat.InboxStyle();
                    notiStyle.setBigContentTitle(
                            res.getQuantityString(R.plurals.mail_notification_title, amount,
                                    amount));
                    notiStyle.setSummaryText("");
                    for (Message m : messages) {
                        notiStyle.addLine(
                                c.getString(R.string.mail_notification_msg, m.getAuthor()));
                    }

                    Notification notification =
                            new NotificationCompat.Builder(c).setContentIntent(intent)
                                    .setSmallIcon(R.drawable.notif)
                                    .setTicker(
                                            res.getQuantityString(R.plurals.mail_notification_title,
                                                    amount, amount))
                                    .setWhen(System.currentTimeMillis())
                                    .setAutoCancel(true)
                                    .setContentTitle(
                                            res.getQuantityString(R.plurals.mail_notification_title,
                                                    amount, amount))
                                    .setStyle(notiStyle)
                                    .addAction(R.drawable.ic_check_all_black,
                                            c.getString(R.string.mail_mark_read), readPI)
                                    .build();
                    notificationManager.notify(0, notification);
                }
            }
        }

        @Override
        protected List<Message> doInBackground(Void... params) {
            try {
                if (Authentication.isLoggedIn && Authentication.didOnline) {
                    InboxPaginator unread = new InboxPaginator(Authentication.reddit, "unread");

                    List<Message> messages = new ArrayList<>();
                    if (unread.hasNext()) {
                        messages.addAll(unread.next());
                    }

                    return messages;
                }
            } catch (Exception ignored) {
                ignored.printStackTrace();

            }
            return null;
        }
    }

    private class AsyncGetModmail extends AsyncTask<Void, Void, List<Message>> {

        @Override
        public void onPostExecute(List<Message> messages) {
            Resources res = c.getResources();
            if (messages != null && !messages.isEmpty()) {

                NotificationManager notificationManager =
                        (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

                Intent notificationIntent = new Intent(c, ModQueue.class);

                notificationIntent.setFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                PendingIntent intent = PendingIntent.getActivity(c, 0, notificationIntent, 0);

                if (messages.size() == 1) {

                    NotificationCompat.BigTextStyle notiStyle =
                            new NotificationCompat.BigTextStyle();
                    notiStyle.setBigContentTitle(c.getString(R.string.mod_mail_notification_msg,
                            messages.get(0).getAuthor()));
                    notiStyle.bigText(Html.fromHtml(messages.get(0).getBody()));

                    Notification notification =
                            new NotificationCompat.Builder(c).setContentIntent(intent)
                                    .setSmallIcon(R.drawable.mod)
                                    .setTicker(res.getQuantityString(
                                            R.plurals.mod_mail_notification_title, 1, 1))
                                    .setWhen(System.currentTimeMillis())
                                    .setAutoCancel(true)
                                    .setContentTitle(c.getString(R.string.mail_notification_author,
                                            messages.get(0).getSubject(),
                                            messages.get(0).getAuthor()))
                                    .setContentText(Html.fromHtml(messages.get(0).getBody()))
                                    .setStyle(notiStyle)
                                    .build();
                    notificationManager.notify(1, notification);
                } else {
                    int amount = messages.size();

                    NotificationCompat.InboxStyle notiStyle = new NotificationCompat.InboxStyle();
                    notiStyle.setBigContentTitle(
                            res.getQuantityString(R.plurals.mod_mail_notification_title, amount,
                                    amount));
                    notiStyle.setSummaryText("");
                    for (Message m : messages) {
                        notiStyle.addLine(
                                c.getString(R.string.mod_mail_notification_msg, m.getAuthor()));
                    }

                    Notification notification =
                            new NotificationCompat.Builder(c).setContentIntent(intent)
                                    .setSmallIcon(R.drawable.mod)
                                    .setTicker(res.getQuantityString(
                                            R.plurals.mod_mail_notification_title, amount, amount))
                                    .setWhen(System.currentTimeMillis())
                                    .setAutoCancel(true)
                                    .setContentTitle(res.getQuantityString(
                                            R.plurals.mod_mail_notification_title, amount, amount))
                                    .setStyle(notiStyle)
                                    .build();
                    notificationManager.notify(1, notification);
                }
            }
        }

        @Override
        protected List<Message> doInBackground(Void... params) {
            try {
                if (Authentication.isLoggedIn && Authentication.didOnline) {
                    InboxPaginator unread =
                            new InboxPaginator(Authentication.reddit, "moderator/unread");

                    List<Message> messages = new ArrayList<>();
                    if (unread.hasNext()) {
                        messages.addAll(unread.next());
                    }

                    return messages;
                }
            } catch (Exception ignored) {

                ignored.printStackTrace();
            }
            return null;
        }
    }

    private class AsyncGetSubs extends AsyncTask<Void, Void, List<Submission>> {

        @Override
        public void onPostExecute(List<Submission> messages) {
            if (messages != null) {
                if (!messages.isEmpty()) {
                    NotificationManager notificationManager =
                            (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                    for (Submission s : messages) {
                        Intent readIntent = new Intent(c, OpenContent.class);
                        readIntent.putExtra(OpenContent.EXTRA_URL,
                                "https://reddit.com" + s.getPermalink());
                        PendingIntent readPI = PendingIntent.getService(c, 1, readIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT);


                        Intent cancelIntent = new Intent(c, CancelSubNotifs.class);
                        cancelIntent.putExtra(CancelSubNotifs.EXTRA_SUB, s.getSubredditName());
                        PendingIntent cancelPi = PendingIntent.getService(c, 2, cancelIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT);


                        NotificationCompat.BigTextStyle notiStyle =
                                new NotificationCompat.BigTextStyle();
                        notiStyle.setBigContentTitle("/r/" + s.getSubredditName());

                        notiStyle.bigText(Html.fromHtml(s.getTitle() + c.getString(
                                R.string.submission_properties_seperator_comments))
                                + s.getAuthor());


                        Notification notification =
                                new NotificationCompat.Builder(c).setContentIntent(readPI)
                                        .setSmallIcon(R.drawable.notif)
                                        .setTicker("New post in /r/" + s.getSubredditName())
                                        .setWhen(System.currentTimeMillis())
                                        .setAutoCancel(true)
                                        .setContentTitle("/r/"
                                                + s.getSubredditName()
                                                + c.getString(
                                                R.string.submission_properties_seperator_comments)
                                                + s.getTitle())
                                        .setContentText(Html.fromHtml(s.getTitle() + c.getString(
                                                R.string.submission_properties_seperator_comments))
                                                + s.getAuthor())
                                        .setColor(Palette.getColor(s.getSubredditName()))
                                        .setStyle(notiStyle)
                                        .addAction(R.drawable.close,
                                                "Stop tracking /r/" + s.getSubredditName(),
                                                cancelPi)
                                        .build();
                        notificationManager.notify((int) (s.getCreated().getTime() / 1000),
                                notification);
                    }
                }
                new NotificationJobScheduler(c).start(c);

            }
        }

        HashMap<String, Integer> subThresholds;

        @Override
        protected List<Submission> doInBackground(Void... params) {
            try {
                long lastTime =
                        (System.currentTimeMillis() - (1000 * Reddit.notificationTime * 60));
                ArrayList<Submission> toReturn = new ArrayList<>();
                ArrayList<String> rawSubs =
                        Reddit.stringToArray(Reddit.appRestart.getString(SUBS_TO_GET, ""));
                subThresholds = new HashMap<>();
                for(String s : rawSubs){
                    try {
                        String[] split = s.split(":");
                        subThresholds.put(split[0].toLowerCase(), Integer.valueOf(split[1]));
                    } catch(Exception ignored){

                    }
                }
                if (subThresholds.isEmpty()) {
                    return null;
                }

                String first = "";
                boolean skipFirst = false;
                ArrayList<String> finalSubs = new ArrayList<>();
                for (String s : subThresholds.keySet()) {
                    if (!s.isEmpty() && !skipFirst) {
                        finalSubs.add(s);
                    } else {
                        skipFirst = true;
                        first = s;
                    }
                }
                SubredditPaginator unread = new SubredditPaginator(Authentication.reddit, first,
                        finalSubs.toArray(new String[finalSubs.size()]));
                unread.setSorting(Sorting.NEW);
                unread.setTimePeriod(TimePeriod.HOUR);

                unread.setLimit(30);
                if (unread.hasNext()) {
                    for (Submission subm : unread.next()) {
                        if (subm.getCreated().getTime() > lastTime && subm.getScore() >= subThresholds.get(subm.getSubredditName().toLowerCase())) {
                            toReturn.add(subm);
                        }
                    }
                }
                return toReturn;

            } catch (Exception ignored)

            {
                ignored.printStackTrace();

            }

            return null;
        }
    }

}