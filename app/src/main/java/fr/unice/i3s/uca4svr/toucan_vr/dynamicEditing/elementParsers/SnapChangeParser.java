package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.elementParsers;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;


import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingParser;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.SnapChange;

/**
 * Parser state for snapchange option.
 * Parses tags corresponding to snapchange XML description and creates a snapchange object out of it.
 *
 * @author Julien Lemaire
 */
public class SnapChangeParser extends ElementParser {

  private SnapChange snapchange;
  private String text;

  public SnapChangeParser(DynamicEditingHolder holder) {
    snapchange = new SnapChange(holder);
    text = "";
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
    if (tagname.equalsIgnoreCase("snapchange")) {
      // add video object to list and check if all the parameters are set
      if(snapchange!=null && snapchange.isWellDefined()) {
        holder.add(snapchange);
        context.setElementParser(new GlobalParser());
      } else {
        throw new XmlPullParserException("Not well formed snapchange tag!");
      }
    } else if (tagname.equalsIgnoreCase("milliseconds")) {
      snapchange.setMilliseconds(Integer.parseInt(text));
    } else if (tagname.equalsIgnoreCase("roiDegrees")) {
      snapchange.setRoiDegrees(Integer.parseInt(text));
    } else if (tagname.equalsIgnoreCase("foVTile")) {
      String[] strTiles = text.split(",");
      int[] intTiles = new int[strTiles.length];
      for (int i = 0; i < strTiles.length; i++) {
        intTiles[i] = Integer.parseInt(strTiles[i]);
      }
      snapchange.setFoVTiles(intTiles);
    }
  }
}
