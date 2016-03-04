android-rotation-sensor-sample
==============================
This is a sample application that uses the Android rotation sensor and displays the device rotation (pitch/roll) with a custom view (an attitude indicator, aka "artificial horizon").

It shows proper usage of the following Android features:
- Monitoring the rotation vector sensor (but only while the activity is resumed/running).
- Converting the raw rotation matrix to pitch and roll, measured in degrees.
- Adjusting the rotation values based on the device orientation (e.g. portrait vs. landscape).
- Drawing a custom view, including using Porter-Duff transfer mode to create an anti-aliased circle cut-out.

![Screenshot](screenshot.png)