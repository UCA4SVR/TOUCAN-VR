package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.elementParsers;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.Optional;
import java.util.function.Consumer;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingParser;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.SlowDown;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.audio.AudioOperation;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.audio.MuteAudio;

public class SlowDownParser extends ElementParser {

  private String text;
  private SlowDown slowDown;

  private enum AudioOperationEnum {
    MUTE(MuteAudio.class);

    private Class<? extends AudioOperation> opClass;

    AudioOperationEnum(Class<? extends AudioOperation> opClass) {
      this.opClass = opClass;
    }

    Class<? extends AudioOperation> getOpClass() {
      return opClass;
    }
  }

  public SlowDownParser(DynamicEditingHolder dynamicEditingHolder) {
    this.slowDown = new SlowDown(dynamicEditingHolder);
  }

  @Override
  void doOnStartTag(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) {
    // Do nothing
  }

  @Override
  void doOnText(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) {
    text = parser.getText();
  }

  @Override
  void doOnEndTag(DynamicEditingParser context, DynamicEditingHolder holder, XmlPullParser parser) throws XmlPullParserException {
    String tagname = parser.getName();
    if (tagname.equalsIgnoreCase("slowdown")) {
      if (slowDown.isWellDefined()) {
        holder.add(slowDown);
        context.setElementParser(new GlobalParser());
      } else {
        throw new XmlPullParserException("Not well formed slowdown tag!");
      }
    } else if (tagname.equalsIgnoreCase("milliseconds")) {
      slowDown.setMilliseconds(Integer.parseInt(text));
    } else if (tagname.equalsIgnoreCase("endtime")) {
      slowDown.setEndTimeMs(Integer.parseInt(text));
    } else if (tagname.equalsIgnoreCase("factor")) {
      slowDown.setSlowDownFactor(Float.parseFloat(text));
    } else if (tagname.equalsIgnoreCase("audio")) {
      Optional<AudioOperationEnum> audioOperationEnum = findAudioOperation(text.toUpperCase());
      audioOperationEnum.ifPresent(new Consumer<AudioOperationEnum>() {
        @Override
        public void accept(AudioOperationEnum audioOperationEnum) {
          try {
            slowDown.setAudioOperation(audioOperationEnum.getOpClass().newInstance());
          } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
          }
        }
      });
    }
  }

  private Optional<AudioOperationEnum> findAudioOperation(String name) {
    for (AudioOperationEnum a : AudioOperationEnum.values()) {
      if (a.name().equals(name)) {
        return Optional.of(a);
      }
    }
    //not found
    return Optional.empty();
  }
}
