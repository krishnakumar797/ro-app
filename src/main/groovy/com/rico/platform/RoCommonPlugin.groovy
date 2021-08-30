package com.rico.platform

import com.rico.platform.utils.RoConstants
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * Common ro plugin for common dependency management
 *
 * @author krishna*
 */
class RoCommonPlugin implements Plugin<Project> {


    final Instantiator instantiator;
    private boolean isModularised;
    private Map<String, Boolean> configMap = new HashMap<>()

    @Inject
    RoCommonPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    void apply(Project project) {

        def extension = project.extensions.create('appConfig', RoCommonExtension, instantiator, project)

        project.buildscript {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
            dependencies {
                classpath "gradle.plugin.com.google.protobuf:protobuf-gradle-plugin:0.8.11"
            }
        }

        //Deleting module info file if exist
        def infoFile = project.file("src/main/java/module-info.java")
        if (infoFile.exists()) {
            println("Clean up module info")
            infoFile.delete();
        }
        project.afterEvaluate {
            //Getting Package names in the common folder
            def sourceSet = project.sourceSets.findByName('main').java
            def packages = sourceSet
                    .filter { it.path.endsWith('.java') }
                    .collect {
                        it.getParentFile().path
                                .substring(sourceSet.srcDirs[0].path.length() + 1)
                                .replace('/', '.')
                    }
                    .unique()

            packages.each {
                if (it.contains("couchbase")) {
                    configMap.put("couchbase", true)
                }
                if (it.contains("kafka")) {
                    configMap.put("kafka", true)
                }
                if (it.contains("redis")) {
                    configMap.put("redis", true)
                }
                if (it.contains("grpc")) {
                    configMap.put("grpc", true)
                }
                if (it.contains("logging")) {
                    configMap.put("logging", true)
                }
                if (it.contains("security")) {
                    configMap.put("security", true)
                }
                if (it.contains("rest")) {
                    configMap.put("rest", true)
                }
                if (it.contains("elasticsearch")) {
                    configMap.put("elasticsearch", true)
                }
                if (it.contains("springdata")) {
                    configMap.put("springdata", true)
                }
                if (it.contains("hibernate")) {
                    configMap.put("hibernate", true)
                }
                if (it.contains("hazelcast")) {
                    configMap.put("hazelcast", true)
                }
                if (it.contains("hazelcast")) {
                    configMap.put("hazelcast", true)
                }
                if (it.contains("web")) {
                    configMap.put("web", true)
                }
                if (it.contains("uaa")) {
                    configMap.put("uaa", true)
                }
            }

            //Getting Ro app extension
            Map<String, Project> childProjects = project.rootProject.getChildProjects()
            for (Map.Entry<String, Project> map : childProjects) {
                if (!map.getKey().contentEquals(project.getName())) {
                    Project project1 = map.getValue()
                    ROExtension roExtension = (ROExtension) project1.getExtensions().findByName("appConfig")
                    if (roExtension.javaModule) {
                        isModularised = true
                        break
                    }
                }
            }
            println "Is modularised " + isModularised

            project.configure(project) {
                ext {
                    set('elasticsearch.version', '7.0.0')
                }
                //Applying generate protobuf plugin
                if (extension.autoGenerateJavaClassForProtoFiles == 'y') {
                    println "ROOT PROJECT PATH $projectDir"
                    project.apply plugin: 'com.google.protobuf'
                    println "To auto generate java class place the proto files under src/main/proto and run generateProto command in the common project"
                    // Place proto files under src/main/proto
                    sourceSets {
                        main {
                            proto {
                                srcDir 'src/main/proto'
                            }
                            java {
                                srcDir 'src/main/java'
                            }
                        }
                    }

                    //Generate java classes based on proto files
                    protobuf {
                        protoc {
                            artifact = "com.google.protobuf:protoc:${RoConstants.protobufVersion}"
                        }
                        plugins {
                            grpc {
                                artifact = "io.grpc:protoc-gen-grpc-java::${RoConstants.protocJavaVersion}"
                            }
                        }

                        generateProtoTasks {
                            ofSourceSet('main').each { task ->
                                task.builtins {
                                    java{
                                        outputSubDir = 'java'
                                    }
                                }
                                task.plugins {
                                    grpc {
                                        outputSubDir = 'java'
                                    }
                                }
                            }
                        }
                        generatedFilesBaseDir = "$projectDir/src"
                    }
                }
                if(extension.unitTest == 'y'){
                    configurations {
                        testImplementation.extendsFrom compileOnly
                    }
                }

                if (isModularised) {
//                    apply plugin: 'de.jjohannes.extra-java-module-info'
//                    extraJavaModuleInfo {
//                        if (configMap.get("grpc")) {
//                            automaticModule("grpc-server-spring-boot-autoconfigure-2.10.1.RELEASE.jar", "grpc.server.spring.boot.autoconfigure")
//                            automaticModule("grpc-client-spring-boot-autoconfigure-2.10.1.RELEASE.jar", "grpc.client.spring.boot.autoconfigure")
//                        }
//                        if (configMap.get("kafka")) {
//                            automaticModule("kafka-clients-2.3.1.jar", "kafka.clients")
//                        }
//                    }
                    project.plugins.withType(JavaPlugin).configureEach {
                        java {
                            modularity.inferModulePath = true
                        }
                    }
                }
            }

            project.with {
                dependencies {

                    def commonModuleInfo

                    if (isModularised) {
                        commonModuleInfo = file("src/main/java/module-info.java")
                        commonModuleInfo.write("module com.rico.common {\n")
                        commonModuleInfo.append("requires static spring.core;\n")
                        commonModuleInfo.append("requires static spring.boot;\n")
                        commonModuleInfo.append("requires static spring.boot.autoconfigure;\n")
                        commonModuleInfo.append("requires static spring.beans;\n")
                        commonModuleInfo.append("requires static java.annotation;\n")
                        commonModuleInfo.append("requires static spring.context;\n")
                        commonModuleInfo.append("requires static lombok;\n")
                    }
                    compileOnly "com.sun.activation:jakarta.activation:${RoConstants.jakartaVersion}"
                    compileOnly 'org.springframework.boot:spring-boot-autoconfigure'
                    compileOnly "com.google.code.findbugs:jsr305:3.0.2"
                    compileOnly "com.fasterxml.jackson.core:jackson-databind:2.0.1"

                    if (configMap.get("couchbase")) {
                        compileOnly 'org.springframework.data:spring-data-couchbase'
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.data.couchbase;\n")
                            commonModuleInfo.append("requires static com.couchbase.client.java;\n")
                            commonModuleInfo.append("requires static com.couchbase.client.core;\n")
                        }
                    }
                    if (configMap.get("kafka")) {
                        compileOnly 'org.springframework.kafka:spring-kafka'
                        compileOnly 'com.esotericsoftware.kryo:kryo5:5.0.2'
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.kafka;\n")
                            commonModuleInfo.append("requires static kafka.clients;\n")
                            commonModuleInfo.append("requires static com.google.protobuf;\n")
                            commonModuleInfo.append("requires static com.esotericsoftware.kryo.kryo5;\n")
                        }
                    }
                    if (configMap.get("redis")) {
                        compileOnly 'org.springframework.data:spring-data-redis'
                        compileOnly 'io.lettuce:lettuce-core'
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.data.redis;\n")
                        }
                    }

                    if (configMap.get("grpc")) {
                        compileOnly "net.devh:grpc-spring-boot-starter:${RoConstants.grpcVersion}"
                        compileOnly "io.grpc:grpc-protobuf:${RoConstants.protocJavaVersion}"
                        compileOnly "io.grpc:grpc-stub:${RoConstants.protocJavaVersion}"
                        compileOnly "io.grpc:grpc-netty-shaded:${RoConstants.protocJavaVersion}"
                        if (isModularised) {
                            commonModuleInfo.append("requires static grpc.client.spring.boot.autoconfigure;\n")
                            commonModuleInfo.append("requires static grpc.api;\n")
                            commonModuleInfo.append("requires static grpc.server.spring.boot.autoconfigure;\n")
                        }
                    }


                    if (configMap.get("logging")) {
                        compileOnly 'org.apache.logging.log4j:log4j-api'
                        compileOnly 'org.apache.logging.log4j:log4j-core'
                        if (isModularised) {
                            commonModuleInfo.append("requires static org.apache.logging.log4j.core;\n")
                        }
                    }

                    if (configMap.get("elasticsearch")) {
                        compileOnly 'org.springframework.data:spring-data-elasticsearch'
                        if (!configMap.get("rest")) {
                            compileOnly('org.springframework.boot:spring-boot-starter-web') {
                                exclude module: "spring-boot-tomcat"
                            }
                        }
                        if (isModularised) {
                            commonModuleInfo.append("requires static elasticsearch.rest.high.level.client;\n")
                            commonModuleInfo.append("requires static spring.data.elasticsearch;\n")
                        }
                    }
                    if (configMap.get("springdata")) {
                        compileOnly 'org.springframework.data:spring-data-jpa'
                        compileOnly 'org.hibernate:hibernate-entitymanager'
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.orm;\n")
                            commonModuleInfo.append("requires static com.zaxxer.hikari;\n")
                            commonModuleInfo.append("requires static java.sql;\n")
                            commonModuleInfo.append("requires static spring.tx;\n")
                        }
                    }
                    if (configMap.get("hibernate")) {
                        compileOnly 'org.hibernate:hibernate-entitymanager'
                    }

