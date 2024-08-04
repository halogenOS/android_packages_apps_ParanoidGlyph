/*
 * Copyright (C) 2023-2024 Paranoid Android
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

package co.aospa.glyph.Services;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import co.aospa.glyph.Manager.AnimationManager;

public class VolumeLevelService extends Service {

    private static final String TAG = "GlyphVolumeLevelService";
    private static final boolean DEBUG = true;

    private HandlerThread thread;
    private Handler mThreadHandler;
    private ContentResolver mContentResolver;
    private VolumeObserver mVolumeObserver;

    private AudioManager audioManager;
    private Runnable dismissVolume = new Runnable() {
        @Override
        public void run() {
            AnimationManager.dismissVolume();
        }
    };

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");
        
        // Add a handler thread
        thread = new HandlerThread("VolumeLevelService");
        thread.start();
        Looper looper = thread.getLooper();
        mThreadHandler = new Handler(looper);

        audioManager = (AudioManager) getSystemService(AudioManager.class);

        mContentResolver = getContentResolver();
        mVolumeObserver = new VolumeObserver();
        mVolumeObserver.register(mContentResolver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        mVolumeObserver.unregister(mContentResolver);
        thread.quit();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int getCurrentVolume() {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }
    
    private int getMaxVolume() {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    private class VolumeObserver extends ContentObserver {
        private int previousVolume;

        public VolumeObserver() {
            super(new Handler());
        }

        public void register(ContentResolver cr) {
            previousVolume = getCurrentVolume();
            cr.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                this);
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

            int delta = previousVolume - getCurrentVolume();

            if (delta != 0) {
                if (mThreadHandler.hasCallbacks(dismissVolume))
                    mThreadHandler.removeCallbacks(dismissVolume);

                if (delta < 0) {
                    if (DEBUG) Log.d(TAG, "Increased: " + (int) (Math.round(100D / getMaxVolume() * getCurrentVolume())));
                    mThreadHandler.post(() -> {
                        AnimationManager.playVolume((int) (Math.round(100D / getMaxVolume() * getCurrentVolume())), false);
                    });
                } else if (delta > 0) {
                    if (DEBUG) Log.d(TAG, "Decreased: " + (int) (Math.round(100D / getMaxVolume() * getCurrentVolume())));
                    mThreadHandler.post(() -> {
                        AnimationManager.playVolume((int) (Math.round(100D / getMaxVolume() * getCurrentVolume())), false);
                    });
                }

                mThreadHandler.postDelayed(dismissVolume, 3000);
                previousVolume = getCurrentVolume();
            }
        }
    }
}
