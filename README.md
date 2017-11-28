# Google Assistant API Sample for Android Things

This sample shows how to call the Google Assistant Service from Android Things using gRPC.

It records a spoken request from the connected microphones, sends it to the Google Assistant API and plays back the Assistant's spoken response on the connected speaker.

## Pre-requisites

- Android Studio 2.2+.
- Android Things compatible board.
- [AIY Projects Voice Kit][voice-kit] or supported [microphone][mic] and [speaker][speaker] (See [audio configuration](#audio-configuration]).
- [Google API Console Project][console].

## Run the sample

- Configure the Google API Console Project to use the [Google Assistant API][google-assistant-api-config].
- Download `client_secret_NNNN.json` (type: `Other`) from the [credentials section of the Console][console-credentials].
- Install the [`google-oauthlib-tool`][google-oauthlib-tool] in a [Python 3][python3] virtual environment:
```
python3 -m venv env
env/bin/python -m pip install --upgrade pip setuptools
env/bin/pip install --upgrade google-auth-oauthlib[tool]
```
- Use the [`google-oauthlib-tool`][google-oauthlib-tool] to generate credentials:
```
env/bin/google-oauthlib-tool --client-secrets client_secret_NNNN.json \
                             --credentials app/src/main/res/raw/credentials.json \
                             --scope https://www.googleapis.com/auth/assistant-sdk-prototype \
                             --save
```
- Make sure to set the [Activity Controls][set-activity-controls] for the Google Account using the application.
- On the first install, grant the sample required permissions for audio and internet access:
```bash
./gradlew assembleDebug
adb install -g app/build/outputs/apk/app-debug.apk
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

You can change those mappings by changing the `USE_VOICEHAT_I2S_DAC` constant or replacing the audio configuration in `AssitantActivity.OnCreate` with one of the following:
```
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
