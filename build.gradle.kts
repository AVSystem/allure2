import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import java.nio.charset.StandardCharsets.UTF_8

val linkHomepage by extra("https://allurereport.org/")
val linkCi by extra("https://ci.qameta.io/job/allure2")
val linkScmUrl by extra("https://github.com/allure-framework/allure2")
val linkScmConnection by extra("scm:git:git://github.com/allure-framework/allure2.git")
val linkScmDevConnection by extra("scm:git:ssh://git@github.com:allure-framework/allure2.git")

val root = rootProject.projectDir
val gradleScriptDir by extra("$root/gradle")
val qualityConfigsDir by extra("$gradleScriptDir/quality-configs")
val spotlessDtr by extra("$qualityConfigsDir/spotless")

tasks.wrapper {
    gradleVersion = "8.1.1"
}

plugins {
    java
    `java-library`
    `maven-publish`
    signing
    id("com.bmuschko.docker-remote-api")
    id("io.github.gradle-nexus.publish-plugin")
    id("com.diffplug.spotless")
    id("com.gorylenko.gradle-git-properties")
    id("io.spring.dependency-management")
    id("ru.vyarus.quality")
    id("org.owasp.dependencycheck")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

allprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}

description = "Allure Report"
group = "io.qameta.allure"

nexusPublishing {
    repositories {
        sonatype()
    }
}

