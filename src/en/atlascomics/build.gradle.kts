import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

android {
    sourceSets {
        named("test") {
            java.directories.add("test")
            kotlin.directories.add("test")
            resources.directories.add("test-resources")
        }
    }
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    testImplementation(libs.bundles.common)
    testImplementation(libs.tachiyomi.lib.v16)
    testImplementation(libs.junit)
}

// Extension registration runs on the production source set, not the fixture tests.
tasks.matching { it.name.startsWith("ksp") && it.name.contains("UnitTest") }.configureEach {
    enabled = false
}

keiyoushi {
    name = "Atlas Comics"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl {
            custom("https://api.atlascomics.dpdns.org")
        }
        lang = "en"
    }

    deeplink {
        path("/v1/series/..*")
    }

    deeplink {
        host("atlascomics.dpdns.org")
        path("/series/..*")
    }
}
