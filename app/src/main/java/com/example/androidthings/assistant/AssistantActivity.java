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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.example.androidthings.assistant.EmbeddedAssistant.ConversationCallback;
import com.example.androidthings.assistant.EmbeddedAssistant.RequestCallback;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.assistant.embedded.v1alpha1.ConverseResponse.EventType;
import com.google.auth.oauth2.UserCredentials;
import com.google.rpc.Status;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.json.JSONException;

public class AssistantActivity extends Activity implements Button.OnButtonEventListener {
    private static final String TAG = AssistantActivity.class.getSimpleName();

    // Peripheral and drivers constants.
    private static final int BUTTON_DEBOUNCE_DELAY_MS = 20;
    // Default on using the Voice Hat on Raspberry Pi 3.
    private static final boolean USE_VOICEHAT_I2S_DAC = Build.DEVICE.equals(BoardDefaults.DEVICE_RPI3);

    // Audio constants.
    private static final String PREF_CURRENT_VOLUME = "current_volume";
    private static final int SAMPLE_RATE = 16000;
    private static final int DEFAULT_VOLUME = 100;

    // Hardware peripherals.
    private Button mButton;
    private android.widget.Button mButtonWidget;
    private Gpio mLed;
    private Gpio mDacTrigger;

    // List & adapter to store and display the history of Assistant Requests.
    private EmbeddedAssistant mEmbeddedAssistant;
    private ArrayList<String> mAssistantRequests = new ArrayList<>();
    private ArrayAdapter<String> mAssistantRequestsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "starting assistant demo");


        setContentView(R.layout.activity_main);
        ListView assistantRequestsListView = (ListView)findViewById(R.id.assistantRequestsListView);
        mAssistantRequestsAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                        mAssistantRequests);
        assistantRequestsListView.setAdapter(mAssistantRequestsAdapter);
        mButtonWidget = (android.widget.Button) findViewById(R.id.assistantQueryButton);
        mButtonWidget.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mEmbeddedAssistant.startConversation();
            }
        });


        // Audio routing configuration: use default routing.
        AudioDeviceInfo audioInputDevice = null;
        AudioDeviceInfo audioOutputDevice = null;
        if (USE_VOICEHAT_I2S_DAC) {
            audioInputDevice = findAudioDevice(AudioManager.GET_DEVICES_INPUTS, AudioDeviceInfo.TYPE_BUS);
            if (audioInputDevice == null) {
                Log.e(TAG, "failed to find I2S audio input device, using default");
            }
            audioOutputDevice = findAudioDevice(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_BUS);
            if (audioOutputDevice == null) {
                Log.e(TAG, "failed to found I2S audio output device, using default");
            }
        }

        try {
            PeripheralManagerService pioService = new PeripheralManagerService();
            if (USE_VOICEHAT_I2S_DAC) {
                Log.i(TAG, "initializing DAC trigger");
                mDacTrigger = pioService.openGpio(BoardDefaults.getGPIOForDacTrigger());
                mDacTrigger.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            }

            mButton = new Button(BoardDefaults.getGPIOForButton(),
                    Button.LogicState.PRESSED_WHEN_LOW);
            mButton.setDebounceDelay(BUTTON_DEBOUNCE_DELAY_MS);
            mButton.setOnButtonEventListener(this);

            mLed = pioService.openGpio(BoardDefaults.getGPIOForLED());
            mLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mLed.setActiveType(Gpio.ACTIVE_HIGH);
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
                .setAudioInputDevice(audioInputDevice)
                .setAudioOutputDevice(audioOutputDevice)
                .setAudioSampleRate(SAMPLE_RATE)
                .setAudioVolume(initVolume)
                .setRequestCallback(new RequestCallback() {
                    @Override
                    public void onRequestStart() {
                        Log.i(TAG, "starting assistant request, enable microphones");
                        mButtonWidget.setText(R.string.button_listening);
                        mButtonWidget.setEnabled(false);
                    }

                    @Override
                    public void onSpeechRecognition(String utterance) {
                        mAssistantRequestsAdapter.add(utterance);
                    }
                })
                .setConversationCallback(new ConversationCallback() {
                    @Override
                    public void onResponseStarted() {
                        if (mDacTrigger != null) {
                            try {
                                mDacTrigger.setValue(true);
                                mLed.setValue(true);
                            } catch (IOException e) {
                                Log.e(TAG, "error enabling DAC", e);
                            }
                        }
                    }

                    @Override
                    public void onResponseFinished() {
                        if (mDacTrigger != null) {
                            try {
                                mDacTrigger.setValue(false);
                                mLed.setValue(false);
                            } catch (IOException e) {
                                Log.e(TAG, "error disabling DAC", e);
                            }
                        }
                    }

                    @Override
                    public void onConversationEvent(EventType eventType) {
                        Log.d(TAG, "converse response event: " + eventType);
                    }

                    @Override
                    public void onAudioSample(ByteBuffer audioSample) {
                        try {
                            mLed.setValue(!mLed.getValue());
                        } catch (IOException e) {
                            Log.e(TAG, "error toggling LED", e);
                        }
                    }

                    @Override
                    public void onConversationError(Status error) {
                        Log.e(TAG, "converse response error: " + error);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e(TAG, "converse error", throwable);
                        // Reset display
                        mButtonWidget.setText(R.string.button_retry);
                        mButtonWidget.setEnabled(true);
                    }

                    @Override
                    public void onVolumeChanged(int percentage) {
                        Log.i(TAG, "assistant volume changed: " + percentage);
                        // Update our shared preferences
                        Editor editor = PreferenceManager
                                .getDefaultSharedPreferences(AssistantActivity.this)
                                .edit();
                        editor.putInt(PREF_CURRENT_VOLUME, percentage);
                        editor.apply();
                    }

                    @Override
                    public void onConversationFinished() {
                        Log.i(TAG, "assistant conversation finished");
                        mButtonWidget.setText(R.string.button_new_request);
                        mButtonWidget.setEnabled(true);
                    }
                })
                .build();
        mEmbeddedAssistant.connect();
    }

    private AudioDeviceInfo findAudioDevice(int deviceFlag, int deviceType) {
        AudioManager manager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] adis = manager.getDevices(deviceFlag);
        for (AudioDeviceInfo adi : adis) {
            if (adi.getType() == deviceType) {
                return adi;
            }
        }
        return null;
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
        if (mDacTrigger != null) {
            try {
                mDacTrigger.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing voice hat trigger", e);
            }
            mDacTrigger = null;
        }
        mEmbeddedAssistant.destroy();
    }
}
