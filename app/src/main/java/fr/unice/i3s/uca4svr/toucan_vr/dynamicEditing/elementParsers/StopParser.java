package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.elementParsers;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingParser;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.Stop;

public class StopParser extends ElementParser {

  private Stop stop;
  private String text;

  public StopParser(DynamicEditingHolder dynamicEditingHolder) {
    stop = new Stop(dynamicEditingHolder);
  }

  @Override
  void doOnStartTag(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) {
    // Nothing
  }

  @Override
  void doOnText(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) {
    text = parser.getText();
  }

  @Override
  void doOnEndTag(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) throws XmlPullParserException {
    String tagname = parser.getName();
    if (tagname.equalsIgnoreCase("stop")) {
      // add video object to list and check if all the parameters are set
      if(stop.isWellDefined()) {
        holder.add(stop);
        context.setElementParser(new GlobalParser());
      } else {
        throw new XmlPullParserException("Not well formed stop tag!");
      }
    } else if (tagname.equalsIgnoreCase("milliseconds")) {
      stop.setMilliseconds(Integer.parseInt(text));
    } else if (tagname.equalsIgnoreCase("duration")) {
      stop.setDuration(Integer.parseInt(text));
    }
  }
}
