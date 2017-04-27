# toucan

V 0.1.0

+ Plays a 360 video in the GearVR headset using exoplayer 2 and the GearVR framework
+ Has a welcome screen asking to tap the trackpad on the side of the headset to start the playback
+ Has an end screen asking to remove the headset
+ Records the head motion of the user during the playback to a file (if storage permission is granted)
+ Display a "tap to grant permission" screen if permissions are lacking to play the video. If
permissions are not granted after a few calls, you need to restart the app and accept the permissions.
+ Storage permissions are required to display videos stored on the phone, activating them will
automatically turn the logging of the headmotion (and other logging in the future) on for now.

This application can be launched through the Toucan_parametrizer app using an intent.
For now, the intent is not read and the path to the video displayed is hard coded.