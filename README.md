# TOUCAN_VR

TOUCAN_VR allows to stream and play a DASH-SRD described content created with [1].
The application implements the buffering strategy, replacement or descarding techniques and dynamic editing described in [2].
TOUCAN_VR has been developed and tested on Samsung Galaxy S7 edge running Android 7, together with the Samsung Gear VR headset.
The application has been built on top of Exoplayer 2 which in turn leverages the Samsung Gear VR Framework to display VR scenes.

Created by:

Universite Nice Sophia Antipolis (Universite Cote d'Azur) and CNRS  
Laboratoire d'Informatique, Signaux et Systèmes de Sophia Antipolis (I3S)

Contributors:

Savino DAMBRA  
Giuseppe SAMELA  
Lucile SASSATELLI  
Romaric PIGHETTI  
Ramon APARICIO-PARDO  
Anne-Marie PINNA-DERY

References:

[1] TOUCAN-Preprocessing https://github.com/UCA4SVR/TOUCAN-preprocessing     
[2] S. Dambra, G. Samela, L. Sassatelli, R. Pighetti, R. Aparicio-Pardo, A. Pinna-Déry "Film Editing: New Levers to Improve VR Streaming", submitted

## Contributing
If you want to contribute to the project, your help is very welcome. 
We suggest the following contribution instructions:

1. Create a personal fork of the project on Github
2. Clone the fork on your local machine, it will be the point where your contribution starts from.
3. Create a new branch to work on. Our working branch is either develop or feature/discarding_bynary_strategy (it depends whether you want to work on the discarding or replacement strategy), so branch from those.
4. Implement/fix your feature, comment your code.
5. Follow the code style of the project, including indentation.
6. Push your commits with a message explaining the work you've done.
7. From your fork, open a pull request in the correct branch. You can find useful instruction about opening pull requests from forked projects here https://help.github.com/articles/creating-a-pull-request-from-a-fork/. Target develop or feature/discarding_bynary_strategy branch depending on what branch you forked from.
8. Once your pull request is accepted, you can pull the changes from the original repository.

## Contact
For any question, please send an email to githubprojecttoucanvr@gmail.com

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
