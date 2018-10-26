repositories {
    jcenter()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.google.guava:guava:27.0-jre")
    implementation("org.ajoberstar.grgit:grgit-gradle:3.0.0-rc.3")
    implementation("com.google.cloud:google-cloud-pubsub:1.52.0")
}
