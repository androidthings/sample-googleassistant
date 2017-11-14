package com.example.androidthings.assistant;

import android.util.Log;

import com.google.android.things.pio.Gpio;

import java.io.IOException;
import java.util.Random;

public class LedBlinkThread extends Thread {
    private static final String TAG = LedBlinkThread.class.getSimpleName();
    private static final long BLINK_INTERVAL = 200;

    private final Gpio mLed;
    private final Random mRandom;
    private boolean mBlinking = false;
    private boolean mClose = false;

    public LedBlinkThread(Gpio led) {
        mLed = led;
        mRandom = new Random();
    }

    public void blink() {
        if (mClose) {
            return;
        }
        mBlinking = true;
        synchronized (this) {
            notify();
        }
    }

    public void close() {
        mClose = true;
        mBlinking = false;
        synchronized (this) {
            notify();
        }
    }

    @Override
    public void run() {
        do {
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            while (mBlinking) {
                mBlinking = false;
                try {
                    mLed.setValue(false);
                    sleep(BLINK_INTERVAL);
                    mLed.setValue(true);
                    sleep(BLINK_INTERVAL);
                } catch (IOException e) {
                    Log.e(TAG, "Error accessing the LED", e);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error while sleeping", e);
                }
            }
        }
        while (!mClose);
    }
}