/*
 * Copyright 2016, The Android Open Source Project
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

package com.example.androidthings.assistant;

import android.media.AudioDeviceInfo;
import android.os.Build;

@SuppressWarnings("WeakerAccess")
public class BoardDefaults {
    public static final String DEVICE_RPI3 = "rpi3";
    public static final String DEVICE_RPI3BP = "rpi3bp";
    public static final String DEVICE_IMX7D_PICO = "imx7d_pico";

    /**
     * Return the GPIO pin that the LED is connected on.
     * For example, on Intel Edison Arduino breakout, pin "IO13" is connected to an onboard LED
     * that turns on when the GPIO pin is HIGH, and off when low.
     */
    public static String getGPIOForLED() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
            case DEVICE_RPI3BP:
                return "BCM25";
            case DEVICE_IMX7D_PICO:
                return "GPIO2_IO02";
            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);
        }
    }

    /**
     * Return the GPIO pin that the Button is connected on.
     */
    public static String getGPIOForButton() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
            case DEVICE_RPI3BP:
                return "BCM23";
            case DEVICE_IMX7D_PICO:
                return "GPIO6_IO14";
            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);
        }
    }

    /**
     * Return the GPIO pin for the Voice Hat DAC trigger.
     */
    public static String getGPIOForDacTrigger() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
            case DEVICE_RPI3BP:
                return "BCM16";
            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);
        }
    }
}
