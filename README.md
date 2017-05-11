Google Assistant API Sample for Android Things
==============================================

This sample shows how to call the Google Assistant API from Android Thing.

It records a spoken request from the connected microphones, send it to the Google Assistant API and plays back Assistant's spoken response on the connected speaker.

Pre-requisites
--------------

- Android Studio 2.2+
- Android Things compatible board
- If using [AIY Projects Voice Kit][voice-kit]:
    - Android Things Raspberry Pi Dev Preview [3.1 image][dev-preview-download] with I2S enabled.
        - mount the sdcard image

              # replace sdb1 with the sdcard reader device.
              mount /dev/sdb1 /mnt/disk

        - edit `config.txt`

              # comment or remove this line:
              # dtoverlay=pwm-2chan-with-clk,pin=18,func=2,pin2=13,func2=4
              #
              # uncomment or add this line:
              dtoverlay=generic-i2s

        - umount the sdcard image

              sync
              umount /mnt/disk
- If using Android Things supported [microphone][mic] and [speaker][speaker]:
    - set `AUDIO_USE_I2S_VOICEHAT_IF_AVAILABLE = false` in `AssistantActivity.java`
- [Google API Console Project][console] with Google Assistant API [enabled][console-apis].
- [OAuth client ID][console-credentials] with application type `Other`
- Google Account with the following [activity controls][activity-controls] enabled
  - Web & App Activity
  - Location History
  - Device Information
  - Voice & Audio Activity

Run the sample
--------------

- Get the `client_secret_NNNN.json`
  [OAuth client ID][oauth2-installed-app] JSON file for the application from
  [Google Developer Console credentials section][console-credentials].
- Use the [`google-oauthlib-tool`][google-oauthlib-tool] to generate credentials:
```
pip install google-auth-oauthlib[tool]
google-oauthlib-tool --client-secrets client_secret_NNNN.json \
                     --credentials app/src/main/res/raw/credentials.json \
                     --scope https://www.googleapis.com/auth/assistant-sdk-prototype \
                     --save
```
- On Android Studio, click on the "Run" button or on the command line, type:
```bash
./gradlew installDebug
adb shell am start com.example.androidthings.assistant/.AssistantActivity
```
- Reboot your device to grant the `AUDIO_RECORD` permission.
- Press the button: recording starts.
- Ask a question in the microphone.
- Release the button: recording stops.
- The Google Assistant answer should playback on the speaker.

License
-------

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
[console-apis]: https://console.developers.google.com/apis
[console-credentials]: https://console.developers.google.com/apis/credentials
[oauth2-installed-app]: https://developers.google.com/identity/protocols/OAuth2InstalledApp
[google-oauthlib-tool]: https://github.com/GoogleCloudPlatform/google-auth-library-python-oauthlib
[dev-preview-download]: https://dl.google.com/dl/androidthings/rpi3/devpreview/3.1/androidthings_rpi3_devpreview_3_1.zip
[activity-controls]: https://myaccount.google.com/activitycontrols
[mic]: https://www.adafruit.com/product/3367
[speaker]: https://www.adafruit.com/product/3369