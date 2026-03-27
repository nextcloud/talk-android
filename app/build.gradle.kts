/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("com.android.built-in-kotlin")
    id("com.android.legacy-kapt")
    id("com.google.devtools.ksp") version "2.3.6"
    id("com.android.application")
    id("kotlin-parcelize")
    id("com.github.spotbugs")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("kotlinx-serialization")
}

val kotlinVersion: String by rootProject.extra

val androidxCameraVersion = "1.6.0"
val coilKtVersion = "2.7.0"
val daggerVersion = "2.59.2"
val emojiVersion = "1.6.0"
val fidoVersion = "4.1.0-patch2"
val lifecycleVersion = "2.10.0"
val okhttpVersion = "4.12.0"
val markwonVersion = "4.6.2"
val materialDialogsVersion = "3.3.0"
val parcelerVersion = "1.1.13"
val retrofit2Version = "3.0.0"
val roomVersion = "2.8.4"
val workVersion = "2.11.2"
val espressoVersion = "3.7.0"
val androidxTestVersion = "1.5.0"
val media3Version = "1.10.0"
val coroutinesVersion = "1.10.2"
val mockitoKotlinVersion = "6.3.0"

android {
    compileSdk = 36

    namespace = "com.nextcloud.talk"

    defaultConfig {
        testInstrumentationRunnerArguments["TEST_SERVER_URL"] =
            providers.gradleProperty("NC_TEST_SERVER_BASEURL").getOrElse("")
        testInstrumentationRunnerArguments["TEST_SERVER_USERNAME"] =
            providers.gradleProperty("NC_TEST_SERVER_USERNAME").getOrElse("")
        testInstrumentationRunnerArguments["TEST_SERVER_PASSWORD"] =
            providers.gradleProperty("NC_TEST_SERVER_PASSWORD").getOrElse("")

        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // mayor.minor.hotfix.increment (for increment: 01-50=Alpha / 51-89=RC / 90-99=stable)
        // xx   .xxx  .xx    .xx
        versionCode = 230010011
        versionName = "23.1.0 Alpha 11"

        vectorDrawables.useSupportLibrary = true

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }

        val localBroadcastPermission = "PRIVATE_BROADCAST"
        manifestPlaceholders["broadcastPermission"] = localBroadcastPermission
        buildConfigField("String", "PERMISSION_LOCAL_BROADCAST", "\"$localBroadcastPermission\"")
    }

    flavorDimensions += "default"

    productFlavors {
        // used for f-droid
        create("generic") {
            applicationId = "com.nextcloud.talk2"
            dimension = "default"
        }
        create("gplay") {
            applicationId = "com.nextcloud.talk2"
            dimension = "default"
        }
        create("qa") {
            applicationId = "com.nextcloud.talk2.qa"
            dimension = "default"
            versionCode = 1
            versionName = "1"
        }
    }

    sourceSets {
        // Adds exported schema location as test app assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE",
                "META-INF/DEPENDENCIES",
                "META-INF/rxjava.properties"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        disable.addAll(
            listOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "InvalidPackage",
                "LogNotTimber",
                "MissingTranslation",
                "PrivateResource",
                "UnusedQuantity",
                "VectorPath"
            )
        )
        htmlOutput = layout.buildDirectory.file("reports/lint/lint.html").get().asFile
        htmlReport = true
    }
}

tasks.named("check") {
    dependsOn("spotbugsGplayDebug", "lint", "ktlintCheck", "detekt")
}

kapt {
    correctErrorTypes = true
}

configurations.configureEach {
    exclude(group = "com.google.firebase", module = "firebase-core")
    exclude(group = "com.google.firebase", module = "firebase-analytics")
    exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
    exclude(group = "org.jetbrains", module = "annotations-java5") // via prism4j, already using annotations explicitly
}

