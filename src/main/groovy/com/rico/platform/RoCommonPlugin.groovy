package com.rico.platform

import com.rico.platform.utils.RoConstants
import com.rico.platform.utils.RoUtils
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
                classpath "gradle.plugin.com.google.protobuf:protobuf-gradle-plugin:0.8.17"
                classpath "de.jjohannes.gradle:extra-java-module-info:0.9"
            }

            if (extension.unitTest == 'y') {
                configurations {
                    testImplementation.extendsFrom compileOnly
                }
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
                                .replace(File.separator, '.')
                    }
                    .unique()

            isModularised = extension.javaModule == 'y' ? true : false;
            println "Is modularised " + isModularised


            project.configure(project) {
                ext {
                    set('elasticsearch.version', '7.0.0')
                }

                //Applying generate protobuf plugin
                if (extension.autoGenerateJavaClassForProtoFiles == 'y') {
                    project.apply plugin: 'com.google.protobuf'
                    println "To auto generate java class place the proto files under src/main/proto and run generateProto command in the common project"
                    // Place proto files under src/main/proto
                    sourceSets {
                        main {
                            proto {
                                srcDir 'src/main/proto'
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
                                artifact = "io.grpc:protoc-gen-grpc-java:${RoConstants.grpcJavaVersion}"
                            }
                        }

                        generateProtoTasks {
                            ofSourceSet('main').each { task ->
                                task.builtins {
                                    java {
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

                if (isModularised) {
                    apply plugin: 'de.jjohannes.extra-java-module-info'
                    extraJavaModuleInfo {
                        failOnMissingModuleInfo.set(false)
                        if (extension.grpc == 'y') {
                            automaticModule("grpc-server-spring-boot-autoconfigure-${RoConstants.grpcVersion}.jar", "grpc.server.spring.boot.autoconfigure")
                            automaticModule("grpc-client-spring-boot-autoconfigure-${RoConstants.grpcVersion}.jar", "grpc.client.spring.boot.autoconfigure")
                            automaticModule("io.grpc-${RoConstants.helidonGrpcVersion}.jar", "io.grpc")
                            automaticModule("grpc-stub-${RoConstants.grpcJavaVersion}.jar", "grpc.stub")
                            automaticModule("grpc-protobuf-${RoConstants.grpcJavaVersion}.jar", "grpc.protobuf")
                            automaticModule("grpc-netty-shaded-${RoConstants.grpcJavaVersion}.jar", "grpc.netty.shaded")
                            automaticModule("guava-30.0-android.jar", "com.google.common")
                            automaticModule("reflections-0.9.11.jar", "reflections")
                            automaticModule("commons-beanutils-1.9.4.jar", "commons.beanutils")
                            automaticModule("servlet-api-2.5.jar", "servlet.api")

                        }
                        if(extension.modelMapper == 'y'){
                            automaticModule("modelmapper-${RoConstants.modelMapperVersion}.jar", "modelmapper")
                        }
                        if (extension.queue == 'y') {
                            automaticModule("kafka-clients-2.3.1.jar", "kafka.clients")
                        }
                    }
                    project.plugins.withType(JavaPlugin).configureEach {
                        java {
                            modularity.inferModulePath = true
                        }
                        tasks.named('compileJava') {
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
                        if (extension.commonPackage) {
                            commonModuleInfo.write("module ${extension.commonPackage}{\n")
                            RoUtils.commonPackageName = extension.commonPackage
                        }
                        packages.each {
                            commonModuleInfo.append("exports ${it};\n")
                        }
                        commonModuleInfo.append("requires static spring.core;\n")
                        commonModuleInfo.append("requires static spring.boot;\n")
                        commonModuleInfo.append("requires static spring.boot.autoconfigure;\n")
                        commonModuleInfo.append("requires static spring.beans;\n")
                        commonModuleInfo.append("requires static java.annotation;\n")
                        commonModuleInfo.append("requires static spring.context;\n")
                        commonModuleInfo.append("requires static commons.beanutils;\n")
                        commonModuleInfo.append("requires static com.fasterxml.jackson.databind;\n")
                        commonModuleInfo.append("requires static com.fasterxml.jackson.annotation;\n")
                        commonModuleInfo.append("requires static lombok;\n")
                        commonModuleInfo.append("requires reflections;\n")
                    }

                    compileOnly "com.sun.activation:jakarta.activation:${RoConstants.jakartaVersion}"
                    compileOnly 'org.springframework.boot:spring-boot-autoconfigure'
                    compileOnly "com.google.code.findbugs:jsr305:3.0.2"
                    compileOnly "com.fasterxml.jackson.core:jackson-databind"

                    if (extension.dataBase.contains('couchbase')) {
                        compileOnly 'org.springframework.data:spring-data-couchbase'
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.data.couchbase;\n")
                            commonModuleInfo.append("requires static com.couchbase.client.java;\n")
                            commonModuleInfo.append("requires static com.couchbase.client.core;\n")
                        }
                    }
                    if (extension.queue == 'y') {
                        compileOnly 'org.springframework.kafka:spring-kafka'
                        compileOnly 'com.esotericsoftware.kryo:kryo5:5.0.2'
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.kafka;\n")
                            commonModuleInfo.append("requires static kafka.clients;\n")
                            commonModuleInfo.append("requires static com.google.protobuf;\n")
                            commonModuleInfo.append("requires static com.esotericsoftware.kryo.kryo5;\n")
                        }
                    }
                    if (extension.keyvaluestore == 'y') {
                        compileOnly 'org.springframework.data:spring-data-redis'
                        compileOnly 'io.lettuce:lettuce-core'
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.data.redis;\n")
                        }
                    }

                    if (extension.grpc == 'y') {
                        compileOnly "net.devh:grpc-spring-boot-starter:${RoConstants.grpcVersion}"
                        compileOnly "io.grpc:grpc-protobuf:${RoConstants.grpcJavaVersion}"
                        compileOnly "io.grpc:grpc-stub:${RoConstants.grpcJavaVersion}"
                        compileOnly "io.grpc:grpc-netty-shaded:${RoConstants.grpcJavaVersion}"
                        if(extension.unitTest == 'y'){
                            testImplementation("io.grpc:grpc-testing:${RoConstants.grpcJavaVersion}")
                        }

                     //  For Java module support WIP
                     //   compileOnly "io.helidon.grpc:io.grpc:${RoConstants.helidonGrpcVersion}"
                        if (isModularised) {
                            commonModuleInfo.append("requires static grpc.client.spring.boot.autoconfigure;\n")
                            commonModuleInfo.append("requires static grpc.server.spring.boot.autoconfigure;\n")
                            commonModuleInfo.append("requires static io.grpc;\n")
                            commonModuleInfo.append("requires static com.google.protobuf;\n")
                            commonModuleInfo.append("requires static grpc.stub;\n")
                            commonModuleInfo.append("requires static grpc.protobuf;\n")
                            commonModuleInfo.append("requires static grpc.netty.shaded;\n")
                            commonModuleInfo.append("requires static com.google.common;\n")
                        }
                    }


                    if (extension.logging == 'y') {
                        compileOnly 'org.apache.logging.log4j:log4j-api'
                        compileOnly 'org.apache.logging.log4j:log4j-core'
                        if (isModularised) {
                            commonModuleInfo.append("requires static org.apache.logging.log4j.core;\n")
                            commonModuleInfo.append("requires static org.apache.logging.log4j;\n")

                        }
                    }

                    if (extension.search == 'y') {
                        compileOnly 'org.springframework.data:spring-data-elasticsearch'
                        if (extension.rest != 'y') {
                            compileOnly('org.springframework.boot:spring-boot-starter-web') {
                                exclude module: "spring-boot-tomcat"
                            }
                        }
                        if (isModularised) {
                            commonModuleInfo.append("requires static elasticsearch.rest.high.level.client;\n")
                            commonModuleInfo.append("requires static spring.data.elasticsearch;\n")
                        }
                    }
                    if (extension.persistence.contains('springData')) {
                        compileOnly 'org.springframework.data:spring-data-jpa'
                        compileOnly 'org.hibernate:hibernate-entitymanager'
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.orm;\n")
                            commonModuleInfo.append("requires static com.zaxxer.hikari;\n")
                            commonModuleInfo.append("requires static java.sql;\n")
                            commonModuleInfo.append("requires static spring.tx;\n")
                        }
                    }
                    if (extension.persistence.contains('hibernate')) {
                        compileOnly 'org.hibernate:hibernate-entitymanager'
                    }

                    if (extension.cache == 'y') {
                        compileOnly "com.hazelcast:hazelcast:${RoConstants.hazelcastVersion}"
                        compileOnly "com.hazelcast:hazelcast-spring:${RoConstants.hazelcastVersion}"
                        if (isModularised) {
                            commonModuleInfo.append("requires static com.hazelcast.core;\n")
                        }
                    }

//                    if (configMap.get("monitoring")) {
//                        compileOnly 'org.springframework.boot:spring-boot-actuator-autoconfigure'
//                    }

                    if (extension.security.contains('form') || extension.security.contains('jwt')) {
                        compileOnly "org.springframework.security:spring-security-web"
                        compileOnly "org.springframework.security:spring-security-config"
                        compileOnly "com.auth0:java-jwt:${RoConstants.jwtVersion}"
                    }

                    if (extension.identityManager && project.appConfig.identityManager.idmName.name() == 'UAA') {
                        compileOnly "org.springframework.boot:spring-boot-starter-oauth2-client"
                        compileOnly "org.springframework.boot:spring-boot-starter-oauth2-resource-server"
                        compileOnly "org.springframework.security:spring-security-oauth2-jose"
                        compileOnly "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:2.2.1.RELEASE"
                    }

                    if (extension.rest == 'y') {
                        compileOnly 'org.springframework:spring-webmvc'
                        compileOnly "javax.servlet:servlet-api:${RoConstants.servletApiVersin}"
                        if (isModularised) {
                            commonModuleInfo.append("requires static spring.webmvc;\n")
                            commonModuleInfo.append("requires static servlet.api;\n")
                        }
                    }

                    if (extension.modelMapper == 'y') {
                        compileOnly "org.modelmapper:modelmapper:${RoConstants.modelMapperVersion}"
                        if (isModularised) {
                            commonModuleInfo.append("requires static modelmapper;\n")
                        }
                    }

                    if (extension.beanValidation == 'y') {
                        compileOnly 'org.springframework.boot:spring-boot-starter-validation'
                        if (isModularised) {
                            commonModuleInfo.append("requires static java.validation;\n")
                        }
                    }

                    if (extension.web == 'y') {
                        compileOnly 'org.springframework.boot:spring-boot-starter-thymeleaf'
                        if (extension.rest != 'y') {
                            compileOnly 'org.springframework:spring-webmvc'
                            compileOnly "javax.servlet:servlet-api:${RoConstants.servletApiVersin}"
                            if (isModularised) {
                                commonModuleInfo.append("requires static spring.webmvc;\n")

                            }
                        }
                    }

                    implementation "org.javassist:javassist:${RoConstants.javaAssistVersion}"

                    //Common libs
                    if (extension.persistence.contains('springData') || extension.persistence.contains('hibernate')) {
                        compileOnly 'com.zaxxer:HikariCP'
                    }
                    if (extension.queue == 'y' || extension.grpc == 'y') {
                        compileOnly "com.google.protobuf:protobuf-java:${RoConstants.protobufVersion}"
                    }

                    if (extension.autoGenerateJavaClassForProtoFiles == 'y') {
                        compileOnly "com.google.protobuf:protobuf-java-util:${RoConstants.protobufVersion}"
                    }

                    if (extension.unitTest == 'y') {
                        //Adding spring boot test frameworks to all applications
                        testImplementation('org.springframework.boot:spring-boot-starter-test') {
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
                        if (extension.dataBase.contains('couchbase') || extension.persistence.contains('hibernate') || extension.persistence.contains('springData') || extension.search == 'y') {
                            commonModuleInfo.append("requires static spring.data.jpa;\n")
                            commonModuleInfo.append("requires static spring.data.commons;\n")
                        }
                        if (extension.search == 'y' || extension.rest == 'y') {
                            commonModuleInfo.append("requires static spring.web;\n")
                        }
                        if (extension.persistence.contains('hibernate') || extension.persistence.contains('springData')) {
                            commonModuleInfo.append("requires static org.hibernate.orm.core;\n")
                        }
                       // commonModuleInfo.append("exports ${extension.commonPackage}.config;\n")
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
