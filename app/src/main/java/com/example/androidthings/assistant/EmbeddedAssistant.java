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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.os.HandlerThread;
import com.google.assistant.embedded.v1alpha1.AudioInConfig;
import com.google.assistant.embedded.v1alpha1.AudioInConfig.Encoding;
import com.google.assistant.embedded.v1alpha1.AudioOutConfig;
import com.google.assistant.embedded.v1alpha1.ConverseConfig;
import com.google.assistant.embedded.v1alpha1.ConverseRequest;
import com.google.assistant.embedded.v1alpha1.ConverseResponse;
import com.google.assistant.embedded.v1alpha1.ConverseResponse.EventType;
import com.google.assistant.embedded.v1alpha1.ConverseResult.MicrophoneMode;
import com.google.assistant.embedded.v1alpha1.ConverseState;
import com.google.assistant.embedded.v1alpha1.EmbeddedAssistantGrpc;
import com.google.auth.oauth2.UserCredentials;
import com.google.protobuf.ByteString;
import com.google.rpc.Status;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.json.JSONException;

public class EmbeddedAssistant {
    private static final String TAG = EmbeddedAssistant.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String ASSISTANT_API_ENDPOINT = "embeddedassistant.googleapis.com";
    private static final int AUDIO_RECORD_BLOCK_SIZE = 1024;

    // Callbacks
    private RequestCallback mRequestCallback;
    private ConversationCallback mConversationCallback;

    // Assistant Thread and Runnables implementing the push-to-talk functionality.
    private ByteString mConversationState;
    private AudioInConfig mAudioInConfig;
    private AudioOutConfig mAudioOutConfig;
    private AudioTrack mAudioTrack;
    private AudioRecord mAudioRecord;
    private int mVolume = 100; // Default to maximum volume.

    private MicrophoneMode mMicrophoneMode;
    private HandlerThread mAssistantThread;
    private Handler mAssistantHandler;

