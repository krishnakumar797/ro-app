package com.rico.platform


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * General ro plugin for dependency management
 *
 * @author krishna*
 */
class RoGeneralPlugin implements Plugin<Project> {


    final Instantiator instantiator;

    private Map<String, String> configMap = new HashMap<>()

    def props = new Properties()

    @Inject
    RoGeneralPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    void apply(Project project) {

        this.getClass().getResource( '/projectInfo.properties' ).withInputStream {
            props.load(it)
        }

        project.buildscript {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
            dependencies {
                classpath "io.freefair.gradle:lombok-plugin:5.1.0"
                classpath "com.github.rico:ro-app:${props.getProperty('ricoPluginVersion')}"
            }
        }

        project.apply plugin: 'io.spring.dependency-management'

        //apply to all sub project except common
        if (!project.name.startsWith('common')) {
            project.apply plugin: 'application'
            project.apply plugin: 'com.github.rico.ro-app'
            project.apply plugin: 'org.springframework.boot'
        }else {
            //Compile only libraries for common application
            project.apply plugin: 'java'
            project.apply plugin: 'java-library'
            project.apply plugin: 'com.github.rico.common'
        }

        project.afterEvaluate {

            println "Rico general version ${props.getProperty('ricoPluginVersion')}"

//            //Getting Ro app extension
//            Map<String, Project> childProjects = project.rootProject.getChildProjects()
//            for (Map.Entry<String, Project> map : childProjects) {
//                if (map.getKey().contentEquals(project.getName())) {
//                    Project project1 = map.getValue()
//                    ROExtension extension  = (ROExtension) project1.getExtensions().findByName("appConfig")
//                    if(extension == null){
//                        continue;
//                    }
//                    for (Map.Entry<String, Object> extensionEntry : extension.properties) {
//                        if(extensionEntry.getValue() && extensionEntry.getValue() instanceof String){
//                            configMap.put(extensionEntry.getKey(), extensionEntry.getValue());
//                        }
//                    }
//                    break
//                }
//            }
//            if (!project.name.startsWith('common')) {
//                for (Map.Entry<String, Boolean> configs : configMap) {
//                   println "KEY "+configs.getKey()
//                }
//            }

            project.configure(project) {
                project.apply plugin: "io.freefair.lombok"

            }

            project.with {

                dependencies {

                }
            }
        }
    }
}
