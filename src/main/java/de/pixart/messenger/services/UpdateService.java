package de.pixart.messenger.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import de.pixart.messenger.BuildConfig;
import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.ui.UpdaterActivity;

import static de.pixart.messenger.services.NotificationService.UPDATE_NOTIFICATION_ID;

public class UpdateService extends AsyncTask<String, Object, UpdateService.Wrapper> {
    public UpdateService (){
    }

    private Context context;
    private boolean playstore;

    public UpdateService(Context context, boolean PlayStore) {
        this.context = context;
        this.playstore = PlayStore;
    }

    public class Wrapper
    {
        public boolean UpdateAvailable = false;
        public boolean NoUpdate = false;
        public boolean isError = false;
    }

    @Override
    protected Wrapper doInBackground(String... params) {
        String jsonString = "";
        boolean UpdateAvailable = false;
        boolean showNoUpdateToast = false;
        boolean isError = false;

        if (params[0].equals("true")) {
            showNoUpdateToast = true;
        }

        HttpsURLConnection connection = null;

        try  {
            URL url = new URL(Config.UPDATE_URL);
            connection = (HttpsURLConnection)url.openConnection();
            connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
            connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
            connection.setRequestProperty("User-Agent", context.getString(R.string.app_name));
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString += line;
            }

        } catch (Exception e) {
            e.printStackTrace();
            isError = true;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        try {
            JSONObject json = new JSONObject(jsonString);
            if (json.getBoolean("success") && json.has("latestVersion") && json.has("appURI") && json.has("filesize")) {
                String version = json.getString("latestVersion");
                String ownVersion = BuildConfig.VERSION_NAME;
                String url = json.getString("appURI");
                String filesize = json.getString("filesize");
                String changelog = "";
                if (json.has("changelog")) {
                    changelog = json.getString("changelog");
                }
                if (checkVersion(version, ownVersion) >= 1) {
                    Log.d(Config.LOGTAG, "AppUpdater: Version " + ownVersion + " should be updated to " + version);
                    UpdateAvailable = true;
                    showNotification(url, changelog, version, filesize, playstore);
                } else {
                    Log.d(Config.LOGTAG, "AppUpdater: Version " + ownVersion + " is up to date");
                    UpdateAvailable = false;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Wrapper w = new Wrapper();
        w.isError = isError;
        w.UpdateAvailable = UpdateAvailable;
        w.NoUpdate = showNoUpdateToast;
        return w;
    }

    @Override
    protected void onPostExecute(Wrapper w) {
        super.onPostExecute(w);
        if (w.isError) {
            showToastMessage(true, true);
            return;
        }
        if (!w.UpdateAvailable) {
            showToastMessage(w.NoUpdate, false);
        }
    }

    private void showToastMessage(boolean show, final boolean error) {
        if (!show) {
            return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                String ToastMessage = "";
                if (error) {
                    ToastMessage = context.getString(R.string.failed);
                } else {
                    ToastMessage = context.getString(R.string.no_update_available);
                }
                Toast.makeText(context, ToastMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showNotification(String url, String changelog, String version, String filesize, boolean playstore) {
        Intent intent = new Intent(context, UpdaterActivity.class);
        intent.putExtra("update", "PixArtMessenger_UpdateService");
        intent.putExtra("url", url);
        intent.putExtra("changelog", changelog);
        intent.putExtra("playstore", playstore);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        mBuilder.setContentTitle(context.getString(R.string.update_service));
        mBuilder.setContentText(String.format(context.getString(R.string.update_available), version, filesize));
        mBuilder.setSmallIcon(R.drawable.ic_update_notification);
        mBuilder.setContentIntent(pi);
        Notification notification = mBuilder.build();
        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification);
    }

    private int checkVersion(String remoteVersion, String installedVersion) {
        // Use this instead of String.compareTo() for a non-lexicographical
        // comparison that works for version strings. e.g. "1.10".compareTo("1.6").
        //
        // @param str1 a string of ordinal numbers separated by decimal points.
        // @param str2 a string of ordinal numbers separated by decimal points.
        // @return The result is a negative integer if str1 is _numerically_ less than str2.
        // The result is a positive integer if str1 is _numerically_ greater than str2.
        // The result is zero if the strings are _numerically_ equal.
        // @note It does not work if "1.10" is supposed to be equal to "1.10.0".

        String[] remote = null;
        String[] installed = null;
        String[] remoteV = null;
        String[] installedV = null;
        try {
            installedV = installedVersion.split(" ");
            Log.d(Config.LOGTAG, "AppUpdater: Version installed: " + installedV[0]);
            installed = installedV[0].split("\\.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            remoteV = remoteVersion.split(" ");
            if (installedV != null && installedV.length > 1) {
                if (installedV[1] != null) {
                    remoteV[0] = remoteV[0] + ".1";
                }
            }
            Log.d(Config.LOGTAG, "AppUpdater: Version on server: " + remoteV[0]);
            remote = remoteV[0].split("\\.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        int i = 0;
        // set index to first non-equal ordinal or length of shortest localVersion string
        if (remote != null && installed != null) {
            while (i < remote.length && i < installed.length && remote[i].equals(installed[i])) {
                i++;
            }
            // compare first non-equal ordinal number
            if (i < remote.length && i < installed.length) {
                int diff = Integer.valueOf(remote[i]).compareTo(Integer.valueOf(installed[i]));
                return Integer.signum(diff);
            }
            // the strings are equal or one string is a substring of the other
            // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
            return Integer.signum(remote.length - installed.length);
        }
        return 0;
    }
}