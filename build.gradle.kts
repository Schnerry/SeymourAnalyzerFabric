import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("net.fabricmc.fabric-loom-remap")
    id("maven-publish")
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    maven {
        name = "Shedaniel"
        url = uri("https://maven.shedaniel.me/")
    }
    maven {
        name = "TerraformersMC"
        url = uri("https://maven.terraformersmc.com/releases/")
    }
}

dependencies {
    add("minecraft", "com.mojang:minecraft:${property("minecraft_version")}")
    add("mappings", loom.officialMojangMappings())
    add("modImplementation", "net.fabricmc:fabric-loader:${property("loader_version")}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    val clothConfig = "me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}"
    add("modImplementation", clothConfig)
    add("include", clothConfig)

    add("compileOnly", "org.projectlombok:lombok:1.18.38")
    add("annotationProcessor", "org.projectlombok:lombok:1.18.38")
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    inputs.property("archivesName", base.archivesName)

    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {
    }
}
