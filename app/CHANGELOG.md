# CHANGELOG

## V 0.4.0
+ Now uses the intent to setup the parameters
  + Supported parameters:
  
    |         Key         |  Type   |                           Description                           |
    |---------------------|---------|-----------------------------------------------------------------|
    | videoLink           | String  | URI to the video                                                |
    | videoName           | String  | Prefix for the logs files                                       |
    | minBufferSize       | Integer | Minimum size of buffer the player tries to ensure               |
    | maxBufferSize       | Integer | Maximum size of the buffer                                      |
    | bufferForPlayback   | Integer | Size of the startup buffer                                      |
    | bufferForPlaybackAR | Integer | Size of the startup buffer after rebuffering events             |
    | bandwidthLogging    | Boolean | Do we enable bandwidth logging ?                                |
    | headMotionLogging   | Boolean | Do we enable head motion logging ?                              |
    | W                   | Integer | Width of the grid used to define tiles                          |
    | H                   | Integer | Height of the grid used to define tiles                         |
    | tilesCSV            | String  | Definition of the tiles in csv containing x,y,w,h for each tile |
+ Default values are provided if the app is launched without using an intent 
(intended for development purposes only).

## V 0.3.1
+ No hole in the top of the sphere
+ Texture applied correctly on the hole sphere

## V 0.3.0
+ Now handling dash SRD
+ One renderer is used for each tile
+ Can create a tiled sphere object to display the video

## V 0.2.0
+ Now uses exoplayer 2
+ Logging with logback
+ Logs bandwidth consumption
+ Logs head motion
+ Support for permissions queries

## V 0.1.0
+ Reads videos to the Gear VR HMD using exoplayer