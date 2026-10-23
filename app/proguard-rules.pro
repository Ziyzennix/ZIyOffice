# ZiyOffice release ProGuard rules.
# Kept minimal; the app has no reflection-heavy frameworks at this stage.
-dontwarn org.jetbrains.annotations.**

# Tesseract and Leptonica JNI classes and native methods
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-dontwarn com.googlecode.tesseract.android.**
-dontwarn com.googlecode.leptonica.android.**
