package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.elementParsers;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingParser;

/**
 * Parser state where nothing is currently parsed (no operation).
 * This state tries to determine what is to be parsed next.
 *
 * @author Julien Lemaire
 */
public class GlobalParser extends ElementParser {

  @Override
  void doOnStartTag(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) throws XmlPullParserException {
    String tagname = parser.getName();
    if (tagname.equalsIgnoreCase("snapchange")) {
      context.setElementParser(new SnapChangeParser(holder));
    } else if (tagname.equalsIgnoreCase("stop")) {
      context.setElementParser(new StopParser(holder));
    } else if (tagname.equalsIgnoreCase("invisiblewall")) {
      context.setElementParser(new InvisibleWallParser(holder));
    } else if (tagname.equalsIgnoreCase("slowdown")) {
      context.setElementParser(new SlowDownParser(holder));
    } else {
      throw new XmlPullParserException("Unsupported tag: " + tagname);
    }
  }

  @Override
  void doOnText(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) {
    // Nothing
  }

  @Override
  void doOnEndTag(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) {
    // Nothing
  }
}
