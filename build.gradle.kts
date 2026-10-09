plugins { java }
group = "ru.servermine"
version = "3.1.0"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.127-stable")
    testImplementation("io.papermc.paper:paper-api:26.2.build.127-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }

val packJava = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
val buildResourcePack = tasks.register<Exec>("buildResourcePack") {
    group = "build"
    description = "Generate the GUI, item models and embedded resource pack from source assets."
    inputs.dir("tools")
    outputs.dir("resourcepack")
    outputs.file("src/main/resources/forge.zip")
    outputs.files("gui-preview.png", "gui-preview-4x.png", "workpiece-models-preview.png")
    workingDir(projectDir)
    doFirst { commandLine(packJava.get().executablePath.asFile.absolutePath, "tools/BuildPack.java") }
}

tasks.processResources { dependsOn(buildResourcePack) }
tasks.jar { archiveFileName.set("ServerMine-ForgeSystem-${project.version}.jar") }
