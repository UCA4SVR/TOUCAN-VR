/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package fr.unice.i3s.uca4svr.toucan_vr.tflite;

import android.app.Activity;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;


/** This TensorFlowLite classifier works with the float MobileNet model. */
public class ClassifierFloatMobileNet extends Classifier {

  /** MobileNet requires additional normalization of the used input. */
  private static final float IMAGE_MEAN = 127.5f;
  private static final float IMAGE_STD = 127.5f;



  /**
   * Initializes a {@code ClassifierFloatMobileNet}.
   *
   * @param activity
   */
  public ClassifierFloatMobileNet(Activity activity, Device device, int numThreads)
      throws IOException {
    super(activity, device, numThreads);
  }

  @Override
  public int getImageSizeX() {
    return 224;
  }

  @Override
  public int getImageSizeY() {
    return 224;
  }

  @Override
  protected String getModelPath() {
    // you can download this file from
    // see build.gradle for where to obtain this file. It should be auto
    // downloaded into assets.
//    return "toto.tflite";
    return "converted_model.tflite"; //"model_test.tflite";

  }

//  @Override
//  protected String getLabelPath() {
//    return "labels.txt";
//  }

//  @Override
//  protected int getNumBytesPerChannel() {
//    return 4; // Float.SIZE / Byte.SIZE;
//  }


//  @Override
//  protected float getProbability(int labelIndex) {
//    return predictions_prob.get(0)[0][labelIndex];
//  }
//
//  @Override
//  protected void setProbability(int labelIndex, Number value) {
//    predictions_prob.get(0)[0][labelIndex] = value.floatValue();
//  }

//  @Override
//  protected float getNormalizedProbability(int labelIndex) {
//    return predictions_prob.get(0)[0][labelIndex];
//  }

  @Override
  protected void runInference() {
    Map<Integer, Object> predictions_map = new HashMap<>();
    predictions_map.put(0, predictions_prob);
//    System.out.println("inputs[0].limit() : "+inputs[0].limit());
//    System.out.println("inputs[1].limit() : "+inputs[1].limit());
//    System.out.println("predictions_prob.length : "+predictions_prob.length);

    tflite.runForMultipleInputsOutputs(inputs, predictions_map);
  }
}