dependencies {
    kapt("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion")
    implementation("androidx.room:room-testing-android:$roomVersion")
    implementation("androidx.compose.foundation:foundation-layout:1.10.6")
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
    spotbugsPlugins("com.mebigfatguy.fb-contrib:fb-contrib:7.7.4")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")

    implementation("androidx.compose.runtime:runtime:1.10.6")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.datastore:datastore-core:1.2.1")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.test.ext:junit-ktx:1.3.0")

    implementation(fileTree(mapOf("include" to listOf("*"), "dir" to "libs")))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:$coroutinesVersion")

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.vanniktech:emoji-google:0.23.0")
    implementation("androidx.emoji2:emoji2:$emojiVersion")
    implementation("androidx.emoji2:emoji2-bundled:$emojiVersion")
    implementation("androidx.emoji2:emoji2-views:$emojiVersion")
    implementation("androidx.emoji2:emoji2-views-helper:$emojiVersion")
    implementation("org.michaelevans.colorart:library:0.0.3")
    implementation("androidx.work:work-runtime:$workVersion")
    implementation("androidx.work:work-rxjava2:$workVersion")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("com.github.bitfireAT:dav4jvm:2.1.3") {
        exclude(group = "org.ogce", module = "xpp3") // Android comes with its own XmlPullParser
    }
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("com.github.nextcloud-deps:qrcodescanner:0.1.2.4") // "com.github.blikoon:QRCodeScanner:0.1.2"

    implementation("androidx.camera:camera-core:$androidxCameraVersion")
    implementation("androidx.camera:camera-camera2:$androidxCameraVersion")
    implementation("androidx.camera:camera-lifecycle:$androidxCameraVersion")
    implementation("androidx.camera:camera-view:$androidxCameraVersion")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-common:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")

    implementation("androidx.biometric:biometric:1.1.0")

    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")

    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    implementation("com.bluelinelabs:logansquare:1.3.7")
    implementation("com.fasterxml.jackson.core:jackson-core:2.20.1")
    kapt("com.bluelinelabs:logansquare-compiler:1.3.7")

    implementation("com.squareup.retrofit2:retrofit:$retrofit2Version")
    implementation("com.squareup.retrofit2:adapter-rxjava2:$retrofit2Version")
    implementation("com.squareup.retrofit2:converter-gson:$retrofit2Version")
    implementation("de.mannodermaus.retrofit2:converter-logansquare:1.4.1")

    implementation("com.google.dagger:dagger:$daggerVersion")
    kapt("com.google.dagger:dagger-compiler:$daggerVersion")
    implementation("com.github.lukaspili.autodagger2:autodagger2:1.1")
    kapt("com.github.lukaspili.autodagger2:autodagger2-compiler:1.1")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    kapt("javax.annotation:javax.annotation-api:1.3.2")

    implementation("org.greenrobot:eventbus:3.3.1")
    implementation("net.zetetic:sqlcipher-android:4.14.0")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-rxjava2:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    implementation("org.parceler:parceler-api:$parcelerVersion")
    implementation("com.github.ddB0515.FlexibleAdapter:flexible-adapter:5.1.1")
    implementation("com.github.ddB0515.FlexibleAdapter:flexible-adapter-ui:5.1.1")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.github.nextcloud-deps:ChatKit:0.4.2")
    implementation("joda-time:joda-time:2.14.1")
    implementation("io.coil-kt:coil:$coilKtVersion")
    implementation("io.coil-kt:coil-gif:$coilKtVersion")
    implementation("io.coil-kt:coil-svg:$coilKtVersion")
    implementation("io.coil-kt:coil-compose:$coilKtVersion")
    implementation("com.github.natario1:Autocomplete:1.1.0")

    implementation("com.github.nextcloud-deps.hwsecurity:hwsecurity-fido:$fidoVersion")
    implementation("com.github.nextcloud-deps.hwsecurity:hwsecurity-fido2:$fidoVersion")

    implementation("com.afollestad.material-dialogs:core:$materialDialogsVersion")
    implementation("com.afollestad.material-dialogs:datetime:$materialDialogsVersion")
    implementation("com.afollestad.material-dialogs:bottomsheets:$materialDialogsVersion")
    implementation("com.afollestad.material-dialogs:lifecycle:$materialDialogsVersion")

    implementation("com.google.code.gson:gson:2.13.2")

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.31")

    implementation("io.noties.markwon:core:$markwonVersion")
    implementation("io.noties.markwon:ext-strikethrough:$markwonVersion")
    implementation("io.noties.markwon:ext-tasklist:$markwonVersion")
    implementation("io.noties.markwon:ext-tables:$markwonVersion")

    // Avatar picker
    implementation("com.github.nextcloud-deps:ImagePicker:2.1.0.2")
    // Override avatar picker's internal crop lib for better edge-to-edge support
    implementation("com.github.yalantis:ucrop:2.2.11")

    implementation("io.github.elye:loaderviewlibrary:3.0.0")
    implementation("org.maplibre.compose:maplibre-compose:0.12.1")
    implementation("org.maplibre.compose:maplibre-compose-material3:0.12.1")
    implementation("fr.dudie:nominatim-api:3.4") {
        //noinspection DuplicatePlatformClasses
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("com.github.nextcloud.android-common:ui:0.33.2")
    implementation("com.github.nextcloud.android-common:core:0.33.2")
    implementation("com.github.nextcloud-deps:android-talk-webrtc:132.6834.0")

    "gplayImplementation"("com.google.android.gms:play-services-base:18.10.0")
    "gplayImplementation"("com.google.firebase:firebase-messaging:25.0.1")

    // compose
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // tests
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.6")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.22.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation("androidx.test:core:1.7.0")

    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("org.mockito:mockito-android:5.22.0")
    androidTestImplementation("androidx.work:work-testing:$workVersion")
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVersion") {
        exclude(group = "com.android.support", module = "support-annotations")
    }
    androidTestImplementation("androidx.test.espresso:espresso-contrib:$espressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-web:$espressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:$espressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-intents:$espressoVersion")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.01"))
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")

    testImplementation("org.junit.vintage:junit-vintage-engine:6.0.3") // DO NOT REMOVE
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
    testImplementation("com.google.dagger:hilt-android-testing:2.59.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

tasks.register<Copy>("installGitHooks") {
    description = "Install git hooks"
    from("../scripts/hooks") {
        include("*")
    }
    into("../.git/hooks")
}

spotbugs {
    ignoreFailures.set(true)
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.valueOf("MEDIUM"))
}

tasks.withType<SpotBugsTask>().configureEach {
    val variantNameCap = name.replace("spotbugs", "")
    val variantName = variantNameCap.substring(0, 1).lowercase() + variantNameCap.substring(1)

    dependsOn("compile${variantNameCap}Sources")

    classes = fileTree(
        layout.buildDirectory.get().asFile.toString() +
            "/intermediates/javac/$variantName/compile${variantNameCap}JavaWithJavac/classes/"
    )
    excludeFilter.set(file("${project.rootDir}/spotbugs-filter.xml"))

    reports.create("xml") {
        required.set(true)
    }
    reports.create("html") {
        required.set(true)
        outputLocation.set(layout.buildDirectory.file("reports/spotbugs/spotbugs.html"))
        setStylesheet("fancy.xsl")
    }
}

tasks.named<Detekt>("detekt").configure {
    reports {
        html.required.set(true)
        txt.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

detekt {
    config.setFrom("../detekt.yml")
    source.setFrom("src/")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
