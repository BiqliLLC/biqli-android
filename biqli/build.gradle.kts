import java.security.MessageDigest
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

plugins {
    id("com.android.library")
    `maven-publish`
    signing
}

android {
    namespace = "li.biq.sdk"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(rootProject.file("README.md"))
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
    from(rootProject.file("NOTICE")) {
        into("META-INF")
    }
}

tasks.withType<Jar>().configureEach {
    if (name == "releaseSourcesJar") {
        from(rootProject.file("LICENSE")) {
            into("META-INF")
        }
        from(rootProject.file("NOTICE")) {
            into("META-INF")
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "biqli-android"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            artifact(javadocJar)

            pom {
                name.set("Biqli Android SDK")
                description.set("Android App Link and deferred-install attribution SDK for Biqli")
                url.set("https://github.com/BiqliLLC/biqli-android")
                inceptionYear.set("2026")

                organization {
                    name.set("Biqli LLC")
                    url.set("https://biq.li")
                }

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("biqli")
                        name.set("Biqli LLC")
                        email.set("contact@biq.li")
                        organization.set("Biqli LLC")
                        organizationUrl.set("https://biq.li")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/BiqliLLC/biqli-android.git")
                    developerConnection.set("scm:git:ssh://github.com/BiqliLLC/biqli-android.git")
                    url.set("https://github.com/BiqliLLC/biqli-android")
                    tag.set("v${project.version}")
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/BiqliLLC/biqli-android/issues")
                }
            }
        }
    }

    repositories {
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

val signingKey = providers.environmentVariable("BIQLI_MAVEN_SIGNING_KEY").orNull
val signingPassword = providers.environmentVariable("BIQLI_MAVEN_SIGNING_PASSWORD").orNull
if (!signingKey.isNullOrBlank()) {
    signing {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

val stagingRepositoryDirectory = layout.buildDirectory.dir("staging-deploy")
val centralArtifactDirectory = stagingRepositoryDirectory.map {
    it.dir("li/biq/biqli-android/${project.version}")
}

val cleanCentralStaging = tasks.register<Delete>("cleanCentralStaging") {
    delete(stagingRepositoryDirectory)
}

val verifyCentralSigning = tasks.register("verifyCentralSigning") {
    doLast {
        if (signingKey.isNullOrBlank()) {
            throw GradleException("BIQLI_MAVEN_SIGNING_KEY is required to build a Central bundle.")
        }
        if (signingPassword.isNullOrBlank()) {
            throw GradleException("BIQLI_MAVEN_SIGNING_PASSWORD is required to build a Central bundle.")
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    if (repository.name == "staging" && publication.name == "release") {
        dependsOn(cleanCentralStaging, verifyCentralSigning)
    }
}

fun checksum(file: File, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

val generateCentralChecksums = tasks.register("generateCentralChecksums") {
    dependsOn("publishReleasePublicationToStagingRepository")

    doLast {
        val artifactDirectory = centralArtifactDirectory.get().asFile
        if (!artifactDirectory.isDirectory) {
            throw GradleException("Central artifact directory was not generated: $artifactDirectory")
        }

        val baseName = "biqli-android-${project.version}"
        val requiredArtifacts = listOf(
            "$baseName.aar",
            "$baseName.pom",
            "$baseName-sources.jar",
            "$baseName-javadoc.jar",
        )

        requiredArtifacts.forEach { name ->
            val artifact = artifactDirectory.resolve(name)
            if (!artifact.isFile) {
                throw GradleException("Required Central artifact is missing: $name")
            }
            if (!artifactDirectory.resolve("$name.asc").isFile) {
                throw GradleException("Required Central signature is missing: $name.asc")
            }
        }

        artifactDirectory.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile && Regex("\\.(md5|sha1|sha256|sha512)$").containsMatchIn(file.name)
            }
            .forEach(File::delete)

        artifactDirectory.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && !file.name.endsWith(".asc") }
            .forEach { file ->
                artifactDirectory.resolve("${file.name}.md5")
                    .writeText(checksum(file, "MD5"), Charsets.US_ASCII)
                artifactDirectory.resolve("${file.name}.sha1")
                    .writeText(checksum(file, "SHA-1"), Charsets.US_ASCII)
            }
    }
}

tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Builds the signed Maven-layout bundle for Central Publisher Portal."
    dependsOn(generateCentralChecksums)

    from(stagingRepositoryDirectory) {
        include("li/biq/biqli-android/${project.version}/**")
    }

    archiveFileName.set("biqli-android-${project.version}-central.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central-bundle"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
