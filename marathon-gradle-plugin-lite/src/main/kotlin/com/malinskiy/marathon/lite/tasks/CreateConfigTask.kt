package com.malinskiy.marathon.lite.tasks

import com.android.build.api.variant.BuiltArtifactsLoader
import com.malinskiy.marathon.cli.schema.AnalyticsConfiguration
import com.malinskiy.marathon.cli.schema.Configuration
import com.malinskiy.marathon.cli.schema.FilteringConfiguration
import com.malinskiy.marathon.cli.schema.ScreenRecordingPolicy
import com.malinskiy.marathon.cli.schema.VendorConfiguration
import com.malinskiy.marathon.cli.schema.android.AllureConfiguration
import com.malinskiy.marathon.cli.schema.android.FileSyncConfiguration
import com.malinskiy.marathon.cli.schema.android.ScreenRecordConfiguration
import com.malinskiy.marathon.cli.schema.android.SerialStrategy
import com.malinskiy.marathon.cli.schema.android.TimeoutConfiguration
import com.malinskiy.marathon.cli.schema.android.VendorType
import com.malinskiy.marathon.cli.schema.strategies.BatchingStrategy
import com.malinskiy.marathon.cli.schema.strategies.FlakinessStrategy
import com.malinskiy.marathon.cli.schema.strategies.PoolingStrategy
import com.malinskiy.marathon.cli.schema.strategies.RetryStrategy
import com.malinskiy.marathon.cli.schema.strategies.ShardingStrategy
import com.malinskiy.marathon.cli.schema.strategies.SortingStrategy
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

