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

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.assistant.embedded.v1alpha2.AssistConfig;
import com.google.assistant.embedded.v1alpha2.AssistRequest;
import com.google.assistant.embedded.v1alpha2.AssistResponse;
import com.google.assistant.embedded.v1alpha2.AssistResponse.EventType;
import com.google.assistant.embedded.v1alpha2.AudioInConfig;
import com.google.assistant.embedded.v1alpha2.AudioOutConfig;
import com.google.assistant.embedded.v1alpha2.DeviceConfig;
import com.google.assistant.embedded.v1alpha2.DeviceLocation;
import com.google.assistant.embedded.v1alpha2.DialogStateIn;
import com.google.assistant.embedded.v1alpha2.DialogStateOut.MicrophoneMode;
import com.google.assistant.embedded.v1alpha2.EmbeddedAssistantGrpc;
import com.google.assistant.embedded.v1alpha2.ScreenOutConfig;
import com.google.assistant.embedded.v1alpha2.SpeechRecognitionResult;
import com.google.auth.oauth2.UserCredentials;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EmbeddedAssistant {
    private static final String TAG = EmbeddedAssistant.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String ASSISTANT_API_ENDPOINT = "embeddedassistant.googleapis.com";
    private static final int AUDIO_RECORD_BLOCK_SIZE = 1024;

    // Device Actions
    private DeviceConfig mDeviceConfig;

    // Callbacks
    private Handler mRequestHandler;
    private RequestCallback mRequestCallback;
    private Handler mConversationHandler;
    private ConversationCallback mConversationCallback;

    // Assistant Thread and Runnables implementing the push-to-talk functionality.
    private ByteString mConversationState;
    private String mLanguageCode = "en-US";
    private AudioRecord mAudioRecord;
    private DeviceLocation mDeviceLocation;
    private AudioInConfig mAudioInConfig;
    private AudioOutConfig mAudioOutConfig;
    private AudioDeviceInfo mAudioInputDevice;
    private AudioDeviceInfo mAudioOutputDevice;
    private AudioFormat mAudioInputFormat;
    private AudioFormat mAudioOutputFormat;
    private int mAudioInputBufferSize;
    private int mAudioOutputBufferSize;
    private int mVolume = 100; // Default to maximum volume.
    private ScreenOutConfig mScreenOutConfig;

    private MicrophoneMode mMicrophoneMode;
    private HandlerThread mAssistantThread;
    private Handler mAssistantHandler;
    private ArrayList<ByteBuffer> mAssistantResponses = new ArrayList<>();

    // gRPC client and stream observers.
    private int mAudioOutSize; // Tracks the size of audio responses to determine when it ends.
    private EmbeddedAssistantGrpc.EmbeddedAssistantStub mAssistantService;
    private StreamObserver<AssistRequest> mAssistantRequestObserver;
    private StreamObserver<AssistResponse> mAssistantResponseObserver =
            new StreamObserver<AssistResponse>() {
                @Override
                public void onNext(final AssistResponse value) {
                    if (DEBUG) {
                        Log.d(TAG, "Received response: " + value.toString());
                    }
                    if (value.getDeviceAction() != null &&
                        !value.getDeviceAction().getDeviceRequestJson().isEmpty()) {
                        // Iterate through JSON object
                        try {
                            JSONObject deviceAction = new JSONObject(value.getDeviceAction()
                                .getDeviceRequestJson());
                            JSONArray inputs = deviceAction.getJSONArray("inputs");
                            for (int i = 0; i < inputs.length(); i++) {
                                if (inputs.getJSONObject(i).getString("intent").equals(
                                    "action.devices.EXECUTE")) {
                                    JSONArray commands = inputs.getJSONObject(i)
                                        .getJSONObject("payload")
                                        .getJSONArray("commands");
                                    for (int j = 0; j < commands.length(); j++) {
                                        final JSONArray execution = commands.getJSONObject(j)
                                            .getJSONArray("execution");
                                        for (int k = 0; k < execution.length(); k++) {
                                            final int finalK = k;
                                            mConversationHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        mConversationCallback.onDeviceAction(
                                                            execution
                                                                .getJSONObject(finalK)
                                                                .getString("command"),
                                                            execution.getJSONObject(finalK)
                                                                .optJSONObject("params"));
                                                    } catch (JSONException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            });

                                        }
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    if (value.getEventType() == EventType.END_OF_UTTERANCE) {
                        mRequestHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mRequestCallback.onRequestFinish();
                            }
                        });
                        mConversationHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mConversationCallback.onResponseStarted();
                            }
                        });
                    }
                    if (value.getDialogStateOut() != null) {
                        mConversationState = value.getDialogStateOut().getConversationState();
                        if (value.getDialogStateOut().getVolumePercentage() != 0) {
                            final int volumePercentage = value.getDialogStateOut().getVolumePercentage();
                            mVolume = volumePercentage;
                            mConversationHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mConversationCallback.onVolumeChanged(volumePercentage);
                                }
                            });
                        }
                        mRequestHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mRequestCallback.onSpeechRecognition(value.getSpeechResultsList());
                            }
                        });
                        mMicrophoneMode = value.getDialogStateOut().getMicrophoneMode();
                        mConversationCallback.onAssistantResponse(value.getDialogStateOut()
                            .getSupplementalDisplayText());
                    }
                    if (value.getAudioOut() != null) {
                        if (mAudioOutSize <= value.getAudioOut().getSerializedSize()) {
                            mAudioOutSize = value.getAudioOut().getSerializedSize();
                        } else {
                            mAudioOutSize = 0;
                            onCompleted();
                        }
                        final ByteBuffer audioData =
                            ByteBuffer.wrap(value.getAudioOut().getAudioData().toByteArray());
                        mAssistantResponses.add(audioData);
                        mConversationHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mConversationCallback.onAudioSample(audioData);
                            }
                        });
                    }
                    if (value.hasScreenOut()) {
                        mConversationHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mConversationCallback.onAssistantDisplayOut(
                                    value.getScreenOut().getData().toStringUtf8());
                            }
                        });
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    mConversationHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mConversationCallback.onError(t);
                        }
                    });
                }

                @Override
                public void onCompleted() {
                    // create a new AudioTrack to workaround audio routing issues.
                    AudioTrack audioTrack = new AudioTrack.Builder()
                            .setAudioFormat(mAudioOutputFormat)
                            .setBufferSizeInBytes(mAudioOutputBufferSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build();
                    if (mAudioOutputDevice != null) {
                        audioTrack.setPreferredDevice(mAudioOutputDevice);
                    }
                    audioTrack.setVolume(AudioTrack.getMaxVolume() * mVolume / 100.0f);
                    audioTrack.play();
                    mConversationHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mConversationCallback.onResponseStarted();
                        }
                    });
                    for (ByteBuffer audioData : mAssistantResponses) {
                        final ByteBuffer buf = audioData;
                        mConversationHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mConversationCallback.onAudioSample(buf);
                            }
                        });
                        audioTrack.write(buf, buf.remaining(),
                                AudioTrack.WRITE_BLOCKING);
                    }
                    mAssistantResponses.clear();
                    audioTrack.stop();
                    audioTrack.release();

                    mConversationHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mConversationCallback.onResponseFinished();
                        }
                    });
                    if (mMicrophoneMode == MicrophoneMode.DIALOG_FOLLOW_ON) {
                        // Automatically start a new request
                        startConversation();
                    } else {
                        // The conversation is done
                        mConversationHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mConversationCallback.onConversationFinished();
                            }
                        });
                    }
                }
            };

    private Runnable mStreamAssistantRequest = new Runnable() {
        @Override
        public void run() {
            ByteBuffer audioData = ByteBuffer.allocateDirect(AUDIO_RECORD_BLOCK_SIZE);
            int result = mAudioRecord.read(audioData, audioData.capacity(),
                    AudioRecord.READ_BLOCKING);
            if (result < 0) {
                return;
            }
            mRequestHandler.post(new Runnable() {
                @Override
                public void run() {
                    mRequestCallback.onAudioRecording();
                }
            });
            mAssistantRequestObserver.onNext(AssistRequest.newBuilder()
                    .setAudioIn(ByteString.copyFrom(audioData))
                    .build());
            mAssistantHandler.post(mStreamAssistantRequest);
        }
    };

    private UserCredentials mUserCredentials;

    private EmbeddedAssistant() {}

    /**
     * Initializes the Assistant.
     */
    public void connect() {
        mAssistantThread = new HandlerThread("assistantThread");
        mAssistantThread.start();
        mAssistantHandler = new Handler(mAssistantThread.getLooper());

        ManagedChannel channel = ManagedChannelBuilder.forTarget(ASSISTANT_API_ENDPOINT).build();
        mAssistantService = EmbeddedAssistantGrpc.newStub(channel)
                .withCallCredentials(MoreCallCredentials.from(mUserCredentials));
    }

    /**
     * Starts a request to the Assistant.
     */
    public void startConversation() {
        mAudioRecord.startRecording();
        mRequestHandler.post(new Runnable() {
            @Override
            public void run() {
                mRequestCallback.onRequestStart();
            }
        });
        mAssistantHandler.post(new Runnable() {
            @Override
            public void run() {
                mAssistantRequestObserver = mAssistantService.assist(mAssistantResponseObserver);
                AssistConfig.Builder assistConfigBuilder = AssistConfig.newBuilder()
                        .setAudioInConfig(mAudioInConfig)
                        .setAudioOutConfig(mAudioOutConfig)
                        .setDeviceConfig(mDeviceConfig);
                if (mScreenOutConfig != null) {
                    assistConfigBuilder.setScreenOutConfig(mScreenOutConfig);
                }
                DialogStateIn.Builder dialogStateInBuilder = DialogStateIn.newBuilder();
                if (mConversationState != null) {
                    dialogStateInBuilder.setConversationState(mConversationState);
                }
                if (mDeviceLocation != null) {
                    dialogStateInBuilder.setDeviceLocation(mDeviceLocation);
                }
                dialogStateInBuilder.setLanguageCode(mLanguageCode);
                assistConfigBuilder.setDialogStateIn(dialogStateInBuilder.build());
                mAssistantRequestObserver.onNext(
                        AssistRequest.newBuilder()
                                .setConfig(assistConfigBuilder.build())
                                .build());
            }
        });
        mAssistantHandler.post(mStreamAssistantRequest);
    }

    public void startConversation(final String inputQuery) {
        mRequestCallback.onRequestStart();
        mAssistantHandler.post(new Runnable() {
            @Override
            public void run() {
                mAssistantRequestObserver = mAssistantService.assist(mAssistantResponseObserver);
                AssistConfig.Builder assistConfigBuilder = AssistConfig.newBuilder()
                        .setTextQuery(inputQuery)
                        .setAudioOutConfig(mAudioOutConfig)
                        .setDeviceConfig(mDeviceConfig)
                        .setScreenOutConfig(mScreenOutConfig);
                DialogStateIn.Builder dialogStateInBuilder = DialogStateIn.newBuilder();
                if (mConversationState != null) {
                    dialogStateInBuilder.setConversationState(mConversationState);
                }
                if (mDeviceLocation != null) {
                    dialogStateInBuilder.setDeviceLocation(mDeviceLocation);
                }
                dialogStateInBuilder.setLanguageCode(mLanguageCode);
                assistConfigBuilder.setDialogStateIn(dialogStateInBuilder.build());
                mAssistantRequestObserver.onNext(
                    AssistRequest.newBuilder()
                        .setConfig(assistConfigBuilder.build())
                        .build());
            }
        });
    }

    /**
     * Manually ends a conversation with the Assistant.
     */
    public void stopConversation() {
        mAssistantHandler.post(new Runnable() {
            @Override
            public void run() {
                mAssistantHandler.removeCallbacks(mStreamAssistantRequest);
                if (mAssistantRequestObserver != null) {
                    mAssistantRequestObserver.onCompleted();
                    mAssistantRequestObserver = null;
                }
            }
        });

        mAudioRecord.stop();
        mConversationHandler.post(new Runnable() {
            @Override
            public void run() {
                mConversationCallback.onConversationFinished();
            }
        });
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TEXT, HTML})
    public @interface ResponseFormat {}
    public static final int TEXT = 0;
    public static final int HTML = 1;

    /**
     * Set desired assistant response format.
     */
    public void setResponseFormat(@ResponseFormat int format) {
        mScreenOutConfig = ScreenOutConfig.newBuilder()
                .setScreenMode(format == HTML
                        ? ScreenOutConfig.ScreenMode.PLAYING
                        : ScreenOutConfig.ScreenMode.SCREEN_MODE_UNSPECIFIED)
                .build();
    }

    /**
     * Removes callbacks and exists the Assistant service. This should be called when an activity is
     * closing to safely quit the Assistant service.
     */
    public void destroy() {
        mAssistantHandler.post(new Runnable() {
            @Override
            public void run() {
                mAssistantHandler.removeCallbacks(mStreamAssistantRequest);
            }
        });
        mAssistantThread.quitSafely();
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord = null;
        }
    }

    /**
     * Generates access tokens for the Assistant based on a credentials JSON file.
     *
     * @param context Application context
     * @param resourceId The resource that contains the project credentials
     *
     * @return A {@link UserCredentials} object which can be used by the Assistant.
     * @throws IOException If the resource does not exist.
     * @throws JSONException If the resource is incorrectly formatted.
     */
    public static UserCredentials generateCredentials(Context context, int resourceId)
            throws IOException, JSONException {
        return Credentials.fromResource(context, resourceId);
    }

    /**
     * Used to build an AssistantManager object.
     */
    public static class Builder {
        private EmbeddedAssistant mEmbeddedAssistant;
        private int mSampleRate;
        private String mDeviceModelId;
        private String mDeviceInstanceId;

        /**
         * Creates a Builder.
         */
        public Builder() {
            mEmbeddedAssistant = new EmbeddedAssistant();
        }

        /**
         * Sets a preferred {@link AudioDeviceInfo} device for input.
         *
         * @param device The preferred audio device to acquire audio from.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setAudioInputDevice(AudioDeviceInfo device) {
            mEmbeddedAssistant.mAudioInputDevice = device;
            return this;
        }

        /**
         * Sets a preferred {@link AudioDeviceInfo} device for output.
         *
         * param device The preferred audio device to route audio to.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setAudioOutputDevice(AudioDeviceInfo device) {
            mEmbeddedAssistant.mAudioOutputDevice = device;
            return this;
        }

        /**
         * Sets a {@link RequestCallback}, which is when a request is being made to the Assistant.
         *
         * @param requestCallback The methods that will run during a request.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setRequestCallback(RequestCallback requestCallback) {
            setRequestCallback(requestCallback, null);
            return this;
        }

        /**
         * Sets a {@link RequestCallback}, which is when a request is being made to the Assistant.
         *
         * @param requestCallback The methods that will run during a request.
         * @param requestHandler Handler used to dispatch the callback.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setRequestCallback(RequestCallback requestCallback,
                                          @Nullable Handler requestHandler) {
            if (requestHandler == null) {
                requestHandler = new Handler();
            }
            mEmbeddedAssistant.mRequestCallback = requestCallback;
            mEmbeddedAssistant.mRequestHandler = requestHandler;
            return this;
        }

        /**
         * Sets a {@link ConversationCallback}, which is when a response is being given from the
         * Assistant.
         *
         * @param responseCallback The methods that will run during a response.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setConversationCallback(ConversationCallback responseCallback) {
            setConversationCallback(responseCallback, null);
            return this;
        }

        /**
         * Sets a {@link ConversationCallback}, which is when a response is being given from the
         * Assistant.
         *
         * @param responseCallback The methods that will run during a response.
         * @param responseHandler Handler used to dispatch the callback.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setConversationCallback(ConversationCallback responseCallback,
                                               @Nullable Handler responseHandler) {
            if (responseHandler == null) {
                responseHandler = new Handler();
            }
            mEmbeddedAssistant.mConversationCallback = responseCallback;
            mEmbeddedAssistant.mConversationHandler = responseHandler;
            return this;
        }

        /**
         * Sets the credentials for the user.
         *
         * @param userCredentials Credentials generated by
         *    {@link EmbeddedAssistant#generateCredentials(Context, int)}.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setCredentials(UserCredentials userCredentials) {
            mEmbeddedAssistant.mUserCredentials = userCredentials;
            return this;
        }

        /**
         * Sets the audio sampling rate for input and output streams
         *
         * @param sampleRate The audio sample rate
         * @return Returns this builder to allow for chaining.
         */
        public Builder setAudioSampleRate(int sampleRate) {
            mSampleRate = sampleRate;
            return this;
        }

        /**
         * Sets the volume for the Assistant response
         *
         * @param volume The audio volume in the range 0 - 100.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setAudioVolume(int volume) {
            mEmbeddedAssistant.mVolume = volume;
            return this;
        }

        /**
         * Sets the model id for each Assistant request.
         *
         * @param deviceModelId The device model id.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setDeviceModelId(String deviceModelId) {
            mDeviceModelId = deviceModelId;
            return this;
        }

        /**
         * Sets the instance id for each Assistant request.
         *
         * @param deviceInstanceId The device instance id.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setDeviceInstanceId(String deviceInstanceId) {
            mDeviceInstanceId = deviceInstanceId;
            return this;
        }

        /**
         * Sets language code of the request using IETF BCP 47 syntax.
         * See <a href='https://tools.ietf.org/html/bcp47'>for the documentation</a>.
         * For example: "en-US".
         *
         * @param languageCode Code for the language. Only Assistant-supported languages are valid.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setLanguageCode(String languageCode) {
            mEmbeddedAssistant.mLanguageCode = languageCode;
            return this;
        }

        public Builder setDeviceLocation(DeviceLocation deviceLocation) {
            mEmbeddedAssistant.mDeviceLocation = deviceLocation;
            return this;
        }

        /**
         * Returns an AssistantManager if all required parameters have been supplied.
         *
         * @return An inactive AssistantManager. Call {@link EmbeddedAssistant#connect()} to start
         * it.
         */
        public EmbeddedAssistant build() {
            if (mEmbeddedAssistant.mRequestCallback == null) {
                throw new NullPointerException("There must be a defined RequestCallback");
            }
            if (mEmbeddedAssistant.mConversationCallback == null) {
                throw new NullPointerException("There must be a defined ConversationCallback");
            }
            if (mEmbeddedAssistant.mUserCredentials == null) {
                throw new NullPointerException("There must be provided credentials");
            }
            if (mSampleRate == 0) {
                throw new NullPointerException("There must be a defined sample rate");
            }
            final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

            // Construct audio configurations.
            mEmbeddedAssistant.mAudioInConfig = AudioInConfig.newBuilder()
                    .setEncoding(AudioInConfig.Encoding.LINEAR16)
                    .setSampleRateHertz(mSampleRate)
                    .build();
            mEmbeddedAssistant.mAudioOutConfig = AudioOutConfig.newBuilder()
                    .setEncoding(AudioOutConfig.Encoding.LINEAR16)
                    .setSampleRateHertz(mSampleRate)
                    .setVolumePercentage(mEmbeddedAssistant.mVolume)
                    .build();

            // Initialize Audio framework parameters.
            mEmbeddedAssistant.mAudioInputFormat = new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(audioEncoding)
                    .setSampleRate(mSampleRate)
                    .build();
            mEmbeddedAssistant.mAudioInputBufferSize = AudioRecord.getMinBufferSize(
                    mEmbeddedAssistant.mAudioInputFormat.getSampleRate(),
                    mEmbeddedAssistant.mAudioInputFormat.getChannelMask(),
                    mEmbeddedAssistant.mAudioInputFormat.getEncoding());
            mEmbeddedAssistant.mAudioOutputFormat = new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(audioEncoding)
                    .setSampleRate(mSampleRate)
                    .build();
            mEmbeddedAssistant.mAudioOutputBufferSize = AudioTrack.getMinBufferSize(
                    mEmbeddedAssistant.mAudioOutputFormat.getSampleRate(),
                    mEmbeddedAssistant.mAudioOutputFormat.getChannelMask(),
                    mEmbeddedAssistant.mAudioOutputFormat.getEncoding());

            // create new AudioRecord to workaround audio routing issues.
            mEmbeddedAssistant.mAudioRecord = new AudioRecord.Builder()
                    .setAudioSource(AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(mEmbeddedAssistant.mAudioInputFormat)
                    .setBufferSizeInBytes(mEmbeddedAssistant.mAudioInputBufferSize)
                    .build();
            if (mEmbeddedAssistant.mAudioInputDevice != null) {
                boolean result = mEmbeddedAssistant.mAudioRecord.setPreferredDevice(
                        mEmbeddedAssistant.mAudioInputDevice);
                if (!result) {
                    Log.e(TAG, "failed to set preferred input device");
                }
            }

            // Construct DeviceConfig
            mEmbeddedAssistant.mDeviceConfig = DeviceConfig.newBuilder()
                .setDeviceId(mDeviceInstanceId)
                .setDeviceModelId(mDeviceModelId)
                .build();

            // Construct default ScreenOutConfig
            mEmbeddedAssistant.mScreenOutConfig = ScreenOutConfig.newBuilder()
                    .setScreenMode(ScreenOutConfig.ScreenMode.SCREEN_MODE_UNSPECIFIED)
                    .build();

            return mEmbeddedAssistant;
        }
    }

    /**
     * Callback for methods during a request to the Assistant.
     */
    public static abstract class RequestCallback {

        /**
         * Called when a request is first made.
         */
        public void onRequestStart() {}

        /**
         * Called when a request has completed.
         */
        public void onRequestFinish() {}

        /**
         * Called when audio is being recording. This may be called multiple times during a single
         * request.
         */
        public void onAudioRecording() {}

        /**
         * Called when the request is complete and the Assistant returns the user's speech-to-text.
         */
        public void onSpeechRecognition(List<SpeechRecognitionResult> results) {}
    }

    /**
     * Callback for methods during a conversation from the Assistant.
     */
    public static abstract class ConversationCallback {

        /**
         * Called when the user's voice query ends and the response from the Assistant is about to
         * start a response.
         */
        public void onResponseStarted() {}

        /**
         * Called when the Assistant's response is complete.
         */
        public void onResponseFinished() {}

        /**
         * Called when audio is being played. This may be called multiple times during a single
         * response. The audio will play using the AudioTrack, although this method may be used
         * to provide auxiliary effects.
         *
         * @param audioSample The raw audio sample from the Assistant
         */
        public void onAudioSample(ByteBuffer audioSample) {}

        /**
         * Called when an error occurs during the response
         *
         * @param throwable A {@link Throwable} which contains information about the response error.
         */
        public void onError(Throwable throwable) {}

        /**
         * Called when the user requests to change the Assistant's volume.
         *
         * @param percentage The desired volume as a percentage of intensity, in the range 0 - 100.
         */
        public void onVolumeChanged(int percentage) {}

        /**
         * Called when the response contains a DeviceAction.
         *
         * @param intentName The name of the intent to execute.
         * @param parameters A JSONObject containing parameters related to this intent.
         */
        public void onDeviceAction(String intentName, JSONObject parameters) {}

        /**
         * Called when the response contains supplemental display text from the Assistant.
         *
         * @param response Supplemental display text.
         */
        public void onAssistantResponse(String response) {}

        /**
         * Called when the response contains HTML output from the Assistant.
         *
         * @param html HTML data showing a rich response
         */
        public void onAssistantDisplayOut(String html) {}

        /**
         * Called when the entire conversation is finished.
         */
        public void onConversationFinished() {}
    }
}