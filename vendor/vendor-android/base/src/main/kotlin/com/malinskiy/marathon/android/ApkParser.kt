package com.malinskiy.marathon.android

import com.shazam.axmlparser.AXMLParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

class ApkParser {
    @Suppress(
        "ComplexMethod",
        "ThrowsCount",
        "TooGenericExceptionThrown",
        "NestedBlockDepth"
    )
    fun parseInstrumentationInfo(apk: File): InstrumentationInfo {
        var apkInputStream: InputStream? = null
        try {
            val zip = ZipFile(apk)
            val entry = zip.getEntry("AndroidManifest.xml")
            apkInputStream = zip.getInputStream(entry)
            return parseAndroidManifest(apkInputStream)
        } catch (e: IOException) {
            throw RuntimeException("Unable to parse test app AndroidManifest.xml.", e)
        } finally {
            apkInputStream?.close()
        }
    }

    fun parseAndroidManifest(input: InputStream): InstrumentationInfo {
        val parser = AXMLParser(input)
        var eventType = parser.type

        var appPackage: String? = null
        var testPackage: String? = null
        var testRunnerClass: String? = null
        while (eventType != AXMLParser.END_DOCUMENT) {
            if (eventType == AXMLParser.START_TAG) {
                val parserName = parser.name
                val isManifest = "manifest" == parserName
                val isInstrumentation = "instrumentation" == parserName
                if (isManifest || isInstrumentation) {
                    for (i in 0 until parser.attributeCount) {
                        val parserAttributeName = parser.getAttributeName(i)
                        if (isManifest && "package" == parserAttributeName) {
                            testPackage = parser.getAttributeValueString(i)
                        } else if (isInstrumentation && "targetPackage" == parserAttributeName) {
                            appPackage = parser.getAttributeValueString(i)
                        } else if (isInstrumentation && "name" == parserAttributeName) {
                            testRunnerClass = parser.getAttributeValueString(i)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        checkNotNull(testRunnerClass) { "Could not find test runner class." }
        checkNotNull(testPackage) { "Could not find test application package." }
        checkNotNull(appPackage) { "Could not find application package." }

        // Support relative declaration of instrumentation test runner.
        if (testRunnerClass.startsWith(".")) {
            testRunnerClass = testPackage + testRunnerClass
        } else if (!testRunnerClass.contains(".")) {
            testRunnerClass = "$testPackage.$testRunnerClass"
        }

        return InstrumentationInfo(appPackage, testPackage, testRunnerClass)
    }
}
