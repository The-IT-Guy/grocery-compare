# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Jsoup
-keeppackagenames org.jsoup.nodes

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.theitguy.grocerycompare.data.models.** { *; }

# Compose
-keep class androidx.compose.** { *; }
