package com.rico.platform

import com.rico.platform.utils.RoConstants
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
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

        this.getClass().getResource('/projectInfo.properties').withInputStream {
            props.load(it)
        }

        project.buildscript {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
            dependencies {
           //     classpath "io.freefair.gradle:lombok-plugin:6.2.0"
                classpath "com.github.rico:ro-app:${props.getProperty('ricoPluginVersion')}"
            }
        }

        project.apply plugin: 'io.spring.dependency-management'

        //apply to all sub project except common
        if (!project.name.startsWith('common')) {
            project.apply plugin: 'application'
            project.apply plugin: 'com.github.rico.ro-app'
            project.apply plugin: 'org.springframework.boot'
        } else {
            //Compile only libraries for common application
            project.apply plugin: 'java-library'
            project.apply plugin: 'com.github.rico.common'
        }

        project.beforeEvaluate {
            if (!project.name.startsWith('common')) {
                project.evaluationDependsOn(':common')
            }
        }

        project.afterEvaluate {

            println "Rico general version ${props.getProperty('ricoPluginVersion')}"

//            //Getting Ro app extension
//            Map<String, Project> childProjects = project.rootProject.getChildProjects()
//            for (Map.Entry<String, Project> map : childProjects) {
//                if (map.getKey().contentEquals(project.getName())) {
//                    Project project1 = map.getValue()
//                    if (project.name.startsWith('common')) {
//                        continue;
//                    }
//                    ROExtension extension = (ROExtension) project1.getExtensions().findByName("appConfig")
//                    if (extension == null) {
//                        continue;
//                    }
//                    for (Map.Entry<String, Object> extensionEntry : extension.properties) {
//                        if (extensionEntry.getValue() && extensionEntry.getValue() instanceof String) {
//                            childProjects.get('common').dependencies.add("compileOnly", 'org.springframework.data:spring-data-jpa');
//                            configMap.put(extensionEntry.getKey(), extensionEntry.getValue());
//                        }
//                    }
//                    break
//                }
//            }


            project.configure(project) {
 //               project.apply plugin: "io.freefair.lombok"

//                if (project.name.startsWith('common')) {
//                    for (Map.Entry<String, Boolean> configs : configMap) {
//                        println "KEY " + configs.getKey() + " VALUE " + configs.getValue()
//                    }
//                }
            }

            project.with {

                dependencies {
                    compileOnly "org.projectlombok:lombok:${RoConstants.lombokVersion}"
                    annotationProcessor "org.projectlombok:lombok:${RoConstants.lombokVersion}"

                    testCompileOnly "org.projectlombok:lombok:${RoConstants.lombokVersion}"
                    testAnnotationProcessor "org.projectlombok:lombok:${RoConstants.lombokVersion}"
                }
            }
        }
    }
}
