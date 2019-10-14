package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.elementParsers;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingParser;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.InvisibleWall;

/**
 * Parser state for invisibleWall option.
 * Parses tags corresponding to invisibleWall XML description and creates a invisibleWall object out of it.
 *
 * @author Antoine Dezarnaud
 */

public class InvisibleWallParser extends ElementParser {

  private InvisibleWall invisibleWall;
  private String text;

  public InvisibleWallParser(DynamicEditingHolder dynamicEditingHolder) {
    invisibleWall = new InvisibleWall(dynamicEditingHolder);
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
    if (tagname.equalsIgnoreCase("invisibleWall")) {
      // add video object to list and check if all the parameters are set
      if(invisibleWall.isWellDefined()) {
        holder.add(invisibleWall);
        context.setElementParser(new GlobalParser());
      } else {
        throw new XmlPullParserException("Not well formed invisibleWall tag!");
      }
    } else if (tagname.equalsIgnoreCase("milliseconds")) {
      invisibleWall.setMilliseconds(Integer.parseInt(text));

    } else if (tagname.equalsIgnoreCase("duration")) {
      invisibleWall.setDuration(Integer.parseInt(text));

    } else if (tagname.equalsIgnoreCase("freedegreesx")) {
      invisibleWall.setFreeXDegrees(Integer.parseInt(text));

    } else if (tagname.equalsIgnoreCase("freedegreesy")) {
      invisibleWall.setFreeYDegrees(Integer.parseInt(text));

    } else if (tagname.equalsIgnoreCase("roiDegrees")) {
      invisibleWall.setRoiDegrees(Integer.parseInt(text));

    }  else if (tagname.equalsIgnoreCase("recenterView")) {
      invisibleWall.setRecenterView(Boolean.parseBoolean(text));

    } else if (tagname.equalsIgnoreCase("foVTile")) {
      String[] strTiles = text.split(",");
      int[] intTiles = new int[strTiles.length];
      for (int i = 0; i < strTiles.length; i++) {
        intTiles[i] = Integer.parseInt(strTiles[i]);
      }
      invisibleWall.setFoVTiles(intTiles);
    }
  }
}
