/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.unice.i3s.uca4svr.toucan_vr.realtimeUserPosition;


import android.os.AsyncTask;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRTransform;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class PushRealtimeEvents extends AsyncTask<RealtimeEvent, Integer, Boolean> {

    private GVRContext context;
    private PushResponse callback;
    private String serverIP;
    private final static String pushInfoPath = "/infos";


    public PushRealtimeEvents(GVRContext context, String serverIP, PushResponse callback) {
        this.context = context;
        this.serverIP = serverIP;
        this.callback = callback;
    }

    @Override
    protected Boolean doInBackground(RealtimeEvent... events) {
        for (RealtimeEvent event : events) {
            if (!push(event)) return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        callback.pushResponse(result);
    }

    private Boolean push(RealtimeEvent event) {
        GVRTransform headTransform = context.getMainScene().getMainCameraRig().getHeadTransform();
        String urlParameters = "x=" + event.x +
                "&y=" + event.y +
                "&z=" + event.z +
                "&w=" + event.w +
                "&time=" + event.timestamp +
                "&currentTime=" + event.videoTime +
                "&playing=" + event.playing +
                "&start=" + event.start;
        String fullURI = serverIP + pushInfoPath;

        if (fullURI.length() > 0) {
            HttpURLConnection urlc;
            try {
                urlc = (HttpURLConnection) (new URL(fullURI).openConnection());
            } catch (IOException e) {
                return false;
            }
            try {
                byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
                urlc.setDoOutput(true);
                urlc.setInstanceFollowRedirects(false);
                urlc.setRequestMethod("POST");
                urlc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                urlc.setRequestProperty("charset", "utf-8");
                urlc.setRequestProperty("Content-Length", Integer.toString(postData.length));
                urlc.setUseCaches(false);
                try (DataOutputStream wr = new DataOutputStream(urlc.getOutputStream())) {
                    wr.write(postData);
                }
                urlc.connect();
                return (urlc.getResponseCode() == 200);
            } catch (IOException e) {
                return false;
            } finally {
                urlc.disconnect();
            }
        } else {
            return false;
        }
    }

    public String getServerIP() {
        return serverIP;
    }
}