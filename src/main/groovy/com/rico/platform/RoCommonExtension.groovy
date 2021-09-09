package com.rico.platform

import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

/**
 * Extension for common configuring ro plugin
 * 
 * @author krishna
 *
 */
class RoCommonExtension extends RoBaseExtension{

	String autoGenerateJavaClassForProtoFiles

	Project project

    RoCommonExtension(Instantiator instantiator,
                      Project project) {
		super(instantiator, project)
		this.project = project
	}
}
