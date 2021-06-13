package com.malinskiy.marathon.vendor.junit4.booter.server

import com.malinskiy.marathon.vendor.junit4.contract.EventType
import com.malinskiy.marathon.vendor.junit4.contract.TestEvent
import com.malinskiy.marathon.vendor.junit4.contract.TestExecutorGrpcKt
import com.malinskiy.marathon.vendor.junit4.contract.TestRequest
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.Result
import org.junit.runner.manipulation.Filter
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

class TestExecutorServer(private val port: Int) {
    private val server: Server = ServerBuilder
        .forPort(port)
        .addService(TestExecutorService())
        .build()

    fun start() {
        server.start()
        println("Server started, listening on $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("*** shutting down gRPC server since JVM is shutting down")
                this@TestExecutorServer.stop()
                println("*** server shut down")
            }
        )
    }

    private fun stop() {
        server.shutdown()
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }

    private class TestExecutorService : TestExecutorGrpcKt.TestExecutorCoroutineImplBase() {
        private val core = JUnitCore()

        override fun execute(request: TestRequest): Flow<TestEvent> {
            val tests = request.testDescriptionList

            val klasses = mutableSetOf<Class<*>>()
            val testDescriptions = tests.map { test ->
                val fqtn = test.fqtn
                val klass = fqtn.substringBefore('#')
                val loadClass = Class.forName(klass)
                klasses.add(loadClass)

                val method = fqtn.substringAfter('#')
                Description.createTestDescription(loadClass, method)
            }.toHashSet()

            val testFilter = TestFilter(testDescriptions)
            val request = Request.classes(*klasses.toTypedArray())
                .filterWith(testFilter)

//            val request = Request.classes(Class.forName("com.atlassian.renderer.v2.functional.TestLink"))
//                .filterWith(
//                    TestFilter(
//                        hashSetOf(
//                            Description.createTestDescription(
//                                Class.forName("com.atlassian.renderer.v2.functional.TestLink"),
//                                "link2 raw"
//                            )
//                        )
//                    )
//                )

            return callbackFlow {
                val callback = object : RunListener() {
                    override fun testRunStarted(description: Description) {
                        super.testRunStarted(description)
//                        println("Started: ${description}")
                        try {
                            sendBlocking(
                                TestEvent.newBuilder()
                                    .setEventType(EventType.RUN_STARTED)
                                    .build()
                            )
                        } catch (e: Exception) {
                            // Handle exception from the channel: failure in flow or premature closing
                        }
                    }

                    override fun testRunFinished(result: Result) {
                        super.testRunFinished(result)
                        try {
                            sendBlocking(
                                TestEvent.newBuilder()
                                    .setEventType(EventType.RUN_FINISHED)
                                    .setTotalDurationMillis(result.runTime)
                                    .build()
                            )
                        } catch (e: Exception) {
                            // Handle exception from the channel: failure in flow or premature closing
                        }

                        channel.close()
                    }

                    override fun testStarted(description: Description) {
                        super.testStarted(description)
                        val description = description.toActualDescription(testFilter.actualClassLocator)
                        try {
                            sendBlocking(
                                TestEvent.newBuilder()
                                    .setEventType(EventType.TEST_STARTED)
                                    .setClassname(description.className)
                                    .setMethod(description.methodName)
                                    .setTestCount(description.testCount())
                                    .build()
                            )
                        } catch (e: Exception) {
                            // Handle exception from the channel: failure in flow or premature closing
                        }
                    }

                    override fun testFinished(description: Description) {
                        super.testFinished(description)
                        val description = description.toActualDescription(testFilter.actualClassLocator)
                        try {
                            sendBlocking(
                                TestEvent.newBuilder()
                                    .setEventType(EventType.TEST_FINISHED)
                                    .setClassname(description.className)
                                    .setMethod(description.methodName)
                                    .build()
                            )
                        } catch (e: Exception) {
                            // Handle exception from the channel: failure in flow or premature closing
                        }
                    }

                    override fun testFailure(failure: Failure) {
                        super.testFailure(failure)
                        val description = failure.description.toActualDescription(testFilter.actualClassLocator)
//                        println(failure.exception.cause?.printStackTrace())
                        try {
                            sendBlocking(
                                TestEvent.newBuilder()
                                    .setEventType(EventType.TEST_FAILURE)
                                    .setClassname(description.className)
                                    .setMethod(description.methodName)
                                    .setMessage(failure.message)
                                    .setStacktrace(failure.trace)
                                    .build()
                            )
                        } catch (e: Exception) {
                            // Handle exception from the channel: failure in flow or premature closing
                        }
                    }

                    override fun testAssumptionFailure(failure: Failure) {
                        super.testAssumptionFailure(failure)
                        val description = failure.description.toActualDescription(testFilter.actualClassLocator)
                        try {
                            sendBlocking(
                                TestEvent.newBuilder()
                                    .setEventType(EventType.TEST_ASSUMPTION_FAILURE)
                                    .setClassname(description.className)
                                    .setMethod(description.methodName)
                                    .setMessage(failure.message)
                                    .setStacktrace(failure.trace)
                                    .build()
                            )
                        } catch (e: Exception) {
                            // Handle exception from the channel: failure in flow or premature closing
                        }
                    }

                    override fun testIgnored(description: Description) {
                        super.testIgnored(description)
                        val description = description.toActualDescription(testFilter.actualClassLocator)
                        try {
                            sendBlocking(
                                TestEvent.newBuilder()
                                    .setEventType(EventType.TEST_IGNORED)
                                    .setClassname(description.className)
                                    .setMethod(description.methodName)
                                    .build()
                            )
                        } catch (e: Exception) {
                            // Handle exception from the channel: failure in flow or premature closing
                        }
                    }
                }
                core.addListener(callback)
                val result = core.run(request)
//                println(
//                    """
//                    Success: ${result.wasSuccessful()}
//                    Tests: ${result.runCount}
//                    Ignored: ${result.ignoreCount}
//                    Failures: ${result.failureCount}
//                    ${result.failures.joinToString("\n") { "${it.description.displayName}: ${it.message}" }}
//                    """.trimIndent()
//                )
//                result.failures.forEach {
//                    println(
//                        """
//                            ${it.testHeader}
//                            ${it.message}: ${it.description}
//                            ${it.exception}
//                            ${it.trace}
//                        """.trimIndent()
//                    )
//                }
                awaitClose {
                    core.removeListener(callback)
                }
            }
        }
    }
}

