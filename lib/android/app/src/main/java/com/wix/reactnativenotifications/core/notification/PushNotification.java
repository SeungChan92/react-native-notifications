package com.wix.reactnativenotifications.core.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import com.facebook.react.bridge.ReactContext;
import com.wix.reactnativenotifications.core.AppLaunchHelper;
import com.wix.reactnativenotifications.core.AppLifecycleFacade;
import com.wix.reactnativenotifications.core.AppLifecycleFacade.AppVisibilityListener;
import com.wix.reactnativenotifications.core.AppLifecycleFacadeHolder;
import com.wix.reactnativenotifications.core.InitialNotificationHolder;
import com.wix.reactnativenotifications.core.JsIOHelper;
import com.wix.reactnativenotifications.core.NotificationIntentAdapter;
import com.wix.reactnativenotifications.core.ProxyService;

import static com.wix.reactnativenotifications.Defs.NOTIFICATION_OPENED_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.NOTIFICATION_RECEIVED_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.NOTIFICATION_RECEIVED_BACKGROUND_EVENT_NAME;

import java.lang.Long;
import java.util.Arrays;
import java.util.ArrayList;

public class PushNotification implements IPushNotification {

    final protected Context mContext;
    final protected AppLifecycleFacade mAppLifecycleFacade;
    final protected AppLaunchHelper mAppLaunchHelper;
    final protected JsIOHelper mJsIOHelper;
    final protected PushNotificationProps mNotificationProps;
    final protected AppVisibilityListener mAppVisibilityListener = new AppVisibilityListener() {
        @Override
        public void onAppVisible() {
            mAppLifecycleFacade.removeVisibilityListener(this);
            dispatchImmediately();
        }

        @Override
        public void onAppNotVisible() {
        }
    };

