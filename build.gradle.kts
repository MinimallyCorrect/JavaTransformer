plugins {
	id("java")
	id("java-library")
	id("maven-publish")
	id("dev.minco.gradle.defaults-plugin") version "0.2.4"
}

apply(from = "properties.gradle")

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

minimallyCorrectDefaults {
	configureProject(project)
}

dependencies {
	testImplementation("junit:junit:4.13.2")
	implementation("com.github.javaparser:javaparser-core:3.6.24")
	api("com.google.code.findbugs:jsr305:3.0.2")
	api("org.jetbrains:annotations:20.1.0")

	val asmVer = "9.1"
	implementation("org.ow2.asm:asm:$asmVer")
	implementation("org.ow2.asm:asm-util:$asmVer")
	implementation("org.ow2.asm:asm-tree:$asmVer")

	val lombok = "org.projectlombok:lombok:1.18.18"
	implementation(lombok)
	annotationProcessor(lombok)
	testAnnotationProcessor(lombok)
}

tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.add("-Xlint:-options")
}
