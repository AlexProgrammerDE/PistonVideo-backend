plugins {
    application
}

group = "net.pistonmaster"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.sparkjava:spark-core:2.9.3")
    implementation("ch.qos.logback:logback-classic:1.2.10")
    implementation("org.mongodb:mongodb-driver-sync:4.4.1")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("com.google.guava:guava:31.0.1-jre")

    compileOnly("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")

    implementation("sh.ory.kratos:kratos-client:0.8.0-alpha.2")
    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.4")

    implementation("com.github.kokorin.jaffree:jaffree:2021.12.30")
}

application {
    // Define the main class for the application.
    mainClass.set("net.pistonmaster.pistonvideo.PistonVideoApplication")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "net.pistonmaster.pistonvideo.PistonVideoApplication"
        }
    }
}

tasks.test {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
