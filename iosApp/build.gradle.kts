import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import java.io.File

val xcodeProject = layout.projectDirectory.file("iosApp.xcodeproj")
val xcodeScheme = "iosApp"
val ipaName = "VRCM"

fun registerArchiveTask(
    taskName: String,
    configuration: String,
    variant: String,
) = tasks.register<Exec>(taskName) {
    group = "build"
    description = "Builds the $configuration iOS archive without a distribution signature"
    workingDir(layout.projectDirectory)

    val archivePath = layout.buildDirectory.dir("archives/$variant/$xcodeScheme.xcarchive")
    outputs.dir(archivePath)
    outputs.upToDateWhen { false }

    commandLine(
        "xcodebuild",
        "-project", xcodeProject.asFile.absolutePath,
        "-scheme", xcodeScheme,
        "-sdk", "iphoneos",
        "-destination", "generic/platform=iOS",
        "archive",
        "-configuration", configuration,
        "-archivePath", archivePath.get().asFile.absolutePath,
        "CODE_SIGNING_ALLOWED=NO",
        "CODE_SIGNING_REQUIRED=NO",
    )
}

fun registerIpaTask(
    taskName: String,
    descriptionText: String,
    variant: String,
    archiveTask: TaskProvider<Exec>,
) = tasks.register(taskName) {
    group = "build"
    description = descriptionText

    val archiveDir = layout.buildDirectory.dir("archives/$variant/$xcodeScheme.xcarchive")
    val outputIpa = layout.buildDirectory.file("archives/$variant/$ipaName.ipa")
    inputs.dir(archiveDir)
    outputs.file(outputIpa)
    dependsOn(archiveTask)

    doLast {
        val applicationsDir = archiveDir.get().asFile.resolve("Products/Applications")
        val apps = applicationsDir.listFiles { candidate ->
            candidate.isDirectory && candidate.extension == "app"
        }?.sortedBy(File::getName).orEmpty()

        if (apps.size != 1) {
            throw GradleException(
                "Expected exactly one .app in ${applicationsDir.absolutePath}, found ${apps.size}",
            )
        }

        temporaryDir.deleteRecursively()
        val payloadDir = File(temporaryDir, "Payload").apply { mkdirs() }
        val packagedApp = File(payloadDir, apps.single().name)
        apps.single().copyRecursively(packagedApp, overwrite = true)

        logger.lifecycle("[IPA] Applying ad-hoc signature to ${packagedApp.name}")
        providers.exec {
            commandLine(
                "codesign",
                "--force",
                "--deep",
                "--sign", "-",
                "--timestamp=none",
                packagedApp.absolutePath,
            )
        }.result.get().assertNormalExitValue()

        val zipFile = File(temporaryDir, "$ipaName.zip")
        providers.exec {
            workingDir(temporaryDir)
            commandLine("zip", "-r", "-y", zipFile.absolutePath, "Payload")
        }.result.get().assertNormalExitValue()

        val outputFile = outputIpa.get().asFile
        outputFile.parentFile.mkdirs()
        zipFile.copyTo(outputFile, overwrite = true)
        logger.lifecycle("[IPA] Created ${outputFile.absolutePath}")
    }
}

val buildDebugArchive = registerArchiveTask(
    taskName = "buildDebugArchive",
    configuration = "Debug",
    variant = "debug",
)

val buildReleaseArchive = registerArchiveTask(
    taskName = "buildReleaseArchive",
    configuration = "Release",
    variant = "release",
)

registerIpaTask(
    taskName = "buildDebugIpa",
    descriptionText = "Packages the Debug iOS archive as an ad-hoc-signed IPA",
    variant = "debug",
    archiveTask = buildDebugArchive,
)

registerIpaTask(
    taskName = "buildReleaseIpa",
    descriptionText = "Packages the Release iOS archive as an ad-hoc-signed IPA",
    variant = "release",
    archiveTask = buildReleaseArchive,
)
