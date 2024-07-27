/*
 * Copyright (C) 2022-2024 Paranoid Android
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

package co.aospa.glyph.Manager;

import android.os.Handler;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Utils.FileUtils;
import co.aospa.glyph.Utils.ResourceUtils;

public final class AnimationManager {

    private static final String TAG = "GlyphAnimationManager";
    private static final boolean DEBUG = true;

    private static boolean check(String name, boolean wait) {
        if (DEBUG) Log.d(TAG, "Playing animation | name: " + name + " | waiting: " + Boolean.toString(wait));

        if (StatusManager.isAllLedActive()) {
            if (DEBUG) Log.d(TAG, "All LEDs are active, exiting animation | name: " + name);
            return false;
        }

        if (StatusManager.isCallLedActive()) {
            if (DEBUG) Log.d(TAG, "Call animation is currently active, exiting animation | name: " + name);
            return false;
        }

        if (StatusManager.isAnimationActive()) {
            long start = System.currentTimeMillis();
            if (wait) {
                if (DEBUG) Log.d(TAG, "There is already an animation playing, wait | name: " + name);
                while (StatusManager.isAnimationActive()) {
                    if (System.currentTimeMillis() - start >= 2500) return false;
                }
            } else {
                if (DEBUG) Log.d(TAG, "There is already an animation playing, exiting | name: " + name);
                return false;
            }
        }

        return true;
    }

    private static boolean checkInterruption(String name) {
        if (StatusManager.isAllLedActive()
                || (name != "call" && StatusManager.isCallLedEnabled())
                || (name == "call" && !StatusManager.isCallLedEnabled())) {
            return true;
        }
        return false;
    }

    public static void playCsv(String name) {
        playCsv(name, false);
    }

    public static void playCsv(String name, boolean wait) {
        if (!check(name, wait))
                return;

        StatusManager.setAnimationActive(true);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                ResourceUtils.getAnimation(name)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (checkInterruption("csv")) throw new InterruptedException();
                line = line.replace(" ", "");
                line = line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
                String[] pattern = line.split(",");
                if (ArrayUtils.contains(Constants.getSupportedAnimationPatternLengths(), pattern.length)) {
                    updateLedFrame(pattern);
                } else {
                    if (DEBUG) Log.d(TAG, "Animation line length mismatch | name: " + name + " | line: " + line);
                    throw new InterruptedException();
                }
                Thread.sleep(17);
            }
        } catch (Exception e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation | name: " + name + " | exception: " + e);
        } finally {
            updateLedFrame(new float[5]);
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: " + name);
        }
    }

    public static void playCharging(Handler mHandler, int batteryLevel, boolean wait) {
        if (!check("charging", wait))
                return;

        StatusManager.setAnimationActive(true);

        boolean batteryDot = ResourceUtils.getBoolean("glyph_settings_battery_dot");
        int[] batteryArray = new int[ResourceUtils.getInteger("glyph_settings_battery_levels_num")];
        int amount = (int) (Math.floor((batteryLevel / 100.0) * (batteryArray.length - (batteryDot ? 2 : 1))) + (batteryDot ? 2 : 1));

        if (mHandler.hasMessages(0))
            mHandler.removeCallbacksAndMessages(null);

        try {
            for (int i = 0; i < batteryArray.length; i++) {
                if ( i <= amount - 1 && batteryLevel > 0) {
                    if (checkInterruption("charging")) throw new InterruptedException();
                    batteryArray[i] = Constants.getBrightness();
                    if (batteryDot && i == 0) continue;
                    updateLedFrame(batteryArray);
                    Thread.sleep(17);
                }
            }
            mHandler.postDelayed(() -> {
                try {
                    if (checkInterruption("charging")) throw new InterruptedException();
                    for (int i = batteryArray.length - 1; i >= 0; i--) {
                        if ( i <= amount - 1 && batteryLevel > 0) {
                            if (checkInterruption("charging")) throw new InterruptedException();
                            if (batteryArray[i] != 0) {
                                batteryArray[i] = 0;
                                updateLedFrame(batteryArray);
                                Thread.sleep(17);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: charging");
                    if (!StatusManager.isAllLedActive()) {
                        updateLedFrame(new int[batteryArray.length]);
                    }
                }
            }, 1000);
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: charging");
            if (!StatusManager.isAllLedActive()) {
                updateLedFrame(new int[batteryArray.length]);
            }
        } finally {
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: charging");
        }
    }

    public static void playVolume(Handler mHandler, int volumeLevel, boolean wait, boolean increase, boolean decrease) {
        if (!check("volume", wait))
            return;

        StatusManager.setAnimationActive(true);

        int[] volumeArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
        int amount = (int) (Math.floor((volumeLevel / 100D) * (volumeArray.length - 1)) + 1);
        int last_led = StatusManager.getVolumeLedLast();
        int next_led = amount - 1;
            
        if (mHandler.hasMessages(0))
            mHandler.removeCallbacksAndMessages(null);

        try {
            if (volumeLevel == 0) {
                if (checkInterruption("volume")) throw new InterruptedException();
                updateLedFrame(new int[volumeArray.length]);
                StatusManager.setVolumeLedLast(0);
            } else if (volumeLevel > 0) {
                if (checkInterruption("volume")) throw new InterruptedException();
                if (last_led == 0) {
                    for (int i = 0; i <= next_led ; i++) {
                        volumeArray[i] = Constants.getBrightness();
                    }
                    updateLedFrame(volumeArray);
                    Thread.sleep(17);
                } else if (last_led > 0) {
                    for (int i = 0; i <= last_led ; i++) {
                        volumeArray[i] = Constants.getBrightness();
                    }
                    if (increase) {
                        for (int i = last_led; i <= next_led ; i++) {
                            volumeArray[i] = Constants.getBrightness();
                            updateLedFrame(volumeArray);
                            Thread.sleep(17);
                        }
                    } else if (decrease) {
                        for (int i = last_led; i >= next_led ; i--) {
                            volumeArray[i] = Constants.getBrightness();
                            updateLedFrame(volumeArray);   
                            Thread.sleep(17);
                        }
                    }
                    StatusManager.setVolumeLedLast(next_led);
                }
            }
            mHandler.postDelayed(() -> {
                try {
                    if (checkInterruption("volume")) throw new InterruptedException();
                    for (int i = volumeArray.length - 1; i >= 0; i--) {
                        if (volumeArray[i] != 0) {
                            if (checkInterruption("volume")) throw new InterruptedException();
                            volumeArray[i] = 0;
                            updateLedFrame(volumeArray);
                            Thread.sleep(17);
                        }
                    }
                } catch (InterruptedException e) {
                    if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: volume");
                    if (!StatusManager.isAllLedActive()) {
                        updateLedFrame(new int[volumeArray.length]);
                    }
                }
            }, 1360);
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: volume");
            if (!StatusManager.isAllLedActive()) {
                updateLedFrame(new int[volumeArray.length]);
            }
        } finally {
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: volume");
        }
    }

    public static void playCall(Handler mHandler, String name) {
        StatusManager.setCallLedEnabled(true);

        if (!check("call: " + name, true))
            return;

        StatusManager.setCallLedActive(true);

        mHandler.post(() -> {
            while (StatusManager.isCallLedEnabled()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        ResourceUtils.getCallAnimation(name)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (checkInterruption("call")) throw new InterruptedException();
                        line = line.replace(" ", "");
                        line = line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
                        String[] pattern = line.split(",");
                        if (ArrayUtils.contains(Constants.getSupportedAnimationPatternLengths(), pattern.length)) {
                            updateLedFrame(pattern);
                        } else {
                            if (DEBUG) Log.d(TAG, "Animation line length mismatch | name: " + name + " | line: " + line);
                            throw new InterruptedException();
                        }
                        Thread.sleep(17);
                    }
                } catch (Exception e) {
                    if (DEBUG) Log.d(TAG, "Exception while playing animation | name: " + name + " | exception: " + e);
                } finally {
                    if (StatusManager.isAllLedActive()) {
                        if (DEBUG) Log.d(TAG, "All LED active, pause playing animation | name: " + name);
                        while (StatusManager.isAllLedActive()) {}
                    }
                }
            }
        });
    }

    public static void stopCall(Handler mHandler, String name) {
        if (DEBUG) Log.d(TAG, "Disabling Call Animation");
        StatusManager.setCallLedEnabled(false);
        mHandler.removeCallbacksAndMessages(null);
        updateLedFrame(new float[5]);
        StatusManager.setCallLedActive(false);
        if (DEBUG) Log.d(TAG, "Done playing animation | name: " + name);
    }

    public static void playEssential() {
        if (DEBUG) Log.d(TAG, "Playing Essential Animation");
        int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
        if (!StatusManager.isEssentialLedActive()) {
                if (!check("essential", true))
                    return;

                StatusManager.setAnimationActive(true);

                try {
                    if (checkInterruption("essential")) throw new InterruptedException();
                    int[] steps = {1, 2, 4, 7};
                    for (int i : steps) {
                        if (checkInterruption("essential")) throw new InterruptedException();
                        updateLedSingle(led, Constants.getMaxBrightness() / 100 * i);
                        Thread.sleep(17);
                    }
                } catch (InterruptedException e) {}

                StatusManager.setAnimationActive(false);
                StatusManager.setEssentialLedActive(true);
                if (DEBUG) Log.d(TAG, "Done playing animation | name: essential");
        } else {
            updateLedSingle(led, Constants.getMaxBrightness() / 100 * 7);
            return;
        }
    }

    public static void stopEssential() {
        if (DEBUG) Log.d(TAG, "Disabling Essential Animation");
        StatusManager.setEssentialLedActive(false);
        if (!StatusManager.isAnimationActive() && !StatusManager.isAllLedActive()) {
            int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
            updateLedSingle(led, 0);
        }
    }

    public static void playMusic(String name) {
        float maxBrightness = (float) Constants.getMaxBrightness();
        float[] pattern = new float[5];

        switch (name) {
            case "low":
                pattern[4] = maxBrightness;
                break;
            case "mid_low":
                pattern[3] = maxBrightness;
                break;
            case "mid":
                pattern[2] = maxBrightness;
                break;
            case "mid_high":
                pattern[0] = maxBrightness;
                break;
            case "high":
                pattern[1] = maxBrightness;
                break;
            default:
                if (DEBUG) Log.d(TAG, "Name doesn't match any zone, returning | name: " + name);
                return;
        }

        try {
            updateLedFrame(pattern);
            Thread.sleep(85);
        } catch (Exception e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation | name: music: " + name + " | exception: " + e);
        } finally {
            updateLedFrame(new float[5]);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: " + name);
        }
    }

    private static void updateLedFrame(String[] pattern) {
        updateLedFrame(Arrays.stream(pattern)
                .mapToInt(Integer::parseInt)
                .toArray());
    }

    private static void updateLedFrame(int[] pattern) {
        float[] floatPattern = new float[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            floatPattern[i] = (float) pattern[i];
        }
        updateLedFrame(floatPattern);
    }

    private static void updateLedFrame(float[] pattern) {
        //if (DEBUG) Log.d(TAG, "Updating pattern: " + pattern);
        float maxBrightness = (float) Constants.getMaxBrightness();
        int essentialLed = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
        if (StatusManager.isEssentialLedActive()) {
            if (pattern.length == 5) { // Phone (1) pattern
                if (pattern[1] < (maxBrightness / 100 * 7)) {
                    pattern[1] = maxBrightness / 100 * 7;
                }
            } else if (pattern.length == 33) { // Phone (2) pattern
                if (pattern[2] < (maxBrightness / 100 * 7)) {
                    pattern[2] = maxBrightness / 100 * 7;
                }
            }
        }
        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = pattern[i] / maxBrightness * Constants.getBrightness();
        }
        FileUtils.writeFrameLed(pattern);
    }

    private static void updateLedSingle(int led, String brightness) {
        updateLedSingle(led, Float.parseFloat(brightness));
    }

    private static void updateLedSingle(int led, int brightness) {
        updateLedSingle(led, (float) brightness);
    }

    private static void updateLedSingle(int led, float brightness) {
        //if (DEBUG) Log.d(TAG, "Updating led | led: " + led + " | brightness: " + brightness);
        float maxBrightness = (float) Constants.getMaxBrightness();
        int essentialLed = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
        if (StatusManager.isEssentialLedActive()
                && led == essentialLed
                && brightness < (maxBrightness / 100 * 7)) {
            brightness = maxBrightness / 100 * 7;
        }
        FileUtils.writeSingleLed(led, brightness / maxBrightness * Constants.getBrightness());
    }
}
