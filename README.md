JavaTransformer
====
<!---freshmark shields
output = [
	link(shield('Latest version', 'latest', '{{version}}', 'blue'), 'https://github.com/{{organisation}}/{{name}}/releases/latest'),
	link(shield('Maven artifact', 'jcenter', '{{name}}', 'blue'), 'https://bintray.com/{{bintrayrepo}}/{{name}}/view'),
	link(image('License', 'https://img.shields.io/github/license/{{organisation}}/{{name}}.svg'), 'LICENSE'),
	'',
	link(image('Coverage', 'https://img.shields.io/codecov/c/github/{{organisation}}/{{name}}/{{branch}}.svg'), ''),
	link(image('Discord chat', 'https://img.shields.io/discord/{{discordId}}.svg'), '{{discordInvite}}'),
	link(shield('Changelog', 'changelog', '{{version}}', 'brightgreen'), '{{releaseNotesPath}}'),
	link(image('Travis CI', 'https://travis-ci.org/{{organisation}}/{{name}}.svg?branch=master'), 'https://travis-ci.org/{{organisation}}/{{name}}'),
	].join('\n');
-->
[![Latest version](https://img.shields.io/badge/latest-1.8.0-blue.svg)](https://github.com/MinimallyCorrect/JavaTransformer/releases/latest)
[![Maven artifact](https://img.shields.io/badge/jcenter-JavaTransformer-blue.svg)](https://bintray.com/minimallycorrect/minimallycorrectmaven/JavaTransformer/view)
[![License](https://img.shields.io/github/license/MinimallyCorrect/JavaTransformer.svg)](LICENSE)

[![Coverage](https://img.shields.io/codecov/c/github/MinimallyCorrect/JavaTransformer/master.svg)]()
[![Discord chat](https://img.shields.io/discord/313371711632441344.svg)](https://discord.gg/YrV3bDm)
[![Changelog](https://img.shields.io/badge/changelog-1.8.0-brightgreen.svg)](docs/release-notes.md)
[![Travis CI](https://travis-ci.org/MinimallyCorrect/JavaTransformer.svg?branch=master)](https://travis-ci.org/MinimallyCorrect/JavaTransformer)
<!---freshmark /shields -->

Applies transformations to .java and .class files using a unified high level API.

Getting Started
----
TODO - WIKI

Compiling
---
JavaTransformer is built using Gradle.

* Install the Java 8 Development Kit
* Run `./gradlew build`

Formatting
----
* Formatting is automatically checked by spotless
* Run `./gradlew spotlessApply` to fix formatting errors
