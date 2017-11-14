/*
 * Copyright 2017, The Android Open Source Project
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

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.example.androidthings.assistant.EmbeddedAssistant.ConversationCallback;
import com.example.androidthings.assistant.EmbeddedAssistant.RequestCallback;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.voicehat.VoiceHat;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.assistant.embedded.v1alpha1.ConverseResponse.EventType;
import com.google.auth.oauth2.UserCredentials;
import com.google.rpc.Status;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;

public class AssistantActivity extends Activity implements Button.OnButtonEventListener {
    private static final String TAG = AssistantActivity.class.getSimpleName();

    // Peripheral and drivers constants.
    private static final boolean AUDIO_USE_I2S_VOICEHAT_IF_AVAILABLE = true;
    private static final int BUTTON_DEBOUNCE_DELAY_MS = 20;

    // Audio constants.
    private static final String PREF_CURRENT_VOLUME = "current_volume";
    private static final int SAMPLE_RATE = 16000;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int DEFAULT_VOLUME = 100;

    private static final AudioFormat AUDIO_FORMAT_STEREO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();
    // Hardware peripherals.
    private VoiceHat mVoiceHat;
    private Button mButton;
    private Gpio mLed;

    private Handler mMainHandler;

    // List & adapter to store and display the history of Assistant Requests.
    private EmbeddedAssistant mEmbeddedAssistant;
    private ArrayList<String> mAssistantRequests = new ArrayList<>();
    private ArrayAdapter<String> mAssistantRequestsAdapter;
    private LedBlinkThread mLedBlinkThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "starting assistant demo");

        setContentView(R.layout.activity_main);
        ListView assistantRequestsListView = (ListView) findViewById(R.id.assistantRequestsListView);
        mAssistantRequestsAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                        mAssistantRequests);
        assistantRequestsListView.setAdapter(mAssistantRequestsAdapter);
        mMainHandler = new Handler(getMainLooper());

        try {
            if (AUDIO_USE_I2S_VOICEHAT_IF_AVAILABLE) {
                PeripheralManagerService pioService = new PeripheralManagerService();
                List<String> i2sDevices = pioService.getI2sDeviceList();
                if (i2sDevices.size() > 0) {
                    try {
                        Log.i(TAG, "creating voice hat driver");
                        mVoiceHat = new VoiceHat(
                                BoardDefaults.getI2SDeviceForVoiceHat(),
                                BoardDefaults.getGPIOForVoiceHatTrigger(),
                                AUDIO_FORMAT_STEREO
                        );
                        mVoiceHat.registerAudioInputDriver();
                        mVoiceHat.registerAudioOutputDriver();
                    } catch (IllegalStateException e) {
                        Log.w(TAG, "Unsupported board, falling back on default audio device:", e);
                    }
                }
            }
            mButton = new Button(BoardDefaults.getGPIOForButton(),
                    Button.LogicState.PRESSED_WHEN_LOW);
            mButton.setDebounceDelay(BUTTON_DEBOUNCE_DELAY_MS);
            mButton.setOnButtonEventListener(this);

            PeripheralManagerService pioService = new PeripheralManagerService();
            mLed = pioService.openGpio(BoardDefaults.getGPIOForLED());
            mLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mLed.setActiveType(Gpio.ACTIVE_LOW);

            if (mLed != null) {
                mLedBlinkThread = new LedBlinkThread(mLed);
                mLedBlinkThread.start();
            }
        } catch (IOException e) {
            Log.e(TAG, "error configuring peripherals:", e);
            return;
        }

        // Set volume from preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int initVolume = preferences.getInt(PREF_CURRENT_VOLUME, DEFAULT_VOLUME);
        Log.i(TAG, "setting audio track volume to: " + initVolume);

        UserCredentials userCredentials = null;
        try {
            userCredentials =
                    EmbeddedAssistant.generateCredentials(this, R.raw.credentials);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "error getting user credentials", e);
        }
        mEmbeddedAssistant = new EmbeddedAssistant.Builder()
                .setCredentials(userCredentials)
                .setAudioSampleRate(SAMPLE_RATE)
                .setAudioVolume(initVolume)
                .setRequestCallback(new RequestCallback() {
                    @Override
                    public void onRequestStart() {
                        Log.i(TAG, "starting assistant request, enable microphones");
                    }

                    @Override
                    public void onSpeechRecognition(String utterance) {
                        Log.i(TAG, "assistant request text: " + utterance);
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mAssistantRequestsAdapter.add(utterance);
                            }
                        });
                    }
                })
                .setConversationCallback(new ConversationCallback() {
                    @Override
                    public void onConversationEvent(EventType eventType) {
                        Log.d(TAG, "converse response event: " + eventType);
                        if (EventType.END_OF_UTTERANCE.equals(eventType)) {
                            if (mLed != null) {
                                try {
                                    mLed.setValue(true);
                                } catch (IOException e) {
                                    Log.e(TAG, "error turning off LED:", e);
                                }
                            }
                        }
                    }

                    @Override
                    public void onAudioSample(ByteBuffer audioSample) {
                        Log.i(TAG, "onAudioSample");
                        if (mLedBlinkThread != null) {
                            mLedBlinkThread.blink();
                        }
                    }

                    @Override
                    public void onConversationError(Status error) {
                        Log.e(TAG, "converse response error: " + error);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e(TAG, "converse error:", throwable);
                    }

                    @Override
                    public void onVolumeChanged(int percentage) {
                        Log.i(TAG, "assistant volume changed: " + percentage);
                        // Update our shared preferences
                        SharedPreferences.Editor editor = PreferenceManager
                                .getDefaultSharedPreferences(AssistantActivity.this)
                                .edit();
                        editor.putInt(PREF_CURRENT_VOLUME, percentage);
                        editor.apply();
                    }

                    @Override
                    public void onConversationFinished() {
                        Log.i(TAG, "assistant conversation finished");
                        if (mLed != null) {
                            try {
                                mLed.setValue(true);
                            } catch (IOException e) {
                                Log.e(TAG, "error turning off LED:", e);
                            }
                        }
                    }
                })
                .build();
        mEmbeddedAssistant.connect();
    }

    @Override
    public void onButtonEvent(Button button, boolean pressed) {
        try {
            if (mLed != null) {
                mLed.setValue(pressed);
            }
        } catch (IOException e) {
            Log.d(TAG, "error toggling LED:", e);
        }
        if (pressed) {
            mEmbeddedAssistant.startConversation();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "destroying assistant demo");
        mLedBlinkThread.close();

        if (mLed != null) {
            try {
                mLed.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing LED", e);
            }
            mLed = null;
        }

        if (mButton != null) {
            try {
                mButton.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing button", e);
            }
            mButton = null;
        }
        if (mVoiceHat != null) {
            try {
                mVoiceHat.unregisterAudioOutputDriver();
                mVoiceHat.unregisterAudioInputDriver();
                mVoiceHat.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing voice hat driver", e);
            }
            mVoiceHat = null;
        }
        mEmbeddedAssistant.destroy();
    }
}
