import org.gradle.api.tasks.Exec

val xcodeProject = layout.projectDirectory.file("iosApp.xcodeproj")
val xcodeScheme = "iosApp"
val ipaName = "VRCM"
val packageIpaScript = layout.projectDirectory.file("package-ipa.sh")

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
) = tasks.register<Exec>(taskName) {
    group = "build"
    description = descriptionText

    val archiveDir = layout.buildDirectory.dir("archives/$variant/$xcodeScheme.xcarchive")
    val outputIpa = layout.buildDirectory.file("archives/$variant/$ipaName.ipa")
    inputs.dir(archiveDir)
    inputs.file(packageIpaScript)
    outputs.file(outputIpa)
    dependsOn(archiveTask)
    workingDir(layout.projectDirectory)
    commandLine(
        "bash",
        packageIpaScript.asFile.absolutePath,
        archiveDir.get().asFile.absolutePath,
        outputIpa.get().asFile.absolutePath,
    )
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
