# TOUCAN_VR

Description

## Build and install

The repository provides an Android Studio project. Clone or download it, import the project in Android Studio, let Gradle check for dependencies and install the application in your favourite device.

## Mandatory requirement

TOUCAN_VR retrieves all network configuration parameters, video to be played and logs to be recorded from the TOUCAN_VR Application (https://github.com/UCA4SVR/TOUCAN_VR_parametrizer). TOUCAN_VR must be started from TOUCAN_VR_Parametrizer. Launching TOUCAN_VR from the launcher will result in an error. 
You may want to test the application with a fix input and a set of predifined parameters: please tune the file PlayerActivity.java and its functions isIntentValid(intent) and parseIntent().

## Permissions

Toucan_VR requires the following permissions:

1. READ_EXTERNAL_STORAGE
2. WRITE_EXTERNAL_STORAGE
3. ACCESS_NETWORK_STATE
4. INTERNET

## Playback

TOUCAN_VR requires a Samsung Gear VR to run. 
For testing purposes, it is possible to run the application outside the headset enabling the developer mode of the Samsung GVR framweork.
When TOUCAN_VR is correctly started, the streaming source is reachable, and the device is inserted into a Samsung Gear VR, a tap on the headset is required to start the playback of the content. The following tap events will pause and resume the playback.