private fun Description.toActualDescription(actualClassLocator: MutableMap<Description, String>): Description {
    return if(className == TestFilter.CLASS_NAME_STUB) {
        Description.createTestDescription(actualClassLocator[this], methodName, *annotations.toTypedArray())
    } else {
        this
    }
}

class TestFilter(private val testDescriptions: HashSet<Description>) : Filter() {
    private val verifiedChildren = mutableSetOf<Description>()
    val actualClassLocator = mutableMapOf<Description, String>()

    override fun shouldRun(description: Description): Boolean {
//        println("JUnit asks about $description")

        return if (verifiedChildren.contains(description)) {
//            println("Already unfiltered $description before")
            true
        } else {
            shouldRun(description, className = null)
        }
    }

    fun shouldRun(description: Description, className: String?): Boolean {
        if (description.isTest) {
//            println("$description")
            /**
             * Handling for parameterized tests that report org.junit.runners.model.TestClass as their test class
             */
            val verificationDescription = if (description.className == CLASS_NAME_STUB && className != null) {
                Description.createTestDescription(className, description.methodName, *description.annotations.toTypedArray())
            } else {
                description
            }
            val contains = testDescriptions.contains(verificationDescription)
            if (contains) {
                verifiedChildren.add(description)
                if(description.className == CLASS_NAME_STUB && className != null) {
                    actualClassLocator[description] = className
                }
            }

            return contains
        }

        // explicitly check if any children want to run
        for (each in description.children) {
//            println("$description")
            if (shouldRun(each!!, description.className)) {
                return true
            }
        }
        return false
    }

    override fun describe() = "Marathon JUnit4 execution filter"
    
    companion object {
        const val CLASS_NAME_STUB = "org.junit.runners.model.TestClass"
    }
}
