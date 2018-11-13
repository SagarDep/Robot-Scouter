package com.supercilex.robotscouter.build

import child
import com.supercilex.robotscouter.build.internal.isRelease
import com.supercilex.robotscouter.build.internal.secrets
import com.supercilex.robotscouter.build.tasks.DeployServer
import com.supercilex.robotscouter.build.tasks.GenerateChangelog
import com.supercilex.robotscouter.build.tasks.RebuildSecrets
import com.supercilex.robotscouter.build.tasks.Setup
import com.supercilex.robotscouter.build.tasks.UpdateTranslations
import com.supercilex.robotscouter.build.tasks.UploadAppToVc
import com.supercilex.robotscouter.build.tasks.UploadAppToVcPrep
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withGroovyBuilder

class RobotScouterBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        check(project === project.rootProject) { "Cannot apply build plugin to subprojects." }

        project.tasks.register<Setup>("setup") {
            group = "build setup"
            description = "Performs one-time setup to prepare Robot Scouter for building."
        }
        project.tasks.register<RebuildSecrets>("rebuildSecrets") {
            group = "build setup"
            description = "Repackages a new version of the secrets for CI."
        }
        project.tasks.register<UpdateTranslations>("updateTranslations") {
            group = "build setup"
            description = "Overwrites existing translations with new ones."
        }

        val generateChangelog =
                project.tasks.register<GenerateChangelog>("generateReleaseChangelog")
        val deployAndroid = project.tasks.register("deployAndroid")
        val uploadAppToVcPrep = project.tasks.register<UploadAppToVcPrep>("uploadAppToVcPrep")
        val uploadAppToVc =
                project.tasks.register<UploadAppToVc>("uploadAppToVersionHistory")
        val deployServer = project.tasks.register<DeployServer>("deployServer")

        val ciBuildLifecycle = project.tasks.register("ciBuild")
        val ciBuildPrep = project.tasks.register("buildForCiPrep")
        val ciBuild = project.tasks.register("buildForCi")

        project.gradle.taskGraph.whenReady {
            val creds = project.secrets.single { it.name.contains("publish") }
            val metadataTasks = allTasks.filter { it.name == "processReleaseMetadata" }

            if (metadataTasks.isNotEmpty() && !creds.exists()) {
                metadataTasks.forEach { it.enabled = false }
            }
        }

        fun deepFind(spec: (Task) -> Boolean) = project.allprojects.map { it.tasks.matching(spec) }

        fun deepFind(name: String) = deepFind { it.name == name }

        fun List<TaskCollection<Task>>.mustRunAfter(vararg paths: Any) = onEach {
            it.configureEach { mustRunAfter(paths) }
        }

        ciBuildPrep.configure {
            dependsOn(deepFind("clean"))

            project.gradle.taskGraph.whenReady {
                if (!hasTask(this@configure)) return@whenReady

                fun skip(task: String, recursive: Boolean = false) {
                    allTasks.filter { it.name == task }.forEach {
                        it.enabled = false
                        if (recursive && hasTask(it)) {
                            getDependencies(it).filter { it.enabled }.forEach {
                                skip(it.name, recursive)
                            }
                        }
                    }
                }

                if (isRelease) {
                    skip("assembleDebug", true)
                } else {
                    skip("testReleaseUnitTest", true)
                }
                skip("lint")
            }
        }
        run {
            fun List<TaskCollection<Task>>.config() = mustRunAfter(ciBuildPrep)

            fun String.config() = deepFind { it.path == this }.config()

            val tasksToBeOrdered = if (isRelease) {
                listOf(
                        deepFind("build").config(),
                        ":app:android-base:bundleRelease".config()
                )
            } else {
                listOf(
                        ":app:android-base:assembleDebug".config(),
                        deepFind("check").config()
                )
            }.flatten()

            ciBuild.configure {
                dependsOn(ciBuildPrep)

                for (collection in tasksToBeOrdered) dependsOn(collection)
            }
        }

        ciBuildLifecycle.configure {
            group = "build"
            description = "Runs a specialized build for CI."

            dependsOn(ciBuild)
            if (isRelease) {
                dependsOn(deployAndroid, deployServer, uploadAppToVc)
            }
        }

        run {
            val publish = deepFind("publish").mustRunAfter(generateChangelog)
            val promote = deepFind {
                it.name.startsWith("promote") && it.name.endsWith("Artifact")
            }.mustRunAfter(publish)

            for (collection in promote) {
                collection.configureEach {
                    doFirst {
                        project.child("android-base").extensions["play"].withGroovyBuilder {
                            invokeMethod("setTrack", "alpha")
                        }
                    }
                }
            }

            deployAndroid.configure {
                group = "publishing"
                description = "Deploys Robot Scouter to the Play Store."

                dependsOn(generateChangelog)
                dependsOn(publish)
                dependsOn(promote)
                dependsOn(deepFind("crashlyticsUploadDeobs"))
            }
        }
        uploadAppToVcPrep.configure {
            dependsOn(ciBuild)
        }
        uploadAppToVc.configure {
            dependsOn(uploadAppToVcPrep)
        }

        deployServer.configure {
            group = "publishing"
            description = "Deploys Robot Scouter to the Web and Backend."

            dependsOn("app:server:functions:assemble")
        }
    }
}