    public static IPushNotification get(Context context, Bundle bundle) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof INotificationsApplication) {
            return ((INotificationsApplication) appContext).getPushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper());
        }
        return new PushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper(), new JsIOHelper());
    }

    protected PushNotification(Context context, Bundle bundle, AppLifecycleFacade appLifecycleFacade, AppLaunchHelper appLaunchHelper, JsIOHelper JsIOHelper) {
        mContext = context;
        mAppLifecycleFacade = appLifecycleFacade;
        mAppLaunchHelper = appLaunchHelper;
        mJsIOHelper = JsIOHelper;
        mNotificationProps = createProps(bundle);
    }

    @Override
    public void onReceived() throws InvalidNotificationException {
        if (!mAppLifecycleFacade.isAppVisible()) {
            // Assume all notifications are properly handled in JS code even
            // when the app is not visible.
            //postNotification(null);
            notifyReceivedBackgroundToJS();
        } else {
            notifyReceivedToJS();
        }
    }

    @Override
    public void onOpened() {
        digestNotification();
    }

    @Override
    public int onPostRequest(Integer notificationId) {
        return postNotification(notificationId);
    }

    @Override
    public PushNotificationProps asProps() {
        return mNotificationProps.copy();
    }

    protected int postNotification(Integer notificationId) {
        final PendingIntent pendingIntent = getCTAPendingIntent();
        final Notification notification = buildNotification(pendingIntent);
        return postNotification(notification, notificationId);
    }

    protected void digestNotification() {
        if (!mAppLifecycleFacade.isReactInitialized()) {
            setAsInitialNotification();
            launchOrResumeApp();
            return;
        }

        final ReactContext reactContext = mAppLifecycleFacade.getRunningReactContext();
        if (reactContext.getCurrentActivity() == null) {
            setAsInitialNotification();
        }

        if (mAppLifecycleFacade.isAppVisible()) {
            dispatchImmediately();
        } else if (mAppLifecycleFacade.isAppDestroyed()) {
            launchOrResumeApp();
        } else {
            dispatchUponVisibility();
        }
    }

    protected PushNotificationProps createProps(Bundle bundle) {
        return new PushNotificationProps(bundle);
    }

    protected void setAsInitialNotification() {
        InitialNotificationHolder.getInstance().set(mNotificationProps);
    }

    protected void dispatchImmediately() {
        notifyOpenedToJS();
    }

    protected void dispatchUponVisibility() {
        mAppLifecycleFacade.addVisibilityListener(getIntermediateAppVisibilityListener());

        // Make the app visible so that we'll dispatch the notification opening when visibility changes to 'true' (see
        // above listener registration).
        launchOrResumeApp();
    }

    protected AppVisibilityListener getIntermediateAppVisibilityListener() {
        return mAppVisibilityListener;
    }

    protected PendingIntent getCTAPendingIntent() {
        final Intent cta = new Intent(mContext, ProxyService.class);
        return NotificationIntentAdapter.createPendingNotificationIntent(mContext, cta, mNotificationProps);
    }

    protected Notification buildNotification(PendingIntent intent) {
        final Notification notification = getNotificationBuilder(intent).build();

        final Bundle bundle = mNotificationProps.mBundle;

        if (bundle.containsKey("call")) {
            final boolean call = bundle.getBoolean("call");
            if (call) {
                notification.defaults ^= Notification.DEFAULT_SOUND;
            }
        }

        if (
                bundle.containsKey("flagInsistent")
                && bundle.getBoolean("flagInsistent")
           ) {
            notification.flags |= Notification.FLAG_INSISTENT;
        }

        if (bundle.containsKey("vibrate")) {
            notification.defaults ^= Notification.DEFAULT_VIBRATE;

            final ArrayList<Integer> vibrate = (ArrayList) bundle.getSerializable("vibrate");
            long[] arr = new long[vibrate.size()];
            for (int i = 0; i < vibrate.size(); i++) {
                arr[i] = (long) Long.valueOf(vibrate.get(i));
            }
            notification.vibrate = arr;
        }

        Log.d("SEUNG", Arrays.toString(notification.vibrate));

        return notification;
    }

    protected Notification.Builder getNotificationBuilder(PendingIntent intent) {
        Log.d("SEUNG", "test");
        final Notification.Builder notification = new Notification.Builder(mContext)
                .setContentTitle(mNotificationProps.getTitle())
                .setContentText(mNotificationProps.getBody())
                .setContentIntent(intent)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true);

        final Bundle bundle = mNotificationProps.mBundle;

        if (bundle.containsKey("call")) {
            final boolean call = bundle.getBoolean("call");
            if (call) {
                notification.setFullScreenIntent(intent, true)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setCategory(Notification.CATEGORY_CALL)
                        .setVisibility(Notification.VISIBILITY_PUBLIC);

                // We'll use the default system ringtone for our incoming call notification.  You can
                // use your own audio resource here.
                Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                notification.setSound(ringtoneUri, new AudioAttributes.Builder()
                        // Setting the AudioAttributes is important as it identifies the purpose of your
                        // notification sound.
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
        }

        if (bundle.containsKey("priority")) {
            final int priority = (int) bundle.getDouble("priority");
            notification.setPriority(priority);
        }

        setUpIcon(notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final String channelId = mNotificationProps.getChannelId();
            if (channelId != null) {
                notification.setChannelId(channelId);
            } else {
                String CHANNEL_ID = "channel_01";
                String CHANNEL_NAME = "Channel Name";

                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT);
                final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.createNotificationChannel(channel);
                notification.setChannelId(CHANNEL_ID);
            }
        }

        return notification;
    }

    private void setUpIcon(Notification.Builder notification) {
        int iconResId = getAppResourceId("notification_icon", "drawable");
        if (iconResId != 0) {
            notification.setSmallIcon(iconResId);
        } else {
            notification.setSmallIcon(mContext.getApplicationInfo().icon);
        }

        setUpIconColor(notification);
    }

    private void setUpIconColor(Notification.Builder notification) {
        int colorResID = getAppResourceId("colorAccent", "color");
        if (colorResID != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color = mContext.getResources().getColor(colorResID);
            notification.setColor(color);
        }
    }

    protected int postNotification(Notification notification, Integer notificationId) {
        int id = notificationId != null ? notificationId : createNotificationId(notification);
        postNotification(id, notification);
        return id;
    }

    protected void postNotification(int id, Notification notification) {
        final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id, notification);
        // TODO: Better to launch a separate activity on top of lock screen.
        // https://app.asana.com/0/0/1199229502507829/f
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = Build.VERSION.SDK_INT >= 20 ? pm.isInteractive() : pm.isScreenOn(); // check if screen is on
        if (!isScreenOn) {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "myApp:notificationLock");
            wl.acquire(3000); //set your time in milliseconds
        }
    }

    protected int createNotificationId(Notification notification) {
        return (int) System.nanoTime();
    }

    private void notifyReceivedToJS() {
        mJsIOHelper.sendEventToJS(NOTIFICATION_RECEIVED_EVENT_NAME, mNotificationProps.asBundle(), mAppLifecycleFacade.getRunningReactContext());
    }

    private void notifyReceivedBackgroundToJS() {
        mJsIOHelper.sendEventToJS(NOTIFICATION_RECEIVED_BACKGROUND_EVENT_NAME, mNotificationProps.asBundle(), mAppLifecycleFacade.getRunningReactContext());
    }

    private void notifyOpenedToJS() {
        Bundle response = new Bundle();
        response.putBundle("notification", mNotificationProps.asBundle());

        mJsIOHelper.sendEventToJS(NOTIFICATION_OPENED_EVENT_NAME, response, mAppLifecycleFacade.getRunningReactContext());
    }

    protected void launchOrResumeApp() {
        final Intent intent = mAppLaunchHelper.getLaunchIntent(mContext);
        mContext.startActivity(intent);
    }

    private int getAppResourceId(String resName, String resType) {
        return mContext.getResources().getIdentifier(resName, resType, mContext.getPackageName());
    }
}
