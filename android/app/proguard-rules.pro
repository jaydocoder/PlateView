# 保留通过 Gson 反射读取的更新清单字段。
-keepclassmembers class com.jaydocoder.plateview.data.update.** {
    <fields>;
}

# 保留 WorkManager 通过清单和反射创建的 Worker。
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(...);
}

# 保留 Room 生成数据库需要的无参入口。
-keep class * extends androidx.room.RoomDatabase { *; }

# SQLCipher 通过 JNI 按类名和字段名访问 Java 层，不能让 R8 重命名或移除。
-keep class net.sqlcipher.** { *; }
