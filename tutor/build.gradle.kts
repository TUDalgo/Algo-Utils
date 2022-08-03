dependencies {
    api(project(":algoutils-student"))
    api(libs.spoon)
    api(libs.docwatcher)
    api(libs.mockito)
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
}

tasks {
    test {
        useJUnitPlatform()
    }
}
