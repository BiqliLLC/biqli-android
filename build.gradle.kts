plugins {
    id("com.android.library") version "9.4.0" apply false
    id("com.android.application") version "9.4.0" apply false
}

allprojects {
    group = "li.biq"
    version = providers.gradleProperty("VERSION_NAME").get()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
