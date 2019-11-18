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
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.Trace;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.tensorflow.lite.Interpreter;
//import org.tensorflow.lite.examples.classification.env.Logger;
//import org.tensorflow.lite.gpu.GpuDelegate;

/** A classifier specialized to label images using TensorFlow Lite. */
public abstract class Classifier {
//  private static final Logger LOGGER = new Logger();

  /** The model type used for classification. */
  public enum Model {
    FLOAT,
    QUANTIZED,
  }

  /** The runtime device type used for executing classification. */
  public enum Device {
    CPU,
    NNAPI,
    GPU
  }

  /** Number of results to show in the UI. */
  private static final int MAX_RESULTS = 3;

  /** Dimensions of inputs. */
  private static final int DIM_BATCH_SIZE = 1;

  private static final int DIM_PIXEL_SIZE = 3;

  /** Preallocated buffers for storing image data in. */
  private final int[] intValues = new int[getImageSizeX() * getImageSizeY()];

  /** Options for configuring the Interpreter. */
  private final Interpreter.Options tfliteOptions = new Interpreter.Options();

  /** The loaded TensorFlow Lite model. */
  private MappedByteBuffer tfliteModel;

  /** Labels corresponding to the output of the vision model. */
  private int[] labels = {0, 1};

  /** Optional GPU delegate for accleration. */
//  private GpuDelegate gpuDelegate = null;

  /** An instance of the driver class to run model inference with Tensorflow Lite. */
  protected Interpreter tflite;

  /** A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs. */
//  protected ByteBuffer imgData = null;
  protected ByteBuffer[] inputs;

//  protected Map<Integer, Object> predictions_prob;
  protected float[][] predictions_prob;


  /**
   * Creates a classifier with the provided configuration.
   *
   * @param activity The current Activity.
   * @param model The model to use for classification.
   * @param device The device to use for classification.
   * @param numThreads The number of threads to use for classification.
   * @return A classifier with the desired configuration.
   */
  public static Classifier create(Activity activity, Model model, Device device, int numThreads)
      throws IOException {
//    if (model == Model.FLOAT) {
    return new ClassifierFloatMobileNet(activity, device, numThreads);
//    }
  }

  /** An immutable result returned by a Classifier describing what was recognized. */
  public static class Prediction {
    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     */
    private final String id;

    /** Display name for the recognition. */
    private final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     */
    private final Float confidence;

    /** Optional location within the source image for the location of the recognized object. */
    private RectF location;

    public Prediction(final String id, final String title, final Float confidence) {
      this.id = id;
      this.title = title;
      this.confidence = confidence;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public Float getConfidence() {
      return confidence;
    }


    @Override
    public String toString() {
      String resultString = "";
      if (id != null) {
        resultString += "[" + id + "] ";
      }

      if (title != null) {
        resultString += title + " ";
      }

      if (confidence != null) {
        resultString += String.format("(%.1f%%) ", confidence * 100.0f);
      }

      return resultString.trim();
    }
  }

  /** Initializes a {@code Classifier}. */
  protected Classifier(Activity activity, Device device, int numThreads) throws IOException {
    tfliteModel = loadModelFile(activity);
    switch (device) {
      case NNAPI:
        tfliteOptions.setUseNNAPI(true);
        break;
//      case GPU:
//        gpuDelegate = new GpuDelegate();
//        tfliteOptions.addDelegate(gpuDelegate);
//        break;
      case CPU:
        break;
    }
    tfliteOptions.setNumThreads(numThreads);
    tflite = new Interpreter(tfliteModel, tfliteOptions);
    predictions_prob = new float[1][getNumLabels()];
//    predictions_prob.put(0, ByteBuffer.allocate(getNumLabels() * Float.BYTES));

//    LOGGER.d("Created a Tensorflow Lite Image Classifier.");
  }


