# RxJava 0.21

-keep class rx.schedulers.Schedulers {
    public static <methods>;
}

-keep class rx.schedulers.ImmediateScheduler {
    public <methods>;
}

-keep class rx.schedulers.TestScheduler {
    public <methods>;
}

-keep class rx.schedulers.Schedulers {
    public static ** test();
}

-keep class rx.internal.util.unsafe.** { *; }

-keepnames class rx.internal.util.unsafe.**

-keepclassmembernames class rx.internal.util.unsafe.**

-dontwarn rx.internal.operators.**