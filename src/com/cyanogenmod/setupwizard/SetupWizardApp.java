/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.setupwizard;


import android.app.Application;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.provider.Settings;

import com.cyanogenmod.setupwizard.util.SetupWizardUtils;

public class SetupWizardApp extends Application {

    public static final String TAG = SetupWizardApp.class.getSimpleName();
    // Leave this off for release
    public static final boolean DEBUG = false;

    public static final String ACTION_FINISHED = "com.cyanogenmod.setupwizard.SETUP_FINISHED";

    public static final String ACCOUNT_TYPE_CYANOGEN = "com.cyanogen";
    public static final String ACCOUNT_TYPE_GMS = "com.google";

    public static final String ACTION_SETUP_WIFI = "com.android.net.wifi.SETUP_WIFI_NETWORK";
    public static final String ACTION_VIEW_LEGAL = "cyanogenmod.intent.action.LEGALESE";

    public static final String ACTION_SETUP_FINGERPRINT = "android.settings.FINGERPRINT_SETUP";

    public static final String EXTRA_FIRST_RUN = "firstRun";
    public static final String EXTRA_ALLOW_SKIP = "allowSkip";
    public static final String EXTRA_AUTO_FINISH = "wifi_auto_finish_on_connect";
    public static final String EXTRA_SHOW_BUTTON_BAR = "extra_prefs_show_button_bar";
    public static final String EXTRA_USE_IMMERSIVE = "useImmersiveMode";
    public static final String EXTRA_THEME = "theme";
    public static final String EXTRA_MATERIAL_LIGHT = "material_light";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_DETAILS = "details";
    public static final String EXTRA_FRAGMENT = "fragment";
    public static final String EXTRA_ACTION_ID = "actionId";
    public static final String EXTRA_SUPRESS_D2D_SETUP = "suppress_device_to_device_setup";

    public static final String KEY_DETECT_CAPTIVE_PORTAL = "captive_portal_detection_enabled";

    private static final String[] THEME_PACKAGES = {
            "org.cyanogenmod.theme.chooser",
            "com.cyngn.theme.chooser",
            "com.cyngn.themestore"
    };

    public static final int REQUEST_CODE_SETUP_WIFI = 0;
    public static final int REQUEST_CODE_SETUP_GMS= 1;
    public static final int REQUEST_CODE_RESTORE_GMS= 2;
    public static final int REQUEST_CODE_SETUP_CYANOGEN= 3;
    public static final int REQUEST_CODE_SETUP_CAPTIVE_PORTAL= 4;
    public static final int REQUEST_CODE_SETUP_BLUETOOTH= 5;
    public static final int REQUEST_CODE_UNLOCK = 6;
    public static final int REQUEST_CODE_SETUP_FINGERPRINT = 7;

    public static final int RADIO_READY_TIMEOUT = 10 * 1000;

    private boolean mIsRadioReady = false;

    private final Handler mHandler = new Handler();

    private final Runnable mRadioTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            mIsRadioReady = true;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            // Since this is a new component, we need to disable here if the user
            // has already been through setup on a previous version.
            final boolean isOwner = SetupWizardUtils.isOwner();
            if (!isOwner
                    || Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.USER_SETUP_COMPLETE) == 1) {
                Thread t = new Thread(){
                    @Override
                    public void run() {
                        Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
                        Settings.Secure.putInt(getContentResolver(),
                                Settings.Secure.USER_SETUP_COMPLETE, 1);
                        SetupWizardUtils.disableGMSSetupWizard(SetupWizardApp.this);
                        SetupWizardUtils.disableSetupWizard(SetupWizardApp.this);
                        if (!isOwner) {
                            disableThemeComponentsForSecondaryUser();
                        }
                    }
                };
                t.run();
            }  else {
                SetupWizardUtils.disableCaptivePortalDetection(getApplicationContext());
            }
        } catch (Settings.SettingNotFoundException e) {
            // Continue with setup
            SetupWizardUtils.disableCaptivePortalDetection(getApplicationContext());
        }
        mHandler.postDelayed(mRadioTimeoutRunnable, SetupWizardApp.RADIO_READY_TIMEOUT);
    }

    public boolean isRadioReady() {
        return mIsRadioReady;
    }

    public void setRadioReady(boolean radioReady) {
        if (!mIsRadioReady && radioReady) {
            mHandler.removeCallbacks(mRadioTimeoutRunnable);
        }
        mIsRadioReady = radioReady;
    }

    private void disableThemeComponentsForSecondaryUser() {
        PackageManager pm = getPackageManager();
        for(String pkgName : THEME_PACKAGES) {
            try {
                pm.getApplicationInfo(pkgName, 0);
                pm.setApplicationEnabledSetting(pkgName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
            } catch (PackageManager.NameNotFoundException e) {
                // don't care
            }
        }
    }
}
