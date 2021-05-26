# Google Assistant SDK for devices - Android Things

This sample shows how to call the [Google Assistant Service](https://developers.google.com/assistant/sdk/guides/service/python/)
from Android Things using gRPC. It records a spoken request from the
connected microphones, sends it to the Google Assistant API and plays
back the Assistant's spoken response on the connected speaker.

> **Note:** The Android Things Console will be turned down for non-commercial
> use on January 5, 2022. For more details, see the
> [FAQ page](https://developer.android.com/things/faq).

## Pre-requisites

- Android Studio 2.2+.
- Android Things compatible board.
- [AIY Projects Voice Kit][voice-kit] or supported [microphone][mic] and [speaker][speaker] (See [audio configuration](#audio-configuration)).
- [Google API Console Project][console].

## Run the sample

1. Create or open a project in the [Actions Console](http://console.actions.google.com)
1. Follow the instructions to [register a device model](https://developers.google.com/assistant/sdk/guides/service/python/embed/register-device)
  1. Download `client_secret_XXXX.json`
  1. Configure the [OAuth consent screen](https://console.developers.google.com/apis/credentials/consent) for your project
1. Install the [`google-oauthlib-tool`](https://github.com/GoogleCloudPlatform/google-auth-library-python-oauthlib) in a [Python 3](https://www.python.org/downloads/) virtual environment:

```
python3 -m venv env
env/bin/python -m pip install --upgrade pip setuptools
env/bin/pip install --upgrade google-auth-oauthlib[tool]
source env/bin/activate
```

- Use the [`google-oauthlib-tool`][google-oauthlib-tool] to generate user credentials:

```bash
google-oauthlib-tool --client-secrets client_secret_XXXX.json \
                     --credentials app/src/main/res/raw/credentials.json \
                     --scope https://www.googleapis.com/auth/assistant-sdk-prototype \
                     --save
```
- Make sure to set the [Activity Controls][set-activity-controls] for the Google Account using the application.
- On the first install, grant the sample required permissions for audio and internet access:

```bash
./gradlew assembleDebug
adb install -g app/build/outputs/apk/debug/app-debug.apk
```

- On Android Studio, click on the "Run" button or on the command line, type:

```bash
./gradlew installDebug
adb shell am start com.example.androidthings.assistant/.AssistantActivity
```
- Try the assistant demo:

  - Press the button: recording starts.
  - Ask a question in the microphone. After your question is finished, recording will end.
  - The Google Assistant answer should playback on the speaker.

## Audio Configuration

By default the sample routes audio to the I2S Voice Hat on Raspberry Pi 3 and default audio on other boards (on-board Line out or HDMI/USB if connected).

You can change those mappings by changing the `USE_VOICEHAT_I2S_DAC`
constant or replacing the audio configuration in the `onCreate` method of [AssistantActivity](https://github.com/androidthings/sample-googleassistant/blob/master/app/src/main/java/com/example/androidthings/assistant/AssistantActivity.java) with one of the following:

```Java
// Force using on-board Line out:
audioInputDevice = findAudioDevice(AudioManager.GET_DEVICES_INPUTS, AudioDeviceInfo.TYPE_BUILTIN_MIC);
audioOutputDevice = findAudioDevice(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);

// Force using USB:
audioInputDevice = findAudioDevice(AudioManager.GET_DEVICES_INPUTS, AudioDeviceInfo.TYPE_USB_DEVICE);
audioOutputDevice = findAudioDevice(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_USB_DEVICE);

// Force using I2S:
audioInputDevice = findAudioDevice(AudioManager.GET_DEVICES_INPUTS, AudioDeviceInfo.TYPE_BUS);
audioOutputDevice = findAudioDevice(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_BUS);
```

## Device Actions
With Device Actions, you can control hardware connected to your device.
In this sample, you can turn on and off the LED attached to your Android
Things board.

Follow the guide [here](https://developers.google.com/assistant/sdk/guides/service/python/embed/register-device)
to learn how to register your device.

- After you register your device model and id, replace the device model and instance
 `PLACEHOLDER` values in `AssistantActivity`:

```Java
private static final String DEVICE_MODEL_ID = "my-device-model-id";
private static final String DEVICE_INSTANCE_ID = "my-device-instance-id";
```

- Handle a Device Actions response if you get one.

```Java
mEmbeddedAssistant = new EmbeddedAssistant.Builder()
    ...
    .setConversationCallback(new ConversationCallback() {
        ...
        @Override
        public void onDeviceAction(String intentName, JSONObject parameters) {
            // Check the type of command
            if (intentName.equals("action.devices.commands.OnOff")) {
                try {
                    boolean turnOn = parameters.getBoolean("on");
                    mLed.setValue(turnOn);
                } catch (JSONException e) {
                    Log.e(TAG, "Cannot get value of command", e);
                } catch (IOException e) {
                    Log.e(TAG, "Cannot set value of LED", e);
                }
            }
        }
    }
    ...
...
```

Try it:

- "Turn on"
- "Turn off"

The LED should change states based on your command.

## Enable auto-launch behavior

This sample app is currently configured to launch only when deployed from your
development machine. To enable the main activity to launch automatically on boot,
add the following `intent-filter` to the app's manifest file:

```xml
<activity ...>

    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.HOME"/>
        <category android:name="android.intent.category.DEFAULT"/>
    </intent-filter>

</activity>
```

## License

Copyright 2017 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.

[voice-kit]: https://aiyprojects.withgoogle.com/voice/
[console]: https://console.developers.google.com
[google-assistant-api-config]: https://developers.google.com/assistant/sdk/prototype/getting-started-other-platforms/config-dev-project-and-account
[console-credentials]: https://console.developers.google.com/apis/credentials
[google-oauthlib-tool]: https://github.com/GoogleCloudPlatform/google-auth-library-python-oauthlib
[dev-preview-download]: https://partner.android.com/things/console/
[set-activity-controls]: https://developers.google.com/assistant/sdk/prototype/getting-started-other-platforms/config-dev-project-and-account#set-activity-controls
[mic]: https://www.adafruit.com/product/3367
[speaker]: https://www.adafruit.com/product/3369
[python3]: https://www.python.org/downloads/
