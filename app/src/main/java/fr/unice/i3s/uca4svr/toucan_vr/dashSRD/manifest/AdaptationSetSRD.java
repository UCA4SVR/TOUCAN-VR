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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SchemeValuePair;
import java.util.Arrays;
import java.util.List;

public class AdaptationSetSRD extends AdaptationSet {

    /**
     * Private attributes
     */
    private String schemeIdUri;
    private String value;
    private final String srdSchemeIdUri = "urn:mpeg:dash:srd:2014";

    /**
     * SRD related attributes
     */
    private int sourceId;
    private float objectX;
    private float objectY;
    private float objectWidth;
    private float objectHeight;
    private float totalWidth;
    private float totalHeight;
    private int spatialSetId;

    /**
     * @param id                       A non-negative identifier for the adaptation set that's unique in the scope of its
     *                                 containing period, or {@link #ID_UNSET} if not specified.
     * @param type                     The type of the adaptation set. One of the {@link C}
     *                                 {@code TRACK_TYPE_*} constants.
     * @param representations          The {@link Representation}s in the adaptation set.
     * @param accessibilityDescriptors The accessibility descriptors in the adaptation set.
     * @param supplementalProperty     A pair uriScheme-value
     */
    public AdaptationSetSRD(int id, int type, List<Representation> representations, List<SchemeValuePair> accessibilityDescriptors, SchemeValuePair supplementalProperty) {
        super(id, type, representations, accessibilityDescriptors);
        this.schemeIdUri = supplementalProperty.schemeIdUri;
        this.value = supplementalProperty.value;
        this.sourceId = this.spatialSetId = -1;
        this.objectX = this.objectY = this.objectWidth = this.objectHeight = this.totalHeight = this.totalWidth = -1;
        if(this.value.equals(this.srdSchemeIdUri)) parseSrdValue(this.value);
    }

    /**
     * If the schemeIdUri refers to SRD, this method parses the value to extract all the values
     * @param value String corresponding to the value of the schemeIdUri attribute
     */
    private void parseSrdValue(String value) {
        List<String> supplementalPropertyValues = Arrays.asList(value.split(","));
        int length = supplementalPropertyValues.size();
        if(length>0) this.sourceId = Integer.parseInt(supplementalPropertyValues.get(0));
        if(length>1) this.objectX = Float.parseFloat(supplementalPropertyValues.get(1));
        if(length>2) this.objectY = Float.parseFloat(supplementalPropertyValues.get(2));
        if(length>3) this.objectWidth = Float.parseFloat(supplementalPropertyValues.get(3));
        if(length>4) this.objectHeight = Float.parseFloat(supplementalPropertyValues.get(4));
        if(length>5) this.totalWidth = Float.parseFloat(supplementalPropertyValues.get(5));
        if(length>6) this.totalHeight = Float.parseFloat(supplementalPropertyValues.get(6));
        if(length>7) this.spatialSetId = Integer.parseInt(supplementalPropertyValues.get(7));
    }

    //Get methods to retrieve attributes

    public String getSchemeIdUri() { return this.schemeIdUri; }
    public String getValue() { return this.value; }
    public int getSourceId() {return this.sourceId; }
    public float getObjectX() {return this.objectX; }
    public float getObjectY() {return this.objectY; }
    public float getObjectWidth() {return this.objectWidth; }
    public float getObjectHeight() {return this.objectHeight; }
    public float getTotalWidth() {return this.totalWidth; }
    public float getTotalHeight() {return this.totalHeight; }
    public int getSpatialSetId() {return this.spatialSetId; }
}
