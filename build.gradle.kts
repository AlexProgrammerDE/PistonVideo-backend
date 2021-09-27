plugins {
    application
}

group = "net.pistonmaster"
version = "0.0.1-SNAPSHOT"
//sourceCompatibility = "16"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.sparkjava:spark-core:2.9.3")
    implementation("ch.qos.logback:logback-classic:1.2.6")
    implementation("org.mongodb:mongodb-driver-sync:4.3.2")
    implementation("com.google.code.gson:gson:2.8.8")

    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")
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