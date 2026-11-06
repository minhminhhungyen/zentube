# Không đổi tên lớp – giúp dễ đọc lỗi và tránh hỏng phần phản chiếu (reflection)
-dontobfuscate

# NewPipeExtractor + các thư viện nó dùng (Rhino để giải mã chữ ký, jsoup, nanojson)
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.grack.nanojson.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
