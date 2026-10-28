plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

// Coverage gate for the DoD (≥ 90 % lines on :domain): ./gradlew :domain:jacocoTestCoverageVerification
apply(plugin = "jacoco")
tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
}
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test)
    violationRules { rule { limit { counter = "LINE"; minimum = "0.90".toBigDecimal() } } }
}
