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
package fr.unice.i3s.uca4svr.toucan_vr.connectivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class CheckConnection extends AsyncTask<String,Integer,Boolean> {

    private Context context;
    public CheckConnectionResponse response = null;

    public CheckConnection(Context context) {
        this.context = context;
    }


    @Override
    protected Boolean doInBackground(String... params) {
        for(String param : params) {
            if(!fileExists(param)) return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        response.urlChecked(result);
    }

    /**
     * Check if at least one network is available.
     * If the function returns TRUE it doesn't mean we have a working connection
     * but that we are just connected to an access point.
     * A further verification is needed.
     * @return True if the network is available, false otherwise
     */
    boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    /**
     * Check if the connection is really active performing an HTTP head request.
     * @return True if the connection is really active, false otherwise,
     */
    public boolean fileExists(String mediaUri) {
        if (isNetworkAvailable()) {
            try {
                HttpURLConnection urlc = (HttpURLConnection) (new URL(mediaUri).openConnection());
                urlc.setRequestProperty("User-Agent", "Test");
                urlc.setRequestProperty("Connection", "close");
                urlc.setConnectTimeout(1500);
                urlc.connect();
                return (urlc.getResponseCode() == 200);
            } catch (IOException e) {
                return false;
            }
        } else {
          System.err.print("HttpURLConnection failed !!!");
            return false;
        }
    }

}
