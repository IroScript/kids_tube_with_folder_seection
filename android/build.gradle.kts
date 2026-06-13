try {
    val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
    val theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment")
    theEnvironmentField.isAccessible = true
    val env = theEnvironmentField.get(null) as MutableMap<String, String>
    env.remove("CPATH")
    env.remove("C_INCLUDE_PATH")
    env.remove("CPLUS_INCLUDE_PATH")

    val theCaseInsensitiveEnvironmentField = try {
        processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment")
    } catch (e: Exception) {
        null
    }
    if (theCaseInsensitiveEnvironmentField != null) {
        theCaseInsensitiveEnvironmentField.isAccessible = true
        val cienv = theCaseInsensitiveEnvironmentField.get(null) as MutableMap<String, String>
        cienv.remove("CPATH")
        cienv.remove("C_INCLUDE_PATH")
        cienv.remove("CPLUS_INCLUDE_PATH")
    }
} catch (e: Exception) {
    // Ignore any reflection failures
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

subprojects {
    val configureAction = Action<Project> {
        plugins.withType<com.android.build.gradle.BasePlugin> {
            configure<com.android.build.gradle.BaseExtension> {
                compileSdkVersion(36)
            }
        }
    }
    if (state.executed) {
        configureAction.execute(this)
    } else {
        afterEvaluate(configureAction)
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
