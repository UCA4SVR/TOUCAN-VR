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

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.elementParsers.ElementParser;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.elementParsers.GlobalParser;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.DynamicOperation;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.SnapChange;

public class DynamicEditingParser {

	//private attributes
	private String dynamicEditingFN;
	private ElementParser elementParser;

	public DynamicEditingParser(String dynamicEditingFN) {
		this.dynamicEditingFN = dynamicEditingFN;
		this.elementParser = new GlobalParser();
	}

	//Main parse method
	public void parse(DynamicEditingHolder dynamicEditingHolder) throws XmlPullParserException, IOException {
		File file = new File("/storage/emulated/0/"+dynamicEditingFN);
		XmlPullParserFactory factory;
		XmlPullParser parser;
		factory = XmlPullParserFactory.newInstance();
		factory.setNamespaceAware(true);
		parser = factory.newPullParser();
		parser.setInput(new FileInputStream(file),"UTF-8");
		int eventType = parser.getEventType();
		while (eventType != XmlPullParser.END_DOCUMENT) {
		  elementParser.parse(this, dynamicEditingHolder, parser);
		  eventType = parser.getEventType();
    }
    //check if the file is empty
    if(dynamicEditingHolder.empty()) {
      throw new XmlPullParserException("File is empty!");
    }

    System.out.println(dynamicEditingHolder.getOperations());
	}

	public void setElementParser(ElementParser elementParser) {
	  this.elementParser = elementParser;
  }

}
