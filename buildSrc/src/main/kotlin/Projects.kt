import org.gradle.api.Project
import org.gradle.api.Task

val Project.isReleaseBuild get() = !hasProperty("devBuild")

fun Project.child(name: String) = subprojects.first { it.name == name }

fun Project.whenTaskScheduled(name: String, action: Task.() -> Unit) =
        whenTaskScheduled({ it == name }, action)

fun Project.whenTaskScheduled(
        named: (String) -> Boolean,
        action: Task.() -> Unit
) = gradle.taskGraph.whenReady {
    allTasks.filter { named(it.name) }.forEach(action)
}

fun Project.deepFind(name: String) = deepFind { it.name == name }

fun Project.deepFind(spec: (Task) -> Boolean) = allprojects.map {
    it.tasks.matching(spec)
}.flatten()
