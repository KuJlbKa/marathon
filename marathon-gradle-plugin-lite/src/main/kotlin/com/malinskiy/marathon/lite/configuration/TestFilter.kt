package com.malinskiy.marathon.lite.configuration

import java.io.Serializable

sealed class TestFilter : Serializable {
    data class SimpleClassname(val simpleClassname: Regex) : TestFilter()
    data class FullyQualifiedClassname(val fullyQualifiedClassname: Regex) : TestFilter()
    data class TestPackage(val `package`: Regex) : TestFilter()
    data class Annotation(val annotation: Regex) : TestFilter()
    data class TestMethod(val method: Regex) : TestFilter()
    data class Composition(val composition: List<TestFilter>, val op: OPERATION) : TestFilter()
    enum class OPERATION {
        UNION,
        INTERSECTION,
        SUBTRACT;
    }
}
