package de.suitepad.fluttermethodbinder

import de.suitepad.methodbinder.annotations.MethodChannel
import java.lang.String

@MethodChannel("testChannelName")
interface TestInterface {

    fun testMethod(testParam: String?)

    fun anotherMethod(param1: Integer, param2: String?)

}