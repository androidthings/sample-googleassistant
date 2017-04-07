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
import android.os.Bundle;
import android.util.Log;

import com.google.assistant.embedded.v1alpha1.AudioInConfig;
import com.google.assistant.embedded.v1alpha1.AudioOutConfig;
import com.google.assistant.embedded.v1alpha1.ConverseConfig;
import com.google.assistant.embedded.v1alpha1.ConverseRequest;
import com.google.assistant.embedded.v1alpha1.ConverseResponse;
import com.google.assistant.embedded.v1alpha1.EmbeddedAssistantGrpc;
import com.google.auth.oauth2.UserCredentials;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.stub.StreamObserver;

public class AssistantActivity extends Activity {
    private static final String TAG = AssistantActivity.class.getSimpleName();
    private static final AudioInConfig ASSISTANT_AUDIO_REQUEST_CONFIG = AudioInConfig.newBuilder()
                         .setEncoding(AudioInConfig.Encoding.LINEAR16)
                         .setSampleRateHertz(16000)
                         .build();
    private static final AudioOutConfig ASSISTANT_AUDIO_RESPONSE_CONFIG = AudioOutConfig.newBuilder()
                    .setEncoding(AudioOutConfig.Encoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .build();

    File mFile;
    FileOutputStream mFileOutputStream;
    StreamObserver<ConverseRequest> mRequestObserver;
    StreamObserver<ConverseResponse> mResponseObserver = new StreamObserver<ConverseResponse>() {
        @Override
        public void onNext(ConverseResponse value) {
            switch (value.getConverseResponseCase()) {
                case EVENT_TYPE:
                    Log.d(TAG, "converse response event: " + value.getEventType());
                    break;
                case RESULT:
                    if (!value.getResult().getSpokenRequestText().isEmpty()) {
                        Log.i(TAG, "assistant request text: " +
                                value.getResult().getSpokenRequestText());
                    }
                    break;
                case AUDIO_OUT:
                    if (value.getAudioOut().getSampleRateHertz() != 0) {
                        Log.d(TAG, "converse audio rate: " +
                                value.getAudioOut().getSampleRateHertz());
                    }
                    Log.d(TAG, "converse audio size: " + value.getAudioOut().getAudioData().size());
                    byte[] buf = new byte[value.getAudioOut().getAudioData().size()];
                    value.getAudioOut().getAudioData().copyTo(buf, 0);
                    try {
                        mFileOutputStream.write(buf);
                    } catch (IOException e) {
                        Log.e(TAG, "error writing file: ", e);
                    }
                    break;
                case ERROR:
                    Log.e(TAG, "converse response error: " + value.getError());
                    break;
            }
        }

        @Override
        public void onError(Throwable t) {
            Log.e(TAG, "converse error:", t);
        }

        @Override
        public void onCompleted() {
            Log.d(TAG, "converse end");
            try {
                mFileOutputStream.close();
                Log.i(TAG, "assistant response sample rate: " +
                        ASSISTANT_AUDIO_REQUEST_CONFIG.getSampleRateHertz());
                Log.i(TAG, "assistant response file: " + mFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "error closing file:", e);
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        try {
            mFile = File.createTempFile("assistant-out-", ".raw", getCacheDir());
            mFileOutputStream = new FileOutputStream(mFile);
        } catch (IOException e) {
            Log.e(TAG, "error creating file: ", e);
            return;
        }

        ManagedChannel channel =
                ManagedChannelBuilder.forTarget("embedded-assistant.googleapis.com").build();
        UserCredentials cred = new UserCredentials(
                Credentials.CLIENT_ID,
                Credentials.CLIENT_SECRET,
                Credentials.REFRESH_TOKEN
        );
        CallCredentials callCreds  = MoreCallCredentials.from(cred);
        EmbeddedAssistantGrpc.EmbeddedAssistantStub stub =
                EmbeddedAssistantGrpc.newStub(channel)
                                     .withCallCredentials(callCreds);
        mRequestObserver = stub.converse(mResponseObserver);
        mRequestObserver.onNext(ConverseRequest.newBuilder().setConfig(
                ConverseConfig.newBuilder()
                    .setAudioInConfig(ASSISTANT_AUDIO_REQUEST_CONFIG)
                    .setAudioOutConfig(ASSISTANT_AUDIO_RESPONSE_CONFIG)
                    .build()).build());
        Log.i(TAG, "assistant request sample rate: " +
                ASSISTANT_AUDIO_REQUEST_CONFIG.getSampleRateHertz());
        try {
            InputStream is = getResources().openRawResource(R.raw.whattimeisit);
            byte[] buf = new byte[1024];
            while (true) {
                int result = is.read(buf);
                if (result < 0) {
                    break;
                }
                Log.d(TAG, "streaming ConverseRequest: " + result);
                mRequestObserver.onNext(ConverseRequest.newBuilder()
                    .setAudioIn(ByteString.copyFrom(buf, 0, result))
                    .build());
            }
            Log.d(TAG, "finished streaming");
            mRequestObserver.onCompleted();
        } catch (IOException e) {
            Log.e(TAG, "error reading local sound file: ", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
