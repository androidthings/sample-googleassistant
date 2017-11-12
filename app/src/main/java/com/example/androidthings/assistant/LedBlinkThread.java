package com.example.androidthings.assistant;

import com.google.android.things.pio.Gpio;

import java.io.IOException;
import java.util.Random;

public class LedBlinkThread extends Thread {
    private final Gpio mLed;
    private final Random mRandom;
    private boolean mBlinking = false;
    private boolean mClose = false;

    public LedBlinkThread(Gpio led) {
        mLed = led;
        mRandom = new Random();
    }

    public void setBlinking(boolean b) {
        if (mClose) {
            return;
        }
        if (mBlinking != b) {
            mBlinking = b;
            synchronized (this) {
                notify();
            }
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
                    sleep(150+mRandom.nextInt(100));
                    mLed.setValue(true);
                    sleep(150+mRandom.nextInt(100));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        while (!mClose);
    }
}