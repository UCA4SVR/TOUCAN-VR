package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.elementParsers;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingParser;

/**
 * Parses a particular element from XML operations file.
 * Has to be overridden by implementing the different behaviors of the parser, following a state and template method pattern.
 *
 * @author Julien Lemaire
 */
public abstract class ElementParser {
  public void parse(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser)
    throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        switch (eventType) {
          case XmlPullParser.START_TAG:
            doOnStartTag(context, holder, parser);
            break;

          case XmlPullParser.TEXT:
            doOnText(context, holder, parser);
            break;

          case XmlPullParser.END_TAG:
            doOnEndTag(context, holder, parser);
            break;

          default:
            break;
        }
        parser.next();
  }

  abstract void doOnStartTag(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) throws XmlPullParserException;

  abstract void doOnText(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser);

  abstract void doOnEndTag(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) throws XmlPullParserException;

}
