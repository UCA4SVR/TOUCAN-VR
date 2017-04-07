/*
 * Copyright (C) 2016 The Android Open Source Project
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
 *
 * Modifications:
 * Package name
 * Added SRD support to the parser
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 * Author: Savino Dambra
 */
package fr.unice.i3s.uca4svr.toucan_vr.dashSRD.manifest;

import android.util.Log;

import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SchemeValuePair;
import com.google.android.exoplayer2.util.XmlPullParserUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.ArrayList;
import java.util.List;

public class DashSRDManifestParser extends com.google.android.exoplayer2.source.dash.manifest.DashManifestParser {

    //Private attributes used to handle different tags inside AdaptationSet
    private List<SupplementalProperty> supplementalPropertyList;

    /**
     * Parses different tags not included in the standard parsing of the adaptationSet tag
     * @param xpp parser object
     * @throws XmlPullParserException
     */
    @Override
    protected void parseAdaptationSetChild(XmlPullParser xpp) throws XmlPullParserException {
        if(XmlPullParserUtil.isStartTag(xpp, "SupplementalProperty")) {
            if(this.supplementalPropertyList == null) {
                this.supplementalPropertyList = new ArrayList<>();
            }
            supplementalPropertyList.add(parseSupplementalProperty(xpp));
        }
    }

    /**
     * Parses the Supplemental property tag to create a SupplementalProperty Object
     * @param xpp
     * @return Suppplemental property object
     * @throws XmlPullParserException
     */
    private SupplementalProperty parseSupplementalProperty(XmlPullParser xpp) throws XmlPullParserException {
        String schemeIdUri = xpp.getAttributeValue(null,"schemeIdUri");
        String value = xpp.getAttributeValue(null,"value");
        return new SupplementalProperty(schemeIdUri,value);
    }

    /**
     * Build the AdaptationSetSRD with the supplemental property object list enclosed.
     * @param id
     * @param contentType
     * @param representations
     * @param accessibilityDescriptors
     * @return adaptationSetSRD
     */
    @Override
    protected AdaptationSetSRD buildAdaptationSet(int id, int contentType, List<Representation> representations, List<SchemeValuePair> accessibilityDescriptors) {
        AdaptationSetSRD adaptationSetSRD = new AdaptationSetSRD(id, contentType, representations, accessibilityDescriptors, supplementalPropertyList);
        if(this.supplementalPropertyList != null) this.supplementalPropertyList.clear();
        return adaptationSetSRD;
    }

}
