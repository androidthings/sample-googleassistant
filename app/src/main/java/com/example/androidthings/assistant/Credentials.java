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

import com.google.auth.oauth2.UserCredentials;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

class Credentials {
    static UserCredentials fromResource(Context context, int resourceId)
            throws IOException, JSONException {
        InputStream is = context.getResources().openRawResource(resourceId);
        byte[] bytes = new byte[is.available()];
        is.read(bytes);
        JSONObject json = new JSONObject(new String(bytes, "UTF-8"));
        return new UserCredentials(
                json.getString("client_id"),
                json.getString("client_secret"),
                json.getString("refresh_token")
        );
    }
}