    // gRPC client and stream observers.
    private int mAudioOutSize; // Tracks the size of audio responses to determine when it ends.
    private EmbeddedAssistantGrpc.EmbeddedAssistantStub mAssistantService;
    private StreamObserver<ConverseRequest> mAssistantRequestObserver;
    private StreamObserver<ConverseResponse> mAssistantResponseObserver =
            new StreamObserver<ConverseResponse>() {
                @Override
                public void onNext(ConverseResponse value) {
                    switch (value.getConverseResponseCase()) {
                        case EVENT_TYPE:
                            mConversationCallback.onConversationEvent(value.getEventType());
                            if (value.getEventType() == EventType.END_OF_UTTERANCE) {
                                mConversationCallback.onResponseStarted();
                            }
                            break;
                        case RESULT:
                            // Update state.
                            mConversationState = value.getResult().getConversationState();
                            // Update volume.
                            if (value.getResult().getVolumePercentage() != 0) {
                                int volumePercentage = value.getResult().getVolumePercentage();
                                mVolume = volumePercentage;
                                mAudioTrack.setVolume(AudioTrack.getMaxVolume()
                                        * volumePercentage / 100.0f);
                                mConversationCallback.onVolumeChanged(volumePercentage);
                            }
                            if (value.getResult().getSpokenRequestText() != null &&
                                    !value.getResult().getSpokenRequestText().isEmpty()) {
                                mRequestCallback.onSpeechRecognition(value.getResult()
                                        .getSpokenRequestText());
                            }
                            // Update microphone mode.
                            mMicrophoneMode = value.getResult().getMicrophoneMode();
                            break;
                        case AUDIO_OUT:
                            if (mAudioOutSize <= value.getAudioOut().getSerializedSize()) {
                                mAudioOutSize = value.getAudioOut().getSerializedSize();
                            } else {
                                mAudioOutSize = 0;
                                onCompleted();
                            }
                            final ByteBuffer audioData =
                                    ByteBuffer.wrap(value.getAudioOut().getAudioData().toByteArray());
                            mAudioTrack.write(audioData, audioData.remaining(),
                                    AudioTrack.WRITE_BLOCKING);
                            mConversationCallback.onAudioSample(audioData);
                            break;
                        case ERROR:
                            mConversationCallback.onConversationError(value.getError());
                            break;
                    }
                }

                @Override
                public void onError(Throwable t) {
                    mConversationCallback.onError(t);
                }

                @Override
                public void onCompleted() {
                    mConversationCallback.onResponseFinished();
                    if (mMicrophoneMode == MicrophoneMode.DIALOG_FOLLOW_ON) {
                        // Automatically start a new request
                        startConversation();
                    } else {
                        // The conversation is done
                        mConversationCallback.onConversationFinished();
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
            mRequestCallback.onAudioRecording();
            mAssistantRequestObserver.onNext(ConverseRequest.newBuilder()
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
        mRequestCallback.onRequestStart();
        mAssistantHandler.post(new Runnable() {
            @Override
            public void run() {
                mAssistantRequestObserver = mAssistantService.converse(mAssistantResponseObserver);
                ConverseConfig.Builder converseConfigBuilder = ConverseConfig.newBuilder()
                        .setAudioInConfig(mAudioInConfig)
                        .setAudioOutConfig(mAudioOutConfig);
                if (mConversationState != null) {
                    converseConfigBuilder.setConverseState(ConverseState.newBuilder()
                            .setConversationState(mConversationState)
                            .build());
                }
                mAssistantRequestObserver.onNext(
                        ConverseRequest.newBuilder()
                                .setConfig(converseConfigBuilder.build())
                                .build());
            }
        });
        mAssistantHandler.post(mStreamAssistantRequest);
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
        mAudioTrack.play();
        mConversationCallback.onConversationFinished();
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
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack = null;
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

        /**
         * Creates a Builder.
         */
        public Builder() {
            mEmbeddedAssistant = new EmbeddedAssistant();
        }

        /**
         * Sets a {@link RequestCallback}, which is when a request is being made to the Assistant.
         *
         * @param requestCallback The methods that will run during a request.
         * @return Returns this builder to allow for chaining.
         */
        public Builder setRequestCallback(RequestCallback requestCallback) {
            mEmbeddedAssistant.mRequestCallback = requestCallback;
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
            mEmbeddedAssistant.mConversationCallback = responseCallback;
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

            // Construct AudioRecord & AudioTrack
            AudioFormat audioFormatOutputMono = new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(audioEncoding)
                    .setSampleRate(mSampleRate)
                    .build();
            int outputBufferSize = AudioTrack.getMinBufferSize(audioFormatOutputMono.getSampleRate(),
                    audioFormatOutputMono.getChannelMask(),
                    audioFormatOutputMono.getEncoding());
            mEmbeddedAssistant.mAudioTrack = new AudioTrack.Builder()
                    .setAudioFormat(audioFormatOutputMono)
                    .setBufferSizeInBytes(outputBufferSize)
                    .build();
            mEmbeddedAssistant.mAudioTrack.setVolume(mEmbeddedAssistant.mVolume *
                    AudioTrack.getMaxVolume() / 100.0f);
            mEmbeddedAssistant.mAudioTrack.play();

            AudioFormat audioFormatInputMono = new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(audioEncoding)
                    .setSampleRate(mSampleRate)
                    .build();
            int inputBufferSize = AudioRecord.getMinBufferSize(audioFormatInputMono.getSampleRate(),
                    audioFormatInputMono.getChannelMask(),
                    audioFormatInputMono.getEncoding());
            mEmbeddedAssistant.mAudioRecord = new AudioRecord.Builder()
                    .setAudioSource(AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(audioFormatInputMono)
                    .setBufferSizeInBytes(inputBufferSize)
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
         * Called when audio is being recording. This may be called multiple times during a single
         * request.
         */
        public void onAudioRecording() {}

        /**
         * Called when the request is complete and the Assistant returns the user's speech-to-text.
         */
        public void onSpeechRecognition(String utterance) {}
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
         * Called when a converse event occurs.
         *
         * @param eventType An {@link EventType} object which contains information about the type of
         *    event.
         */
        public void onConversationEvent(EventType eventType) {}

        /**
         * Called when audio is being played. This may be called multiple times during a single
         * response. The audio will play using the AudioTrack, although this method may be used
         * to provide auxiliary effects.
         *
         * @param audioSample The raw audio sample from the Assistant
         */
        public void onAudioSample(ByteBuffer audioSample) {}

        /**
         * Called when there is an error with conversing.
         *
         * @param error A {@link Status} object which contains information about the converse error.
         */
        public void onConversationError(Status error) {}

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
         * Called when the entire conversation is finished.
         */
        public void onConversationFinished() {}
    }
}