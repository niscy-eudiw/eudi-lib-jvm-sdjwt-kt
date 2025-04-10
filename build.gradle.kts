import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    // alias(libs.plugins.kotlinx.knit)
    // alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dependency.check)
}

repositories {
    mavenCentral()
    mavenLocal()
    google()
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        optIn =
            listOf(
                "kotlin.io.encoding.ExperimentalEncodingApi",
                "kotlin.contracts.ExperimentalContracts",
            )
        freeCompilerArgs =
            listOf(
                "-Xconsistent-data-class-copy-visibility",
            )
    }

    // JVM target
    jvm()

    // Android target
    androidTarget {
        // Set JVM target to 17 to match Java compatibility
        // Using direct property access instead of deprecated kotlinOptions
        JvmTarget.fromTarget(libs.versions.java.get())
            .let { javaTarget ->
                compilations.all {
                    compileTaskProvider.configure {
                        compilerOptions.jvmTarget.set(javaTarget)
                    }
                }
            }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withAndroidTarget()
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.client.serialization)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.coroutines.debug)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.logging)
            }
        }
        val jvmAndAndroidMain by getting {
            dependencies {
                api(libs.nimbus.jose.jwt)
            }
        }
        val jvmAndAndroidTest by getting {
            dependencies {
                implementation(libs.tink)
                implementation(libs.ktor.client.java)
                implementation(libs.logback.classic)
                implementation(libs.bouncy.castle)
                implementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
                implementation("org.junit.jupiter:junit-jupiter-engine:5.9.3")
                implementation("org.junit.jupiter:junit-jupiter-params:5.9.3")
            }
        }
    }
}

// Android configuration
android {
    namespace = properties["namespace"].toString()
    group = properties["group"].toString()
    compileSdk = properties["android.targetSdk"].toString().toInt()

    defaultConfig {
        minSdk = properties["android.minSdk"].toString().toInt()
    }

    compileOptions {
        JavaVersion.toVersion(libs.versions.java.get().toInt())
            .let { javaVersion ->
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
    }
}

// knit {
//    rootDir = project.rootDir
//    files = fileTree(project.rootDir) {
//        include("docs/examples/**/*.md")
//        include("README.md")
//    }
//    defaultLineSeparator = "\n"
// }

spotless {
    kotlin {
        ktlint(libs.versions.ktlintVersion.get())
        licenseHeaderFile("FileHeader.txt")
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(libs.versions.ktlintVersion.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

object Meta {
    const val BASE_URL = "https://github.com/eu-digital-identity-wallet/eudi-lib-jvm-sdjwt-kt"
}
// tasks.withType<DokkaTask>().configureEach {
//    dokkaSourceSets {
//        named("main") {
//            // used as project name in the header
//            moduleName.set("EUDI SD-JWT")
//
//            // contains descriptions for the module and the packages
//            includes.from("Module.md")
//
//            documentedVisibilities.set(
//                setOf(
//                    DokkaConfiguration.Visibility.PUBLIC,
//                    DokkaConfiguration.Visibility.PROTECTED,
//                ),
//            )
//
//            val remoteSourceUrl = System.getenv()["GIT_REF_NAME"]?.let { URI.create("${Meta.BASE_URL}/tree/$it/src").toURL() }
//            remoteSourceUrl
//                ?.let {
//                    sourceLink {
//                        localDirectory.set(projectDir.resolve("src"))
//                        remoteUrl.set(it)
//                        remoteLineSuffix.set("#L")
//                    }
//                }
//        }
//    }
// }

mavenPublishing {
    pom {
        ciManagement {
            system = "github"
            url = "${Meta.BASE_URL}/actions"
        }
    }
}

val nvdApiKey: String? = System.getenv("NVD_API_KEY") ?: properties["nvdApiKey"]?.toString()
val dependencyCheckExtension = extensions.findByType(DependencyCheckExtension::class.java)
dependencyCheckExtension?.apply {
    formats = mutableListOf("XML", "HTML")
    nvd.apiKey = nvdApiKey ?: ""
}