open class CreateConfigTask @Inject constructor(
    objects: ObjectFactory,
    projectLayout: ProjectLayout
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    internal val instrumentationApkDir: DirectoryProperty = objects.directoryProperty()

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    internal val apkFolder: DirectoryProperty = objects.directoryProperty()

    @get:Internal
    internal val builtArtifactsLoader: Property<BuiltArtifactsLoader> = objects.property()

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    internal val sdk: DirectoryProperty = objects.directoryProperty()

    @get:Input
    internal val flavorName: Property<String> = objects.property()

    @get:Input
    val name: Property<String> = objects.property()

    @get:Input
    val vendor: Property<VendorType> = objects.property()

    @get:Input
    val bugsnag: Property<Boolean> = objects.property()

    @get:Input
    val analyticsConfiguration: Property<AnalyticsConfiguration> = objects.property()

    @get:Input
    val poolingStrategy: Property<PoolingStrategy> = objects.property()

    @get:Input
    val shardingStrategy: Property<ShardingStrategy> = objects.property()

    @get:Input
    val sortingStrategy: Property<SortingStrategy> = objects.property()

    @get:Input
    val batchingStrategy: Property<BatchingStrategy> = objects.property()

    @get:Input
    val flakinessStrategy: Property<FlakinessStrategy> = objects.property()

    @get:Input
    val retryStrategy: Property<RetryStrategy> = objects.property()

    @get:Input
    val filteringConfiguration: Property<FilteringConfiguration> = objects.property()

    @get:Input
    val baseOutputDir: Property<String> = objects.property()

    @get:Input
    val ignoreFailures: Property<Boolean> = objects.property()

    @get:Input
    val codeCoverageEnabled: Property<Boolean> = objects.property()

    @get:Input
    val fallbackToScreenshots: Property<Boolean> = objects.property()

    @get:Input
    val strictMode: Property<Boolean> = objects.property()

    @get:Input
    val uncompletedTestRetryQuota: Property<Int> = objects.property()

    @get:Input
    val testClassRegexes: Property<Collection<String>> = objects.property()

    @get:Input
    val includeSerialRegexes: Property<Collection<String>> = objects.property()

    @get:Input
    val excludeSerialRegexes: Property<Collection<String>> = objects.property()

    @get:Input
    val testBatchTimeoutMillis: Property<Long> = objects.property()

    @get:Input
    val testOutputTimeoutMillis: Property<Long> = objects.property()

    @get:Input
    val debug: Property<Boolean> = objects.property()

    @get:Input
    val screenRecordingPolicy: Property<ScreenRecordingPolicy> = objects.property()

    @get:Input
    val applicationPmClear: Property<Boolean> = objects.property()

    @get:Input
    val testApplicationPmClear: Property<Boolean> = objects.property()

    @get:Input
    val adbInitTimeout: Property<Int> = objects.property()

    @get:Input
    val installOptions: Property<String> = objects.property()

    @get:Input
    val serialStrategy: Property<SerialStrategy> = objects.property()

    @get:Input
    val screenRecordConfiguration: Property<ScreenRecordConfiguration> = objects.property()

    @get:Input
    val analyticsTracking: Property<Boolean> = objects.property()

    @get:Input
    val deviceInitializationTimeoutMillis: Property<Long> = objects.property()

    @get:Input
    val waitForDevicesTimeoutMillis: Property<Long> = objects.property()

    @get:Input
    val allureConfiguration: Property<AllureConfiguration> = objects.property()

    @get:Input
    val timeoutConfiguration: Property<TimeoutConfiguration> = objects.property()

    @get:Input
    val fileSyncConfiguration: Property<FileSyncConfiguration> = objects.property()

    //Android specific for now
    @get:Input
    val autoGrantPermission: Property<Boolean> = objects.property()

    @get:Input
    val instrumentationArgs: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)


    @get:OutputFile
    val configOutput: Provider<RegularFile> = projectLayout.buildDirectory.file("Marathonfile");

    @TaskAction
    fun createConfig() {
        val instrumentationApk = instrumentationApkDir.asFileTree.single { it.extension == "apk" }
        val buildArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
        val applicationApk = buildArtifacts?.elements?.first()?.outputFile
        val baseOutputDir = File(baseOutputDir.getOrElse(project.buildDir.toString()), "reports/marathon")
        val output = File(baseOutputDir, flavorName.get())

        val vendorConfiguration = createAndroidConfiguration(applicationApk?.let { File(it) }, instrumentationApk)

        val cnf = Configuration(
            name.getOrElse("marathon"),
            output,
            analyticsConfiguration.get(),
            poolingStrategy.get(),
            shardingStrategy.get(),
            sortingStrategy.get(),
            batchingStrategy.get(),
            flakinessStrategy.get(),
            retryStrategy.get(),
            filteringConfiguration.get(),
            ignoreFailures.get(),
            codeCoverageEnabled.get(),
            fallbackToScreenshots.get(),
            strictMode.get(),
            uncompletedTestRetryQuota.get(),
            testClassRegexes.get().map { Regex(it) },
            includeSerialRegexes.get().map { Regex(it) },
            excludeSerialRegexes.get().map { Regex(it) },
            testBatchTimeoutMillis.get(),
            testOutputTimeoutMillis.get(),
            debug.get(),
            screenRecordingPolicy.get(),
            vendorConfiguration,
            analyticsTracking.get(),
            deviceInitializationTimeoutMillis.get()
        )
        saveConfig(cnf, configOutput.get().asFile)
    }

    private fun saveConfig(config: Configuration, output: File) {
        val yaml = Yaml()
        println(config)
        println(yaml.dumpAsMap(config))
        yaml.dump(config, FileWriter(output))
    }

    private fun createAndroidConfiguration(
        applicationApk: File?,
        instrumentationApk: File
    ): VendorConfiguration.Android {
        val autoGrantPermission = autoGrantPermission.get()
        val instrumentationArgs = instrumentationArgs.get()
        val applicationPmClear = applicationPmClear.get()
        val testApplicationPmClear = testApplicationPmClear.get()
        val adbInitTimeout = adbInitTimeout.get()
        val installOptions = installOptions.get()
        val screenRecordConfiguration = screenRecordConfiguration.get()
        val serialStrategy = serialStrategy.get()
        val waitForDevicesTimeoutMillis = waitForDevicesTimeoutMillis.get()
        val allureConfiguration = allureConfiguration.get()

        return VendorConfiguration.Android(
            androidSdk = sdk.get().asFile,
            applicationApk = applicationApk,
            testApplicationApk = instrumentationApk,
            autoGrantPermission = autoGrantPermission,
            instrumentationArgs = instrumentationArgs,
            applicationPmClear = applicationPmClear,
            testApplicationPmClear = testApplicationPmClear,
            adbInitTimeoutMillis = adbInitTimeout,
            installOptions = installOptions,
            screenRecordConfiguration = screenRecordConfiguration,
            serialStrategy = serialStrategy,
            waitForDevicesTimeoutMillis = waitForDevicesTimeoutMillis,
            allureConfiguration = allureConfiguration
        )
    }
}
