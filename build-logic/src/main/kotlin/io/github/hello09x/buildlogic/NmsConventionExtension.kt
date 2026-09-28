package io.github.hello09x.buildlogic

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class NmsConventionExtension @Inject constructor(objects: ObjectFactory) {
    val javaVersion: Property<Int> = objects.property(Int::class.java)
    val toolchainVersion: Property<Int> = objects.property(Int::class.java)
    val nmsVersion: Property<String> = objects.property(String::class.java)
    val lombok: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val coreProvided: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val dependsOn: Property<String> = objects.property(String::class.java)
    val transitive: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val remappedMojang: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
}
