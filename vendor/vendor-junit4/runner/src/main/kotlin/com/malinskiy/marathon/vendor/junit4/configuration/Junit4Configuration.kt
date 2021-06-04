package com.malinskiy.marathon.vendor.junit4.configuration

import com.malinskiy.marathon.log.MarathonLogConfigurator
import com.malinskiy.marathon.vendor.VendorConfiguration
import com.malinskiy.marathon.vendor.junit4.AsmTestParser
import com.malinskiy.marathon.vendor.junit4.Junit4DeviceProvider
import org.koin.core.KoinComponent
import org.koin.core.get
import java.io.File

data class Junit4Configuration(
    val applicationClasspath: List<File>,
    val testClasspath: List<File>,
) : VendorConfiguration, KoinComponent {
    override fun logConfigurator(): MarathonLogConfigurator = Junit4LogConfigurator()

    override fun testParser() = AsmTestParser()

    override fun deviceProvider() = Junit4DeviceProvider(get())
}
