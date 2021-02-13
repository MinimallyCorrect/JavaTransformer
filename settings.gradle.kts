rootProject.name = "JavaTransformer"

pluginManagement {
	repositories {
		exclusiveContent {
			forRepository {
				maven(url = "https://maven.minco.dev/")
			}
			filter {
				includeGroupByRegex("dev\\.minco.*")
				includeGroupByRegex("me\\.nallar.*")
				includeGroupByRegex("org\\.minimallycorrect.*")
			}
		}
		mavenCentral()
		gradlePluginPortal()
	}
}
