# LiteRT native bridge
-keep class com.google.ai.edge.litert.** { *; }
# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class de.pilzscout.** { kotlinx.serialization.KSerializer serializer(...); }
