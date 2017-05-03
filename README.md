Google Assistant API Sample for Android Things
==============================================

This sample shows how to call the Google Assistant API from Android Things.

It records a spoken request from I2S microphones and plays back Assistant's spoken response
on speakers connected to an I2S DAC.

Pre-requisites
--------------

- Android Studio 2.2+
- Raspberry Pi 3
- I2S microphone and speaker.
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
  [Google Developer Console credentials section][console-credentials]
- Using the [`oauth2l`][oauth2l]
  command line tool generate a `REFRESH_TOKEN`

         oauth2l --json client_secret_NNNN.json token \
            https://www.googleapis.com/auth/assistant-sdk-prototype

- Edit `Credentials.java`:
  - From the `client_secret_NNNN.json` file copy:
    - `CLIENT_ID`
    - `CLIENT_SECRET`
  - From the [`oauth2l token`][oauth2l] command line tool output copy:
    - `REFRESH_TOKEN`
- On Android Studio, click on the "Run" button or on the command line, type:
```bash
./gradlew installDebug
adb shell am start com.example.androidthings.assistant/.AssistantActivity
```
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

[console]: https://console.developers.google.com
[console-apis]: https://console.developers.google.com/apis
[console-credentials]: https://console.developers.google.com/apis/credentials
[oauth2-installed-app]: https://developers.google.com/identity/protocols/OAuth2InstalledApp
[oauth2l]: https://github.com/google/oauth2l/tree/master/go/oauth2client
[dev-preview-download]: https://developer.android.com/things/preview/download.html
[activity-controls]: https://myaccount.google.com/activitycontrols