                    if (configMap.get("hazelcast")) {
                        compileOnly "com.hazelcast:hazelcast:${RoConstants.hazelcastVersion}"
                        compileOnly "com.hazelcast:hazelcast-spring:${RoConstants.hazelcastVersion}"
                        if (isModularised) {
                            commonModuleInfo.append("requires static com.hazelcast.core;\n")
                        }
                    }

                    if (configMap.get("monitoring")) {
                        compileOnly 'org.springframework.boot:spring-boot-actuator-autoconfigure'
                    }

                    if (configMap.get("security")) {
                        compileOnly "org.springframework.security:spring-security-web"
                        compileOnly "org.springframework.security:spring-security-config"
                        compileOnly "com.auth0:java-jwt:${RoConstants.jwtVersion}"
                    }

                    if (configMap.get("uaa")) {
                        compileOnly "org.springframework.boot:spring-boot-starter-oauth2-client"
                        compileOnly "org.springframework.boot:spring-boot-starter-oauth2-resource-server"
                        compileOnly "org.springframework.security:spring-security-oauth2-jose"
                        compileOnly "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:2.2.1.RELEASE"
                    }

                    if (configMap.get("rest")) {
                        compileOnly "org.modelmapper:modelmapper:${RoConstants.modelMapperVersion}"
                        compileOnly 'org.springframework:spring-webmvc'
                        compileOnly 'jakarta.validation:jakarta.validation-api'
                        compileOnly "javax.servlet:servlet-api:${RoConstants.servletApiVersin}"
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.webmvc;\n")
                            commonModuleInfo.append("requires static modelmapper;\n")
                            commonModuleInfo.append("requires static java.validation;\n")
                        }
                    }

                    if (configMap.get("web")) {
                        compileOnly  'org.springframework.boot:spring-boot-starter-thymeleaf'
                        if(!configMap.get("rest")) {
                            compileOnly "org.modelmapper:modelmapper:${RoConstants.modelMapperVersion}"
                            compileOnly 'org.springframework:spring-webmvc'
                            compileOnly 'jakarta.validation:jakarta.validation-api'
                            compileOnly "javax.servlet:servlet-api:${RoConstants.servletApiVersin}"
                        }
                        if (isModularised) {
                            if(!configMap.get("rest")) {
                                commonModuleInfo.append("requires static spring.webmvc;\n")
                                commonModuleInfo.append("requires static modelmapper;\n")
                                commonModuleInfo.append("requires static java.validation;\n")
                            }
                        }
                    }

                    implementation "org.javassist:javassist:${RoConstants.javaAssistVersion}"

                    //Common libs
                    if (configMap.get("springdata") || configMap.get("hibernate")) {
                        compileOnly 'com.zaxxer:HikariCP'
                    }
                    if (configMap.get("kafka") || configMap.get("grpc")) {
                        compileOnly "com.google.protobuf:protobuf-java:${RoConstants.protobufVersion}"
                    }

                    if (extension.autoGenerateJavaClassForProtoFiles == 'y') {
                        implementation "com.google.protobuf:protobuf-java-util:${RoConstants.protobufVersion}"
                    }

                    if(extension.unitTest == 'y') {
                        //Adding spring boot test frameworks to all applications
                        testCompileOnly('org.springframework.boot:spring-boot-starter-test') {
                            exclude group: 'org.junit.vintage', module: 'junit-vintage-engine'
                        }
                        //Use Springboot test starter package provided Junit instead of using Junit standalone
                        //JUnit 5 libraries
                      //  testCompileOnly(platform("org.junit:junit-bom:${RoConstants.junitTestVersion}"))
                      //  testCompileOnly('org.junit.jupiter:junit-jupiter')

                        // Mockito libraries
                        testCompileOnly("org.mockito:mockito-core:${RoConstants.mockitoVersion}")
                        testCompileOnly("org.mockito:mockito-inline:${RoConstants.mockitoVersion}")
                        testCompileOnly("org.mockito:mockito-junit-jupiter:${RoConstants.mockitoVersion}")
                    }


                    //Common modules
                    if (isModularised) {
                        if (configMap.get("couchbase") || configMap.get("hibernate") || configMap.get("springdata") || configMap.get("elasticsearch")) {
                            commonModuleInfo.append("requires static spring.data.jpa;\n")
                            commonModuleInfo.append("requires static spring.data.commons;\n")
                        }
                        if (configMap.get("elasticsearch") || configMap.get("rest")) {
                            commonModuleInfo.append("requires static spring.web;\n")
                        }
                        if (configMap.get("hibernate") || configMap.get("springdata")) {
                            commonModuleInfo.append("requires static org.hibernate.orm.core;\n")
                        }

                        commonModuleInfo.append("}")
                    }
                }

                //Adding junit test platform
                test {
                    if (extension.unitTest == 'y') {
                        useJUnitPlatform()
                    }
                }
            }
        }
    }
}
