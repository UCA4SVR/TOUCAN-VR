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

package fr.unice.i3s.uca4svr.toucan_vr.dashSRD.manifest;

import android.support.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SchemeValuePair;

import java.util.ArrayList;
import java.util.List;

public class AdaptationSetSRD extends AdaptationSet implements Comparable<AdaptationSetSRD>{

    public final ArrayList<SupplementalProperty> supplementalProperties;
    public boolean isSRDRelated = false;
    public int SRDIndex = -1;

    /**
     * @param id                       A non-negative identifier for the adaptation set that's unique in the scope of its
     *                                 containing period, or {@link #ID_UNSET} if not specified.
     * @param type                     The type of the adaptation set. One of the {@link C}
     *                                 {@code TRACK_TYPE_*} constants.
     * @param representations          The {@link Representation}s in the adaptation set.
     * @param accessibilityDescriptors The accessibility descriptors in the adaptation set.
     * @param supplementalProperties   A list of supplemental properties
     */
    public AdaptationSetSRD(int id, int type, List<Representation> representations, List<SchemeValuePair> accessibilityDescriptors, ArrayList<SupplementalProperty> supplementalProperties) {
        super(id, type, representations, accessibilityDescriptors);
        this.supplementalProperties = supplementalProperties;
        for(int i=0; i<supplementalProperties.size();i++) {
            if(supplementalProperties.get(i).isSRDRelated) {
                SRDIndex = i;
                isSRDRelated = true;
            }
        }
    }

    @Override
    public int compareTo(@NonNull AdaptationSetSRD compareObject) {
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        //TODO: REMOVE INTEGER CASTING AFTER FIXING THE PRE-PROCESSING SCRIPT THAT IS NOW WORKING WITH PERCENTAGE

        //ComputingObjectScores
        int currentObjectScore = 100;
        int compareObjectScore = 100;

        if(this.SRDIndex>=0)
            currentObjectScore = (int)this.supplementalProperties.get(this.SRDIndex).getObjectX() + 8*(int)this.supplementalProperties.get(this.SRDIndex).getObjectY();
        if(compareObject.SRDIndex>=0)
            compareObjectScore = (int)compareObject.supplementalProperties.get(compareObject.SRDIndex).getObjectX() + 8*(int)compareObject.supplementalProperties.get(compareObject.SRDIndex).getObjectY();

        //Computing result
        if(currentObjectScore < compareObjectScore) return BEFORE;
        else if(currentObjectScore > compareObjectScore) return AFTER;
        else return EQUAL;
    }

}
