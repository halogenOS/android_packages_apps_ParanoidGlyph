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

import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Utils.FileUtils;
import co.aospa.glyph.Utils.ResourceUtils;

public final class AnimationManager {

    private static final String TAG = "GlyphAnimationManager";
    private static final boolean DEBUG = true;

    private static Future<?> submit(Runnable runnable) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        return executorService.submit(runnable);
    }

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
        submit(() -> {
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
                    Thread.sleep(16, 666000);
                }
            } catch (Exception e) {
                if (DEBUG) Log.d(TAG, "Exception while playing animation | name: " + name + " | exception: " + e);
            } finally {
                updateLedFrame(new float[5]);
                StatusManager.setAnimationActive(false);
                if (DEBUG) Log.d(TAG, "Done playing animation | name: " + name);
            }
        });
    }

    public static void playCharging(int batteryLevel, boolean wait) {
        if (!check("charging", wait))
            return;

        StatusManager.setAnimationActive(true);
        StatusManager.setChargingAnimationActive(true);

        int[] batteryArray = StatusManager.getBatteryArray();
        int amount = (int) Math.floor((batteryLevel / 100D) * batteryArray.length);
        int last = StatusManager.getChargingLedLast();
        int next = amount - 1;

        try {
            if (last <= next) {
                for (int i = last; i <= next; i++) {
                    if (checkInterruption("charging")) throw new InterruptedException();
                    StatusManager.setChargingLedLast(i);
                    batteryArray[i] = Constants.MAX_PATTERN_BRIGHTNESS;
                    updateLedFrame(batteryArray);
                    Thread.sleep(22);
                }
            } else if (last > next) {
                for (int i = last; i > next; i--) {
                    if (checkInterruption("charging")) throw new InterruptedException();
                    StatusManager.setChargingLedLast(i);
                    batteryArray[i] = 0;
                    updateLedFrame(batteryArray);
                    Thread.sleep(22);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: charging");
            if (!StatusManager.isAllLedActive()) {
                StatusManager.setChargingLedLast(0);
                batteryArray = new int[ResourceUtils.getInteger("glyph_settings_battery_levels_num")];
                updateLedFrame(batteryArray);
            }
        } finally {
            StatusManager.setAnimationActive(false);
            StatusManager.setBatteryArray(batteryArray);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: charging");
        }
    }

    public static void dismissCharging() {
        int[] emptyArray = new int[ResourceUtils.getInteger("glyph_settings_battery_levels_num")];
        int[] batteryArray = StatusManager.getBatteryArray();

        if (Arrays.equals(emptyArray, batteryArray))
            return;

        if (!check("Dismiss charging", false))
            return;

        StatusManager.setAnimationActive(true);

        try {
            if (checkInterruption("Dismiss charging")) throw new InterruptedException();
            for (int i = batteryArray.length - 1; i >= 0; i--) {
                if (checkInterruption("Dismiss charging")) throw new InterruptedException();
                if (batteryArray[i] != 0) {
                    StatusManager.setChargingLedLast(i);
                    batteryArray[i] = 0;
                    updateLedFrame(batteryArray);
                    Thread.sleep(22);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: Dismiss charging");
            if (!StatusManager.isAllLedActive())
                updateLedFrame(new int[batteryArray.length]);
        } finally {
            StatusManager.setChargingLedLast(0);
            StatusManager.setChargingAnimationActive(false);
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: Dismiss charging");
        }
    }

    public static void playVolume(int volumeLevel, boolean wait) {
        if (!check("volume", wait))
            return;

        StatusManager.setAnimationActive(true);
        StatusManager.setVolumeAnimationActive(true);

        int[] volumeArray = StatusManager.getVolumeArray();
        int amount = (int) Math.round((volumeLevel / 100D) * volumeArray.length);
        int last = StatusManager.getVolumeLedLast();
        int next = amount - 1;

        try {
            if (last <= next) {
                for (int i = last; i <= next; i++) {
                    if (checkInterruption("volume")) throw new InterruptedException();
                    StatusManager.setVolumeLedLast(i);
                    volumeArray[i] = Constants.MAX_PATTERN_BRIGHTNESS;
                    updateLedFrame(volumeArray);
                    Thread.sleep(22);
                }
            } else if (last > next) {
                for (int i = last; i > next; i--) {
                    if (checkInterruption("volume")) throw new InterruptedException();
                    StatusManager.setVolumeLedLast(i);
                    volumeArray[i] = 0;
                    updateLedFrame(volumeArray);
                    Thread.sleep(22);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: volume");
            if (!StatusManager.isAllLedActive()) {
                StatusManager.setVolumeLedLast(0);
                volumeArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
                updateLedFrame(volumeArray);
            }
        } finally {
            StatusManager.setAnimationActive(false);
            StatusManager.setVolumeArray(volumeArray);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: volume");
        }
    }

    public static void dismissVolume() {
        int[] emptyArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
        int[] volumeArray = StatusManager.getVolumeArray();

        if (Arrays.equals(emptyArray, volumeArray))
            return;

        if (!check("Dismiss volume", false))
            return;

        StatusManager.setAnimationActive(true);

        try {
            if (checkInterruption("Dismiss volume")) throw new InterruptedException();
            for (int i = volumeArray.length - 1; i >= 0; i--) {
                if (volumeArray[i] != 0) {
                    if (checkInterruption("Dismiss volume")) throw new InterruptedException();
                    StatusManager.setVolumeLedLast(i);
                    volumeArray[i] = 0;
                    updateLedFrame(volumeArray);
                    Thread.sleep(22);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: Dismiss volume");
            if (!StatusManager.isAllLedActive())
                updateLedFrame(new int[volumeArray.length]);
        } finally {
            StatusManager.setVolumeLedLast(0);
            StatusManager.setVolumeAnimationActive(false);
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: Dismiss volume");
        }
    }

    public static void playCall(String name) {
        StatusManager.setCallLedEnabled(true);

        if (!check("call: " + name, true))
            return;

        StatusManager.setCallLedActive(true);

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
                    Thread.sleep(16, 666000);
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
    }

    public static void stopCall() {
        if (DEBUG) Log.d(TAG, "Disabling Call Animation");
        StatusManager.setCallLedEnabled(false);
        updateLedFrame(new float[5]);
        StatusManager.setCallLedActive(false);
        if (DEBUG) Log.d(TAG, "Done playing Call Animation");
    }

    public static void playEssential() {
        if (DEBUG) Log.d(TAG, "Playing Essential Animation");
        int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
        if (!StatusManager.isEssentialLedActive()) {
            submit(() -> {
                if (!check("essential", true))
                    return;

                StatusManager.setAnimationActive(true);

                try {
                    if (checkInterruption("essential")) throw new InterruptedException();
                    int[] steps = {12, 24, 36, 48, 60};
                    for (int i : steps) {
                        if (checkInterruption("essential")) throw new InterruptedException();
                        updateLedSingle(led, Constants.MAX_PATTERN_BRIGHTNESS / 100 * i);
                        Thread.sleep(16, 666000);
                    }
                } catch (InterruptedException e) {}
                StatusManager.setAnimationActive(false);
                StatusManager.setEssentialLedActive(true);
                if (DEBUG) Log.d(TAG, "Done playing animation | name: essential");
            });
        } else {
            updateLedSingle(led, Constants.MAX_PATTERN_BRIGHTNESS / 100 * 60);
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
        if (StatusManager.isAnimationActive() || StatusManager.isChargingAnimationActive() 
            || StatusManager.isVolumeAnimationActive() || StatusManager.isCallLedEnabled())
            return;

        float maxPatternBrightness = (float) Constants.MAX_PATTERN_BRIGHTNESS;
        float[] pattern = new float[5];

        switch (name) {
            case "low":
                pattern[4] = maxPatternBrightness;
                break;
            case "mid_low":
                pattern[3] = maxPatternBrightness;
                break;
            case "mid":
                pattern[2] = maxPatternBrightness;
                break;
            case "mid_high":
                pattern[0] = maxPatternBrightness;
                break;
            case "high":
                pattern[1] = maxPatternBrightness;
                break;
            default:
                if (DEBUG) Log.d(TAG, "Name doesn't match any zone, returning | name: " + name);
                return;
        }

        try {
            updateLedFrame(pattern);
            Thread.sleep(106);
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
        float maxPatternBrightness = (float) Constants.MAX_PATTERN_BRIGHTNESS;
        float currentBrightness = (float) Constants.getBrightness();
        int essentialLed = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");

        if (StatusManager.isEssentialLedActive()) {
            if (pattern.length == 5) { // Phone (1) pattern
                if (pattern[1] < (maxPatternBrightness / 100 * 60)) {
                    pattern[1] = maxPatternBrightness / 100 * 60;
                }
            } else if (pattern.length == 33) { // Phone (2) pattern
                if (pattern[2] < (maxPatternBrightness / 100 * 60)) {
                    pattern[2] = maxPatternBrightness / 100 * 60;
                }
            }
        }

        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = pattern[i] / maxPatternBrightness * currentBrightness;
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
        float maxPatternBrightness = (float) Constants.MAX_PATTERN_BRIGHTNESS;
        float currentBrightness = (float) Constants.getBrightness();
        int essentialLed = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");

        if (StatusManager.isEssentialLedActive()
                && led == essentialLed
                && brightness < (maxPatternBrightness / 100 * 60)) {
            brightness = maxPatternBrightness / 100 * 60;
        }

        brightness = brightness / maxPatternBrightness * currentBrightness;

        FileUtils.writeSingleLed(led, brightness);
    }
}