  /** Memory-map the model file in Assets. */
  private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
    AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(getModelPath());
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  /*private void loadInputs(float[][] past_samples, boolean[] futureDynamicOperation){

    ByteBuffer buffer1 = ByteBuffer.allocateDirect(past_samples.length * past_samples[0].length * Float.BYTES);
    buffer1.order(ByteOrder.nativeOrder());
    for(int i = 0; i < past_samples.length; i++) {
      for(int j = 0; j < past_samples[i].length; j++) {
        buffer1.putFloat(past_samples[i][j]);
      }
    }
    buffer1.rewind();

    ByteBuffer buffer2 = ByteBuffer.allocateDirect(futureDynamicOperation.length * Integer.BYTES);
    buffer2.order(ByteOrder.nativeOrder());
    for(int i = 0; i < futureDynamicOperation.length; i++){
      if(futureDynamicOperation[i] == true){
        buffer2.putInt(1);
      } else{
        buffer2.putInt(0);
      }
    }
    buffer2.rewind();

    inputs = new ByteBuffer[2];
    inputs[0] = buffer2;
    inputs[1] = buffer1;
//    inputs[2] = buffer3;
//    inputs[3] = buffer4;
  }*/

  private void loadInputs(float[][] past_samples){

    ByteBuffer buffer1 = ByteBuffer.allocateDirect(past_samples.length * past_samples[0].length * Float.BYTES);
    buffer1.order(ByteOrder.nativeOrder());
    for(int i = 0; i < past_samples.length; i++) {
      for(int j = 0; j < past_samples[i].length; j++) {
        buffer1.putFloat(past_samples[i][j]);
      }
    }
    buffer1.rewind();

    inputs = new ByteBuffer[1];
    inputs[0] = buffer1;
  }

  /** Runs inference and returns the classification results. */
  /*public float[][] predict(float[][] userPositions, boolean[] futureDynamicOperation) {
    // Log this method so that it can be analyzed with systrace.
    Trace.beginSection("recognizeImage");

    Trace.beginSection("preprocessBitmap");
    loadInputs(userPositions, futureDynamicOperation);
    Trace.endSection();

    // Run the inference call.
    Trace.beginSection("runInference");
    long startTime = SystemClock.uptimeMillis();
    runInference();
    long endTime = SystemClock.uptimeMillis();
    Trace.endSection();

    return predictions_prob;
  }*/

  public float[][] predict(float[][] userPositions) {
    // Log this method so that it can be analyzed with systrace.
    Trace.beginSection("recognizeImage");

    Trace.beginSection("preprocessBitmap");
    loadInputs(userPositions);
    Trace.endSection();

    // Run the inference call.
    Trace.beginSection("runInference");
    long startTime = SystemClock.uptimeMillis();
    runInference();
    long endTime = SystemClock.uptimeMillis();
    Trace.endSection();

    return predictions_prob;
  }

  /** Closes the interpreter and model to release resources. */
  public void close() {
    if (tflite != null) {
      tflite.close();
      tflite = null;
    }
    tfliteModel = null;
  }

  /**
   * Get the image size along the x axis.
   *
   * @return
   */
  public abstract int getImageSizeX();

  /**
   * Get the image size along the y axis.
   *
   * @return
   */
  public abstract int getImageSizeY();

  /**
   * Get the name of the model file stored in Assets.
   *
   * @return
   */
  protected abstract String getModelPath();

  /**
   * Get the name of the label file stored in Assets.
   *
   * @return
   */
//  protected abstract String getLabelPath();
//
//  /**
//   * Get the number of bytes that is used to store a single color channel value.
//   *
//   * @return
//   */
//  protected abstract int getNumBytesPerChannel();
//
//  /**
//   * Add pixelValue to byteBuffer.
//   *
//   * @param pixelValue
//   */
//  protected abstract void addPixelValue(int pixelValue);
//
//  /**
//   * Read the probability value for the specified label This is either the original value as it was
//   * read from the net's output or the updated value after the filter was applied.
//   *
//   * @param labelIndex
//   * @return
//   */
//  protected abstract float getProbability(int labelIndex);
//
//  /**
//   * Set the probability value for the specified label.
//   *
//   * @param labelIndex
//   * @param value
//   */
//  protected abstract void setProbability(int labelIndex, Number value);
//
//  /**
//   * Get the normalized probability value for the specified label. This is the final value as it
//   * will be shown to the user.
//   *
//   * @return
//   */
//  protected abstract float getNormalizedProbability(int labelIndex);

  /**
   * Run inference using the prepared input in {@link #imgData}. Afterwards, the result will be
   * provided by getProbability().
   *
   * <p>This additional method is necessary, because we don't have a common base for different
   * primitive data types.
   */
  protected abstract void runInference();

  /**
   * Get the total number of labels.
   *
   * @return
   */
  protected int getNumLabels() {
    return 2;
  }
}
