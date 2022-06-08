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
            groupId = "bilibili"
            artifactId = "poc"
            version = "1.0"

            from(components["java"])
        }
    }
}
