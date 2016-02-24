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

package com.cyanogenmod.setupwizard.ui;

import android.animation.Animator;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.cyanogenmod.setupwizard.R;
import com.cyanogenmod.setupwizard.SetupWizardApp;
import com.cyanogenmod.setupwizard.cmstats.SetupStats;
import com.cyanogenmod.setupwizard.setup.CMSetupWizardData;
import com.cyanogenmod.setupwizard.setup.Page;
import com.cyanogenmod.setupwizard.setup.SetupDataCallbacks;
import com.cyanogenmod.setupwizard.util.EnableAccessibilityController;
import com.cyanogenmod.setupwizard.util.SetupWizardUtils;

import cyanogenmod.themes.ThemeManager;

import java.util.ArrayList;


public class SetupWizardActivity extends Activity implements SetupDataCallbacks,
        ThemeManager.ThemeChangeListener {

    private static final String TAG = SetupWizardActivity.class.getSimpleName();

    private static final int UI_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private View mRootView;
    private View mButtonBar;
    private Button mNextButton;
    private Button mPrevButton;
    private ImageView mReveal;
    private ProgressBar mFinishingProgressBar;

    private EnableAccessibilityController mEnableAccessibilityController;

    private CMSetupWizardData mSetupData;

    private final Handler mHandler = new Handler();

    private volatile boolean mIsFinishing = false;

    private static long sLaunchTime = 0;

    private final ArrayList<Runnable> mFinishRunnables = new ArrayList<Runnable>();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final boolean isOwner = SetupWizardUtils.isOwner();
        if (!isOwner) {
            finish();
        }
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(UI_FLAGS);
        decorView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {

                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            decorView.setSystemUiVisibility(UI_FLAGS);
                        }
                    }
                });
        if (sLaunchTime == 0) {
            SetupStats.addEvent(SetupStats.Categories.APP_LAUNCH, TAG);
            sLaunchTime = System.nanoTime();
        }
        setContentView(R.layout.setup_main);
        mRootView = findViewById(R.id.root);
        mRootView.setSystemUiVisibility(UI_FLAGS);
        mReveal = (ImageView)mRootView.findViewById(R.id.reveal);
        mButtonBar = findViewById(R.id.button_bar);
        mFinishingProgressBar = (ProgressBar)findViewById(R.id.finishing_bar);
        ((SetupWizardApp)getApplicationContext()).disableStatusBar();
        mSetupData = (CMSetupWizardData)getLastNonConfigurationInstance();
        if (mSetupData == null) {
            mSetupData = new CMSetupWizardData(getApplicationContext());
        }
        mNextButton = (Button) findViewById(R.id.next_button);
        mPrevButton = (Button) findViewById(R.id.prev_button);
        if (mSetupData.isFinished()) {
            mNextButton.setVisibility(View.INVISIBLE);
            mPrevButton.setVisibility(View.INVISIBLE);
        }
        mSetupData.registerListener(this);
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableButtonBar(false);
                mSetupData.onNextPage();
            }
        });
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableButtonBar(false);
                mSetupData.onPreviousPage();
            }
        });
        if (savedInstanceState == null) {
            Page page = mSetupData.getCurrentPage();
            page.doLoadAction(getFragmentManager(), Page.ACTION_NEXT);
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("data")) {
            mSetupData.load(savedInstanceState.getBundle("data"));
        }
        mEnableAccessibilityController =
                EnableAccessibilityController.getInstance(getApplicationContext());
        mRootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean consumeIntercept = mEnableAccessibilityController.onInterceptTouchEvent(event);
                boolean consumeTouch = mEnableAccessibilityController.onTouchEvent(event);
                return consumeIntercept && consumeTouch;
            }
        });
        registerReceiver(mSetupData, mSetupData.getIntentFilter());
    }

    @Override
    protected void onResume() {
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(UI_FLAGS);
        super.onResume();
        if (isFinishing()) {
            return;
        }
        if (mSetupData.isFinished()) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    finishSetup();
                }
            }, 500);
        }  else {
            mSetupData.onResume();
            onPageTreeChanged();
            enableButtonBar(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSetupData != null) {
            mSetupData.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSetupData != null) {
            mSetupData.onDestroy();
            mSetupData.unregisterListener(this);
            unregisterReceiver(mSetupData);
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mSetupData;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("data", mSetupData.save());
    }

    @Override
    public void onBackPressed() {
        if (!mSetupData.isFirstPage()) {
            mSetupData.onPreviousPage();
        }
    }

    @Override
    public void onNextPage() {
        Page page = mSetupData.getCurrentPage();
        if (!isFinishing()) {
            page.doLoadAction(getFragmentManager(), Page.ACTION_NEXT);
        }
    }

    @Override
    public void onPreviousPage() {
        Page page = mSetupData.getCurrentPage();
        if (!isFinishing()) {
            page.doLoadAction(getFragmentManager(), Page.ACTION_PREVIOUS);
        }
    }

    @Override
    public void onPageLoaded(Page page) {
        updateButtonBar();
        enableButtonBar(true);
    }

    @Override
    public void onPageTreeChanged() {
        updateButtonBar();
    }

    private void enableButtonBar(boolean enabled) {
        mNextButton.setEnabled(enabled);
        mPrevButton.setEnabled(enabled);
    }

    private void updateButtonBar() {
        Page page = mSetupData.getCurrentPage();
        mNextButton.setText(page.getNextButtonTitleResId());
        if (page.getPrevButtonTitleResId() != -1) {
            mPrevButton.setText(page.getPrevButtonTitleResId());
        } else {
            mPrevButton.setText("");
        }
        if (mSetupData.isFirstPage()) {
            mPrevButton.setCompoundDrawables(null, null, null, null);
            mPrevButton.setVisibility(SetupWizardUtils.hasTelephony(this) ?
                    View.VISIBLE : View.INVISIBLE);
        } else {
            mPrevButton.setCompoundDrawablesWithIntrinsicBounds(
                    getDrawable(R.drawable.ic_chevron_left_dark),
                    null, null, null);
        }
        final Resources resources = getResources();
        if (mSetupData.isLastPage()) {
            mButtonBar.setBackgroundResource(R.color.primary);
            mNextButton.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    getDrawable(R.drawable.ic_chevron_right_wht), null);
            mNextButton.setTextColor(resources.getColor(R.color.white));
            mPrevButton.setCompoundDrawablesWithIntrinsicBounds(
                    getDrawable(R.drawable.ic_chevron_left_wht), null,
                    null, null);
            mPrevButton.setTextColor(resources.getColor(R.color.white));
        } else {
            mButtonBar.setBackgroundResource(R.color.button_bar_background);
            mNextButton.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    getDrawable(R.drawable.ic_chevron_right_dark), null);
            mNextButton.setTextColor(resources.getColorStateList(R.color.button_bar_text));
            mPrevButton.setTextColor(resources.getColorStateList(R.color.button_bar_text));
        }
    }

    @Override
    public Page getPage(String key) {
        return mSetupData.getPage(key);
    }

    @Override
    public Page getPage(int key) {
        return mSetupData.getPage(key);
    }

    @Override
    public boolean isCurrentPage(Page page) {
        return mSetupData.isCurrentPage(page);
    }

    @Override
    public void addFinishRunnable(Runnable runnable) {
        mFinishRunnables.add(runnable);
    }

    @Override
    public void onFinish() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        Animation fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
        mNextButton.startAnimation(fadeOut);
        mNextButton.setVisibility(View.INVISIBLE);
        mPrevButton.startAnimation(fadeOut);
        mPrevButton.setVisibility(View.INVISIBLE);
        final SetupWizardApp setupWizardApp = (SetupWizardApp)getApplication();
        setupWizardApp.enableStatusBar();
        setupWizardApp.enableCaptivePortalDetection();
        Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        mFinishingProgressBar.setVisibility(View.VISIBLE);
        mFinishingProgressBar.setIndeterminate(true);
        mFinishingProgressBar.startAnimation(fadeIn);
        final ThemeManager tm = ThemeManager.getInstance();
        tm.addClient(this);
        mSetupData.finishPages();
    }

    @Override
    public void onFinish(boolean isSuccess) {
        if (isResumed()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    finishSetup();
                }
            });
        }
    }

    @Override
    public void onProgress(int progress) {
        if (progress > 0) {
            mFinishingProgressBar.setIndeterminate(false);
            mFinishingProgressBar.setProgress(progress);
        }
    }

    @Override
    public void finishSetup() {
        if (!mIsFinishing) {
            SetupStats.addEvent(SetupStats.Categories.APP_FINISHED, TAG,
                    SetupStats.Label.TOTAL_TIME, String.valueOf(
                            System.nanoTime() - sLaunchTime));
            final SetupWizardApp setupWizardApp = (SetupWizardApp)getApplication();
            setupWizardApp.sendStickyBroadcastAsUser(
                    new Intent(SetupWizardApp.ACTION_FINISHED),
                    UserHandle.getCallingUserHandle());
            mIsFinishing = true;
            setupRevealImage();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translucent_enter, R.anim.translucent_exit);
    }

    private void setupRevealImage() {
        mFinishingProgressBar.setProgress(100);
        Animation fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
        mFinishingProgressBar.startAnimation(fadeOut);
        mFinishingProgressBar.setVisibility(View.INVISIBLE);

        final Point p = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(p);
        final WallpaperManager wallpaperManager =
                WallpaperManager.getInstance(SetupWizardActivity.this);
        wallpaperManager.forgetLoadedWallpaper();
        final Bitmap wallpaper = wallpaperManager.getBitmap();
        Bitmap cropped = null;
        if (wallpaper != null) {
            cropped = Bitmap.createBitmap(wallpaper, 0,
                    0, Math.min(p.x, wallpaper.getWidth()),
                    Math.min(p.y, wallpaper.getHeight()));
        }
        if (cropped != null) {
            mReveal.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mReveal.setImageBitmap(cropped);
        } else {
            mReveal.setBackground(wallpaperManager
                    .getBuiltInDrawable(p.x, p.y, false, 0, 0));
        }
        animateOut();
    }

    private void animateOut() {
        int cx = (mReveal.getLeft() + mReveal.getRight()) / 2;
        int cy = (mReveal.getTop() + mReveal.getBottom()) / 2;
        int finalRadius = Math.max(mReveal.getWidth(), mReveal.getHeight());
        Animator anim =
                ViewAnimationUtils.createCircularReveal(mReveal, cx, cy, 0, finalRadius);
        anim.setDuration(900);
        anim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mReveal.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        finalizeSetup();
                    }
                });
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });
        anim.start();
    }

    private void finalizeSetup() {
        mFinishRunnables.add(new Runnable() {
            @Override
            public void run() {
                Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.USER_SETUP_COMPLETE, 1);
                if (mEnableAccessibilityController != null) {
                    mEnableAccessibilityController.onDestroy();
                }
                final ThemeManager tm = ThemeManager.getInstance();
                tm.removeClient(SetupWizardActivity.this);
                SetupStats.sendEvents(SetupWizardActivity.this);
                SetupWizardUtils.disableGMSSetupWizard(SetupWizardActivity.this);
                final WallpaperManager wallpaperManager =
                        WallpaperManager.getInstance(SetupWizardActivity.this);
                wallpaperManager.forgetLoadedWallpaper();
            }
        });
        new FinishTask(this, mFinishRunnables).execute();
    }

    private static class FinishTask extends AsyncTask<Void, Void, Boolean> {

        private final SetupWizardActivity mActivity;
        private final ArrayList<Runnable> mFinishRunnables;

        public FinishTask(SetupWizardActivity activity,
                ArrayList<Runnable> finishRunnables) {
            mActivity = activity;
            mFinishRunnables = finishRunnables;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            for (Runnable runnable : mFinishRunnables) {
                runnable.run();
            }
            SetupWizardUtils.disableSetupWizard(mActivity);
            return Boolean.TRUE;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            mActivity.startActivity(intent);
            mActivity.finish();
        }
    }
}
