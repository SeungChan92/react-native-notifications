package com.wix.reactnativenotifications.core.notification;

import android.os.Bundle;

public class PushNotificationProps {

    protected Bundle mBundle;

    public PushNotificationProps(Bundle bundle) {
        mBundle = bundle;
    }

    public String getTitle() {
        return getBundleStringFirstNotNull("gcm.notification.title", "title");
    }

    public String getBody() {
        return getBundleStringFirstNotNull("gcm.notification.body", "body");
    }

    public String getChannelId() {
        return getBundleStringFirstNotNull("channelId", null);
    }

    public int getPriority() {
        return getBundleIntFirstNotNull("priority", null);
    }

    public boolean getFlagInsistent() {
        return getBundleBooleanFirstNotNull("flagInsistent", null);
    }

    public Bundle asBundle() {
        return (Bundle) mBundle.clone();
    }

    public boolean isFirebaseBackgroundPayload() {
        return mBundle.containsKey("google.message_id");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        for (String key : mBundle.keySet()) {
            sb.append(key).append("=").append(mBundle.get(key)).append(", ");
        }
        return sb.toString();
    }

    protected PushNotificationProps copy() {
        return new PushNotificationProps((Bundle) mBundle.clone());
    }

    private String getBundleStringFirstNotNull(String key1, String key2) {
        String result = mBundle.getString(key1);
        return result == null ? mBundle.getString(key2) : result;
    }

    private int getBundleIntFirstNotNull(String key1, String key2) {
        int result = mBundle.getInt(key1);
        return result == 0 ? mBundle.getInt(key2) : result;
    }

    private int getBundleBooleanFirstNotNull(String key1, String key2) {
        int result = mBundle.getBoolean(key1, null);
        return result == null ? mBundle.getBoolean(key2, null) : result;
    }
}
