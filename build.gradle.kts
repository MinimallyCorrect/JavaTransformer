plugins {
	id("base")
	id("java")
	id("java-library")
	id("maven-publish")
	id("dev.minco.gradle.defaults-plugin") version "0.2.37"
	id("org.shipkit.shipkit-auto-version") version "1.1.19"
	id("org.shipkit.shipkit-changelog") version "1.1.15"
	id("org.shipkit.shipkit-github-release") version "1.1.15"
}

apply(from = "properties.gradle")
apply(from = "$rootDir/gradle/shipkit.gradle")

val releasing = project.hasProperty("releasing")
if (!releasing) {
	version = "$version-SNAPSHOT"
}

repositories {
	exclusiveContent {
		forRepository {
			maven { url = uri("https://maven.minco.dev/") }
		}
		filter {
			includeGroupByRegex("dev\\.minco.*")
			includeGroupByRegex("me\\.nallar.*")
			includeGroupByRegex("org\\.minimallycorrect.*")
		}
	}
	mavenCentral()
}

minimallyCorrectDefaults.languageLevel = JavaVersion.VERSION_11
minimallyCorrectDefaults.configureProject(project)

dependencies {
	testImplementation("junit:junit:4.13.2")
	implementation("com.github.javaparser:javaparser-core:3.23.1")
	api("com.google.code.findbugs:jsr305:3.0.2")
	api("org.jetbrains:annotations:22.0.0")

	val asmVer = "9.2"
	implementation("org.ow2.asm:asm:$asmVer")
	implementation("org.ow2.asm:asm-util:$asmVer")
	implementation("org.ow2.asm:asm-tree:$asmVer")

	val lombok = "org.projectlombok:lombok:1.18.20"
	compileOnly(lombok)
	testCompileOnly(lombok)
	annotationProcessor(lombok)
	testAnnotationProcessor(lombok)
}

tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.add("-Xlint:-options")
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
		}
	}

	repositories {
		System.getenv("DEPLOYMENT_REPO_PASSWORD")?.let { deploymentRepoPassword ->
			maven {
				url = if (releasing) {
					name = "minco.dev_releases"
					uri(System.getenv("DEPLOYMENT_REPO_URL_RELEASE"))
				} else {
					name = "minco.dev_snapshots"
					uri(System.getenv("DEPLOYMENT_REPO_URL_SNAPSHOT"))
				}
				credentials {
					username = System.getenv("DEPLOYMENT_REPO_USERNAME")
					password = deploymentRepoPassword
				}
			}
		}
	}
}

