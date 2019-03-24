package de.suitepad.methodbinder

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import de.suitepad.methodbinder.annotations.MethodChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.view.FlutterView
import org.jetbrains.annotations.Nullable

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.tools.Diagnostic
import java.io.File
import java.io.IOException
import java.util.HashSet
import javax.lang.model.element.*


@AutoService(Processor::class)
class MethodBinderProcessor : AbstractProcessor() {

    companion object {

        val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }


    override fun getSupportedAnnotationTypes(): Set<String> {
        val supportedAnnotations = HashSet<String>()
        supportedAnnotations.add(MethodChannel::class.java.name)
        return supportedAnnotations
    }

    override fun process(set: Set<TypeElement>, roundEnvironment: RoundEnvironment): Boolean {
        val annotatedElements = roundEnvironment.getElementsAnnotatedWith(MethodChannel::class.java)
        for (element in annotatedElements) {
            if (element.kind != ElementKind.INTERFACE) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Only classes can be annotated with MethodChannel"
                )
                return true
            }
            try {
                processAnnotation(element)
            } catch (e: IOException) {
                e.printStackTrace()
                processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "error while writing generated code")
                return true
            }

        }
        return false
    }

    @Throws(IOException::class)
    private fun processAnnotation(element: Element) {
        val className = element.simpleName.toString()
        val packageName = processingEnv.elementUtils.getPackageOf(element).toString()
        val fileName = "MethodChannel$className"

        val fileBuilder = FileSpec.builder(packageName, fileName)
        val classBuilder = TypeSpec.interfaceBuilder(fileName).addSuperinterface(element.asType().asTypeName())

        val channelName = element.getAnnotation(MethodChannel::class.java).channelName
        classBuilder.addFunction(
            FunSpec.builder("registerChannel")
                .addModifiers(KModifier.PUBLIC)
                .addParameter(ParameterSpec.builder("flutterView", FlutterView::class.java).build())
                .addStatement(
                    "%T(flutterView, \"%L\").setMethodCallHandler(%L)",
                    io.flutter.plugin.common.MethodChannel::class.java,
                    channelName,
                    generateMethodhandlerFor(element)
                )
                .build()
        )

        val file = fileBuilder.addType(classBuilder.build()).build()
        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        file.writeTo(File(kaptKotlinGeneratedDir))
    }

    fun generateMethodhandlerFor(element: Element): TypeSpec {
        val handleMethod = FunSpec.builder("onMethodCall")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("methodCall", MethodCall::class.java)
            .addParameter("result", io.flutter.plugin.common.MethodChannel.Result::class.java)
            .beginControlFlow("when (methodCall.method)").also { builder ->
                element.enclosedElements.filter { it.kind == ElementKind.METHOD }.forEach { method ->
                    builder.beginControlFlow("\"${method.simpleName}\" -> ")
                    val paramList =
                        (method as ExecutableElement).parameters.filter { it.kind == ElementKind.PARAMETER }.also {
                            it.forEach { param ->
                                builder.beginControlFlow("if (!methodCall.hasArgument(\"%L\"))", param.simpleName)
                                builder.addStatement("result.notImplemented()")
                                builder.endControlFlow()
                                val paramType = param.asType()
                                builder.addStatement("val ${param.simpleName} = methodCall.argument<%T>(\"${param.simpleName}\")",
                                    paramType
                                )
                                if (param.getAnnotation(Nullable::class.java) == null) {
                                    builder.beginControlFlow("if (${param.simpleName} == null)")
                                    builder.addStatement("result.notImplemented()")
                                    builder.addStatement("return")
                                    builder.endControlFlow()
                                }
                            }
                        }.map { it.simpleName }

                    builder.addStatement("val res = ${method.simpleName}(${paramList.joinToString(", ")})")
                    builder.addStatement("result.success(res)")
                    builder.endControlFlow()
                }
            }
            .beginControlFlow("else ->")
            .addStatement("result.notImplemented()")
            .endControlFlow()
            .endControlFlow()
            .build()
        return TypeSpec.anonymousClassBuilder()
            .addSuperinterface(io.flutter.plugin.common.MethodChannel.MethodCallHandler::class.java)
            .addFunction(handleMethod)
            .build()
    }

}
