/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class DynamicEditingParser {

	//private attributes
	private DynamicEditingHolder dynamicEditingHolder;
	private SnapChange snapchange;
	private String text;
	private String[] strTiles;
	private int[] intTiles;
	private String dynamicEditingFN;

	public DynamicEditingParser(String dynamicEditingFN) {
		this.dynamicEditingFN = dynamicEditingFN;
	}

	//Main parse method
	public DynamicEditingHolder parse() {
		File file = new File("/storage/emulated/0/"+dynamicEditingFN);
		dynamicEditingHolder = new DynamicEditingHolder();

		XmlPullParserFactory factory;
		XmlPullParser parser;
		try {
			factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);
			parser = factory.newPullParser();
			parser.setInput(new FileInputStream(file),"UTF-8");
			int eventType = parser.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				String tagname = parser.getName();
				switch (eventType) {
					case XmlPullParser.START_TAG:
						if (tagname.equalsIgnoreCase("snapchange")) {
							snapchange = new SnapChange();
						}
						break;

					case XmlPullParser.TEXT:
						text = parser.getText();
						break;

					case XmlPullParser.END_TAG:
						if (tagname.equalsIgnoreCase("snapchange")) {
							// add video object to list and check if all the parameters are set
							if(snapchange!=null && snapchange.isOK() && dynamicEditingHolder!=null) {
								dynamicEditingHolder.add(snapchange);
							} else {
								dynamicEditingHolder = null;
							}
						} else if (tagname.equalsIgnoreCase("milliseconds")) {
							if (snapchange!=null) snapchange.setMilliseconds(Integer.parseInt(text));
						} else if (tagname.equalsIgnoreCase("roiDegrees")) {
							if (snapchange!=null) snapchange.setRoiDegrees(Integer.parseInt(text));
						} else if (tagname.equalsIgnoreCase("foVTile")) {
								strTiles = text.split(",");
								intTiles = new int[strTiles.length];
								for (int i = 0; i < strTiles.length; i++) {
									intTiles[i] = Integer.parseInt(strTiles[i]);
								}
								if (snapchange != null) snapchange.setFoVTiles(intTiles);
						}
						break;
					default:
						break;
				}
				eventType = parser.next();
			}
		} catch (XmlPullParserException | IOException e) {
			e.printStackTrace();
			dynamicEditingHolder = null;
		} finally {
			return dynamicEditingHolder;
		}
	}

}