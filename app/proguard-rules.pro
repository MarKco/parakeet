# Legacy Java-serialized class read during migration from v3.x
-keep class com.ilsecondodasinistra.parakeet.ThingToDo { *; }
-keepnames class java.util.LinkedList

# ML Kit bundled barcode scanner (used to read the QR code that stops the alarm).
# Its components are created by reflection from the manifest, and its native library (libbarhopper) calls
# back into Java classes by name via JNI: R8 must not strip or rename them (release crashed on QR scan).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.** { <init>(); }
-keep class com.google.android.libraries.barhopper.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
