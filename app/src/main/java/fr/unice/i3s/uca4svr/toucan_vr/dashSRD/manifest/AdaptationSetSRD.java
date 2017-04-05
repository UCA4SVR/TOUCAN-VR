/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Author: Savino Dambra
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

package fr.unice.i3s.uca4svr.toucan_vr.dashSRD.manifest;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SchemeValuePair;

import java.util.ArrayList;
import java.util.List;

public class AdaptationSetSRD extends AdaptationSet {

    public final List<SupplementalProperty> supplementalPropertyList;

    /**
     * @param id                       A non-negative identifier for the adaptation set that's unique in the scope of its
     *                                 containing period, or {@link #ID_UNSET} if not specified.
     * @param type                     The type of the adaptation set. One of the {@link C}
     *                                 {@code TRACK_TYPE_*} constants.
     * @param representations          The {@link Representation}s in the adaptation set.
     * @param accessibilityDescriptors The accessibility descriptors in the adaptation set.
     * @param supplementalPropertyList A list of supplemental properties
     */
    public AdaptationSetSRD(int id, int type, List<Representation> representations, List<SchemeValuePair> accessibilityDescriptors, List<SupplementalProperty> supplementalPropertyList) {
        super(id, type, representations, accessibilityDescriptors);
        this.supplementalPropertyList = new ArrayList<>();
        for(int i=0; i<supplementalPropertyList.size(); i++)
            this.supplementalPropertyList.add(supplementalPropertyList.get(i));
    }


}
