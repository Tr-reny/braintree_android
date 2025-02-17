package com.braintreepayments.api;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import androidx.annotation.Nullable;

class ManifestValidator {

    boolean isActivityDeclaredInAndroidManifest(Context context, Class klass) {
        return getActivityInfo(context, klass) != null;
    }

    boolean isUrlSchemeDeclaredInAndroidManifest(Context context, String urlScheme, Class klass) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse(urlScheme + "://"))
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addCategory(Intent.CATEGORY_BROWSABLE);

        ActivityInfo activityInfo = getActivityInfo(context, klass);
        AppHelper appHelper = new AppHelper();
        return (activityInfo != null && activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK &&
                appHelper.isIntentAvailable(context, intent));
    }

    @Nullable
    ActivityInfo getActivityInfo(Context context, Class klass) {
        try {
            PackageInfo packageInfo =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);
            ActivityInfo[] activities = packageInfo.activities;
            if (activities != null) {
                for (ActivityInfo activityInfo : activities) {
                    if (activityInfo.name.equals(klass.getName())) {
                        return activityInfo;
                    }
                }
            }
        } catch (NameNotFoundException ignored) {}

        return null;
    }
}
