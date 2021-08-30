package com.rico.platform

import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

/**
 * Extension for common configuring ro plugin
 * 
 * @author krishna
 *
 */
class RoCommonExtension {

	String autoGenerateJavaClassForProtoFiles
	String unitTest

	Project project

    RoCommonExtension(Instantiator instantiator,
                      Project project) {
		this.project = project
	}
}
