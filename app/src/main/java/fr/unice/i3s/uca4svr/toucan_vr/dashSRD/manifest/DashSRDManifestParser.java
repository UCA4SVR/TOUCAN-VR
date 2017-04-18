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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SchemeValuePair;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase;
import com.google.android.exoplayer2.util.XmlPullParserUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DashSRDManifestParser extends com.google.android.exoplayer2.source.dash.manifest.DashManifestParser {

    @Override
    protected AdaptationSet parseAdaptationSet(XmlPullParser xpp, String baseUrl,
                                               SegmentBase segmentBase) throws XmlPullParserException, IOException {
        int id = parseInt(xpp, "id", AdaptationSet.ID_UNSET);
        int contentType = parseContentType(xpp);

        String mimeType = xpp.getAttributeValue(null, "mimeType");
        String codecs = xpp.getAttributeValue(null, "codecs");
        int width = parseInt(xpp, "width", Format.NO_VALUE);
        int height = parseInt(xpp, "height", Format.NO_VALUE);
        float frameRate = parseFrameRate(xpp, Format.NO_VALUE);
        int audioChannels = Format.NO_VALUE;
        int audioSamplingRate = parseInt(xpp, "audioSamplingRate", Format.NO_VALUE);
        String language = xpp.getAttributeValue(null, "lang");
        ArrayList<DrmInitData.SchemeData> drmSchemeDatas = new ArrayList<>();
        ArrayList<SchemeValuePair> inbandEventStreams = new ArrayList<>();
        ArrayList<SchemeValuePair> accessibilityDescriptors = new ArrayList<>();
        List<RepresentationInfo> representationInfos = new ArrayList<>();
        ArrayList<SupplementalProperty> supplementalProperties = new ArrayList<>();
        @C.SelectionFlags int selectionFlags = 0;

        boolean seenFirstBaseUrl = false;
        do {
            xpp.next();
            if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
                if (!seenFirstBaseUrl) {
                    baseUrl = parseBaseUrl(xpp, baseUrl);
                    seenFirstBaseUrl = true;
                }
            } else if (XmlPullParserUtil.isStartTag(xpp, "ContentProtection")) {
                DrmInitData.SchemeData contentProtection = parseContentProtection(xpp);
                if (contentProtection != null) {
                    drmSchemeDatas.add(contentProtection);
                }
            } else if (XmlPullParserUtil.isStartTag(xpp, "ContentComponent")) {
                language = checkLanguageConsistency(language, xpp.getAttributeValue(null, "lang"));
                contentType = checkContentTypeConsistency(contentType, parseContentType(xpp));
            } else if (XmlPullParserUtil.isStartTag(xpp, "Role")) {
                selectionFlags |= parseRole(xpp);
            } else if (XmlPullParserUtil.isStartTag(xpp, "AudioChannelConfiguration")) {
                audioChannels = parseAudioChannelConfiguration(xpp);
            } else if (XmlPullParserUtil.isStartTag(xpp, "Accessibility")) {
                accessibilityDescriptors.add(parseAccessibility(xpp));
            } else if (XmlPullParserUtil.isStartTag(xpp, "Representation")) {
                RepresentationInfo representationInfo = parseRepresentation(xpp, baseUrl, mimeType, codecs,
                        width, height, frameRate, audioChannels, audioSamplingRate, language,
                        selectionFlags, accessibilityDescriptors, segmentBase);
                contentType = checkContentTypeConsistency(contentType,
                        getContentType(representationInfo.format));
                representationInfos.add(representationInfo);
            } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentBase")) {
                segmentBase = parseSegmentBase(xpp, (SegmentBase.SingleSegmentBase) segmentBase);
            } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentList")) {
                segmentBase = parseSegmentList(xpp, (SegmentBase.SegmentList) segmentBase);
            } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTemplate")) {
                segmentBase = parseSegmentTemplate(xpp, (SegmentBase.SegmentTemplate) segmentBase);
            } else if (XmlPullParserUtil.isStartTag(xpp, "InbandEventStream")) {
                inbandEventStreams.add(parseInbandEventStream(xpp));
                //Add-ons
            } else if (XmlPullParserUtil.isStartTag(xpp, "SupplementalProperty")) {
                supplementalProperties.add(parseSupplementalProperty(xpp));
                //End Add-ons
            } else if (XmlPullParserUtil.isStartTag(xpp)) {
                parseAdaptationSetChild(xpp);
            }
        } while (!XmlPullParserUtil.isEndTag(xpp, "AdaptationSet"));

        // Build the representations.
        List<Representation> representations = new ArrayList<>(representationInfos.size());
        for (int i = 0; i < representationInfos.size(); i++) {
            representations.add(buildRepresentation(representationInfos.get(i), contentId,
                    drmSchemeDatas, inbandEventStreams));
        }

        return buildAdaptationSet(id, contentType, representations, accessibilityDescriptors, supplementalProperties);
    }

    /**
     * Parses the Supplemental property tag to create a SupplementalProperty Object
     * @param xpp
     * @return Suppplemental property object
     * @throws XmlPullParserException
     */
    protected SupplementalProperty parseSupplementalProperty(XmlPullParser xpp) throws XmlPullParserException {
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

    protected AdaptationSetSRD buildAdaptationSet(int id, int contentType, List<Representation> representations, List<SchemeValuePair> accessibilityDescriptors, ArrayList<SupplementalProperty> supplementalProperties) {
        AdaptationSetSRD adaptationSetSRD = new AdaptationSetSRD(id, contentType, representations, accessibilityDescriptors, supplementalProperties);
        return adaptationSetSRD;
    }
}
