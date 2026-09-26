# Keep MediaPipe / TFLite classes referenced via reflection (wired in later stages).
-keep class com.google.mediapipe.** { *; }
-keep class org.tensorflow.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn org.tensorflow.**
