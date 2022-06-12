plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    implementation(gradleApi())
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "bilibili-poc-plugin"
            artifactId = "bilibili-poc-plugin.gradle.plugin"
            version = "1.0"

            from(components["java"])
        }
    }
}
