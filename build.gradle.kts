// Plugin versions are declared in settings.gradle.kts (pluginManagement).
tasks.register("testCore") {
    group = "verification"
    description = "Runs the unit tests of the pure Kotlin modules (dsp and targets)."
    dependsOn(":dsp:test", ":targets:test")
}
