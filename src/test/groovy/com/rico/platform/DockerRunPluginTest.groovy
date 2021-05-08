package com.rico.platform;

import static org.gradle.testkit.runner.TaskOutcome.*

import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DockerRunPluginTest {

	@Rule public TemporaryFolder testProjectDir = new TemporaryFolder()
	File settingsFile
	File buildFile

	@Before
	void setup() {
		settingsFile = testProjectDir.newFile('settings.gradle')
		buildFile = testProjectDir.newFile('build.gradle')
	}

	@Test
	void dockertest() {
		given:
		settingsFile << """
        rootProject.name = 'docker-test'
        """
		buildFile << """
              plugins {
                id 'com.github.rico.dockerRun' version '2.12'
              }
              def portNums = ["8080:8080", "9090:9090"] as String[]
              dockerRun {
                         name "test-container"
                         image "testimage"
                         ports portNums
                         network 'bridge'
                         env 'MYVAR1': 'MYVALUE1', 'MYVAR2': 'MYVALUE2'
                     }
        """

		when:
		def result = GradleRunner.create().withPluginClasspath()
				.withProjectDir(testProjectDir.root)
				.withArguments('dockerNetworkModeStatus')
				.build()

		then:
		println "Test success"
	}
}
