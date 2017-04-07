Google Assistant API Sample for Android Things
==============================================

This sample shows how to call the Google Assistant API from Android Things.

It streams an audio file containing a pre-recorded spoken request ("what time is it?")
located in the app [resource](app/src/main/res/raw) and writes the Assistant's spoken response
to a temporary audio file.

Pre-requisites
--------------

- Android Things compatible board
- Android Studio 2.2+
- [Google API Console Project](https://console.developers.google.com) w/ Embedded Assistant API [enabled](https://console.developers.google.com/apis).
- [OAuth client ID](https://console.developers.google.com/apis/credentials) with application type `Other`.

Build and install
-----------------

- Edit `Credentials.json` and add valid OAuth2 credentials for both the application *and* the end user:
    - From the [OAuth client ID](https://developers.google.com/identity/protocols/OAuth2InstalledApp) `client_secret_NNNN.json` file of the application extract:
        - `CLIENT_ID`
        - `CLIENT_SECRET`
	- Using the [`oauth2l token`](https://github.com/google/oauth2l/tree/master/go/oauth2client) command line tool generate:
        - `REFRESH_TOKEN`
- On Android Studio, click on the "Run" button or on the command line, type:
```bash
./gradlew installDebug
adb shell am start com.example.androidthings.assistant/.AssistantActivity
```
- The sample should prints the path of the assistant answer to `logcat`.
```
assistant response file: /data/user/0/com.example.androidthings.assistant/cache/assistant-out-NN.raw
```
- Retrieve the audio file and play it with the following commands:
```bash
adb pull /data/user/0/com.example.androidthings.assistant/cache/assistant-out-*.raw
aplay assistant-out-*.raw
```

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
