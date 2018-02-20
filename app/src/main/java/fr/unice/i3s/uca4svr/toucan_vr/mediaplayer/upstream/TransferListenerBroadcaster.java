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
package fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.upstream;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds a list of listeners and forwards the events to them.
 * Exists because simple classes from exoplayer2 accept only one listener.
 * Allows the registration of any number of listeners.
 */
public class TransferListenerBroadcaster implements TransferListener<Object> {

    private Set<TransferListener<Object>> listeners = new HashSet<>();

    public TransferListenerBroadcaster() {}

    synchronized public boolean addListener(TransferListener<Object> listener) {
        return listeners.add(listener);
    }

    synchronized public boolean removeListener(TransferListener<Object> listener) {
        return listeners.remove(listener);
    }

    synchronized public void removeAllListeners() {
        listeners.removeAll(listeners);
    }

    @Override
    synchronized public void onTransferStart(Object source, DataSpec dataSpec) {
        for (TransferListener<Object> listener : listeners) {
            listener.onTransferStart(source, dataSpec);
        }
    }

    @Override
    synchronized public void onBytesTransferred(Object source, int bytesTransferred) {
        for (TransferListener<Object> listener : listeners) {
            listener.onBytesTransferred(source, bytesTransferred);
        }
    }

    @Override
    synchronized public void onTransferEnd(Object source) {
        for (TransferListener<Object> listener : listeners) {
            listener.onTransferEnd(source);
        }
    }
}