subprojects {
    group = if (project.name.endsWith("plugin")) {
        "io.qameta.allure.plugins"
    } else {
        "io.qameta.allure"
    }
    version = version

    apply(plugin = "java")
    apply(plugin = "signing")
    apply(plugin = "maven-publish")
    apply(plugin = "ru.vyarus.quality")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.spring.dependency-management")

    dependencyManagement {
        imports {
            mavenBom("com.fasterxml.jackson:jackson-bom:2.16.1")
            mavenBom("org.junit:junit-bom:5.10.1")
            mavenBom("io.qameta.allure:allure-bom:2.25.0")
        }
        dependencies {
            dependency("ch.qos.logback:logback-classic:1.3.14")
            dependency("com.beust:jcommander:1.82")
            dependency("com.github.spotbugs:spotbugs-annotations:4.8.3")
            dependency("com.opencsv:opencsv:4.6")
            dependency("commons-beanutils:commons-beanutils:1.9.4")
            dependency("commons-io:commons-io:2.15.1")
            dependency("javax.xml.bind:jaxb-api:2.3.1")
            dependency("org.allurefw:allure1-model:1.0")
            dependency("org.apache.commons:commons-lang3:3.14.0")
            dependency("org.apache.httpcomponents:httpclient:4.5.14")
            dependency("org.apache.tika:tika-core:2.9.1")
            dependency("org.assertj:assertj-core:3.25.1")
            dependency("org.eclipse.jetty:jetty-server:9.4.53.v20231009")
            dependency("org.freemarker:freemarker:2.3.32")
            dependency("org.junit-pioneer:junit-pioneer:2.2.0")
            dependency("org.mockito:mockito-core:5.8.0")
            dependency("org.projectlombok:lombok:1.18.30")
            dependency("org.zeroturnaround:zt-zip:1.16")
            dependencySet("org.slf4j:2.0.7") {
                entry("slf4j-api")
                entry("slf4j-nop")
                entry("slf4j-simple")
            }
            dependencySet("com.squareup.retrofit2:2.9.0") {
                entry("converter-jackson")
                entry("retrofit")
            }
            dependencySet("com.vladsch.flexmark:0.62.2") {
                entry("flexmark")
                entry("flexmark-ext-tables")
            }
        }
    }

    tasks.compileJava {
        if (JavaVersion.current().isJava8) {
            java.targetCompatibility = JavaVersion.VERSION_1_8
        } else {
            options.release.set(8)
        }
    }

    tasks.compileTestJava {
        options.compilerArgs.add("-parameters")
    }

    tasks.jar {
        manifest {
            attributes(mapOf(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version
            ))
        }
    }

    tasks.test {
        systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
        systemProperty("allure.model.indentOutput", "true")
        systemProperty("junit.jupiter.execution.parallel.enabled", true)
        systemProperty("junit.jupiter.execution.parallel.mode.default", true)
        testLogging {
            listOf(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
        }
        maxHeapSize = project.property("test.maxHeapSize").toString()
        maxParallelForks = Integer.parseInt(project.property("test.maxParallelForks") as String)
    }

    tasks.processTestResources {
        filesMatching("**/allure.properties") {
            filter {
                it.replace("#project.description#", project.description ?: project.name)
            }
        }
    }

    quality {
        configDir = "$gradleScriptDir/quality-configs"
        excludeSources = fileTree("build/generated-sources")
        exclude("**/*.json")
        checkstyleVersion = "8.36.1"
        pmdVersion = "6.28.0"
        spotbugsVersion = "4.1.2"
        codenarcVersion = "1.6"
        spotbugs = true
        codenarc = true
        pmd = true
        checkstyle = true
        htmlReports = false

        afterEvaluate {
            val spotbugs = configurations.findByName("spotbugs")
            if (spotbugs != null) {
                dependencies {
                    spotbugs("org.slf4j:slf4j-simple")
                    spotbugs("com.github.spotbugs:spotbugs:4.8.2")
                }
            }
        }
    }

    spotless {
        java {
            target("src/**/*.java")
            removeUnusedImports()
            importOrder("", "jakarta", "javax", "java", "\\#")
            licenseHeader(file("$spotlessDtr/allure.java.license").readText(UTF_8))
            endWithNewline()
            replaceRegex("one blank line after package line", "(package .+;)\n+import", "$1\n\nimport")
            replaceRegex("one blank line after import lists", "(import .+;\n\n)\n+", "$1")
            replaceRegex("no blank line between jakarta & javax", "(import jakarta.+;\n)\n+(import javax.+;\n)", "$1$2")
            replaceRegex("no blank line between javax & java", "(import javax.+;\n)\n+(import java.+;\n)", "$1$2")
            replaceRegex("no blank line between jakarta & java", "(import jakarta.+;\n)\n+(import java.+;\n)", "$1$2")
        }
        format("misc") {
            target(
                    "*.gradle",
                    "*.gitignore",
                    "README.md",
                    "CONTRIBUTING.md",
                    "config/**/*.xml",
                    "src/**/*.xml"
            )
            trimTrailingWhitespace()
            endWithNewline()
        }

        encoding("UTF-8")
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                suppressAllPomMetadataWarnings()
                pom {
                    name.set(project.name)
                    description.set("Module ${project.name} of Allure Framework.")
                    url.set("https://allurereport.org/")
                    organization {
                        name.set("Qameta Software")
                        url.set("https://qameta.io/")
                    }
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("baev")
                            name.set("Dmitry Baev")
                            email.set("dmitry.baev@qameta.io")
                            url.set("https://github.com/baev")
                        }
                        developer {
                            id.set("eroshenkoam")
                            name.set("Artem Eroshenko")
                            email.set("artem.eroshenko@qameta.io")
                            url.set("https://github.com/eroshenkoam")
                        }
                    }
                    scm {
                        developerConnection.set("scm:git:git://github.com/allure-framework/allure2")
                        connection.set("scm:git:git://github.com/allure-framework/allure2")
                        url.set("https://github.com/allure-framework/allure2")
                    }
                    issueManagement {
                        system.set("GitHub Issues")
                        url.set("https://github.com/allure-framework/allure2/issues")
                    }
                    ciManagement {
                        system.set("Github Actions")
                        url.set("https://github.com/allure-framework/allure-java/actions")
                    }
                    versionMapping {
                        usage("java-api") {
                            fromResolutionOf("runtimeClasspath")
                        }
                        usage("java-runtime") {
                            fromResolutionResult()
                        }
                    }
                }
            }
        }
    }

    // Developers do not always have PGP configured,
    // so activate signing for release versions only
    // Just in case Maven Central rejects signed snapshots for some reason
    if (!version.toString().endsWith("-SNAPSHOT")) {
        signing {
            sign(publishing.publications["maven"])
        }
    }

    val allurePlugin by configurations.creating {
        isCanBeResolved = true
        isCanBeConsumed = true
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named("allure-plugin"))
        }
    }

    val pluginsDir = "$buildDir/plugins/"
    val copyPlugins by tasks.creating(Sync::class) {
        group = "Build"
        dependsOn(allurePlugin)
        into(pluginsDir)
        from(provider { allurePlugin.map { if (it.isDirectory) it else zipTree(it) } })
        eachFile {
            val segments = relativePath.segments
            segments[0] = segments[0].replace("-${project.version}", "")
        }
        includeEmptyDirs = false
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

apply(plugin = "com.bmuschko.docker-remote-api")

val deleteDemoReport by tasks.creating(Delete::class) {
    group = "Build"
    delete("$root/build/docker/report")
}

val generateDemoReport by tasks.creating(Exec::class) {
    group = "Build"
    dependsOn("deleteDemoReport", "allure-commandline:build")
    executable = "$root/allure-commandline/build/install/allure/bin/allure"
    args("generate", "$root/allure-web/test-data/demo", "-o", "$root/build/docker/report")
}

val generateDemoDockerfile by tasks.creating(Dockerfile::class) {
    group = "Build"
    dependsOn("generateDemoReport")
    destFile.set(file("build/docker/Dockerfile"))
    from("nginx")
    addFile("report", "/usr/share/nginx/html")
}
