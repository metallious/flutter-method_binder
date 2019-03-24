package de.suitepad.methodbinder.annotations

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class MethodChannel(val channelName: String)