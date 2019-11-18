# TOUCAN_VR

TOUCAN_VR allows to stream and play a DASH-SRD described content created with [1].
The application implements the buffering strategy, replacement or descarding techniques and dynamic editing described in [2].
TOUCAN_VR has been developed and tested on Samsung Galaxy S7 edge running Android 7, together with the Samsung Gear VR headset.
The application has been built on top of Exoplayer 2 which in turn leverages the Samsung Gear VR Framework to display VR scenes.

[1] TOUCAN-Preprocessing https://github.com/UCA4SVR/TOUCAN-preprocessing
[2] S. Dambra, G. Samela, L. Sassatelli, R. Pighetti, R. Pardo, A. Pinna-Déry "Film Editing: New Levers to Improve VR Streaming"

## Build and install

The repository provides an Android Studio project. Clone or download it, import the project in Android Studio, let Gradle check for dependencies and install the application in your favourite device.
The Oculus Mobile SDK is required for the Samsung Gear VR framwork to work. For further details please check http://www.gearvrf.org/getting_started/

## Mandatory requirement

TOUCAN_VR retrieves all network configuration parameters, video to be played and logs to be recorded from the TOUCAN_VR-Parametrizer Application (https://github.com/UCA4SVR/TOUCAN_VR_parametrizer). 
TOUCAN_VR must be started from TOUCAN_VR-Parametrizer. 
Launching TOUCAN_VR from the launcher will result in a screen error. 
You may want to test the application with a fix input and a set of predifined parameters: please check the file PlayerActivity.java and tune the functions "isIntentValid(intent)" and "parseIntent()".

## TOUCAN_VR lifecycle

TOUCAN_VR requires a Samsung Gear VR headset to run. 
For testing purposes, it is possible to run the application outside the headset enabling the developer mode in the Oculus application.

The following permissions are required:

1. READ_EXTERNAL_STORAGE
2. WRITE_EXTERNAL_STORAGE
3. ACCESS_NETWORK_STATE
4. INTERNET

**If permissions are not granted, an error screen will appear together with the request for the missing permission.**

**If TOUCAN_VR is not started from TOUCAN_VR-Parametrizer an error screen will be shown.**

When TOUCAN_VR is correctly invoked from TOUCAN_VR-Parametrizer, the following checks are carried out:

1. Internet connection (if the video is remote)
2. Existence of the MPD file

**An error screen will appear if checks fail.**

When TOUCAN_VR is correctly started and the streaming source is reachable, a screen suggests to tap the touchpad of the Samsung Gear VR headset to start the playback of the content. The following tap events will pause and resume the playback. At the end of the content, a screen suggest the user to remove the headset.

It is enough to resume TOUCAN_VR-Parametrizer and select a new content to restart TOUCAN_VR with the updated information.

## How to use TensorFlow-Lite model

You need to drop the tflite model in "src/main/assets" directory

## Prerequisites

Java
Android Gradle Plugin Version 3.2.1
Gradle Version 4.6
TensorFlow
TensorFlow-Lite

## TODO

- How to introduce TensorFlow-Lite model to take the decision to trigger the DynamicOperations found on an Xml file ? (check the updateSelectedTrack() function in PyramidalTrackSelection.java)

### Known structure

The core of the app is located in "PlayerActivity.java". In this file, the method : "private GVRVideoSceneObjectPlayer<ExoPlayer> makeVideoSceneObject() {...}" is used to create the video player scene object which holds the instance of the exoplayer.
The player needs a "TrackSelector" object for selecting the tracks to be consumed by each of the player's Renderers. 
"TrackSelector" uses a "PyramidalTrackSelection" class to update the tracks selected before downloading them through the "updateSelectedTrack" method.
I changed this function to use the Tensorflow-Lite model to take the decision to weither or not trigger the DynamicOperations from "dynamicEditingHolder" variable.
This part of the code is still in development and I havent been able to test it yet because it looks like this function is never called in the current program.

The use of the Tensorflow-Lite model should be correct but I'm not sure about how to implement this app's code to take a decision to trigger a DynamicOperations at equal time intervals.


### Known issues

- There is a error/warning message coming from OurChunkSampleStream.discardDownstreamMediaChunks(...) when the TOUCAN-VR-parametrizer app doesn't use the "Realtime user position" option. This error has been introduce in order too share the quality of each tiles to the computer but this error doesn't affect anything else in the app. Just adding a condition in this function might be sufficient to get ride of this error message.
- The app crash sometimes at the end of the video. It might be because of one of the last modification of the code (check git commits).



### Resources

- https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/ExoPlayer.html
- https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/trackselection/TrackSelector.html
- https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/trackselection/TrackSelection.html
- https://medium.com/google-exoplayer/exoplayer-2-x-track-selection-2b62ff712cc9
