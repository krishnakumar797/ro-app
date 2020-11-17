package com.rico.platform

import javax.inject.Inject

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

import com.rico.platform.utils.RoUtils
/**
 * Common ro plugin for dependency managment
 * 
 * @author krishna
 *
 */
class RoCommonPlugin implements Plugin<Project> {


	final Instantiator instantiator;

	@Inject
	RoCommonPlugin(Instantiator instantiator) {
		this.instantiator = instantiator;
	}

	void apply(Project project) {

		project.afterEvaluate {

			project.with {
				dependencies {
					compileOnly "org.springframework.security:spring-security-web"
					compileOnly "org.springframework.security:spring-security-config"

					compileOnly 'org.springframework.data:spring-data-couchbase'
					compileOnly 'org.springframework.kafka:spring-kafka'

					compileOnly 'org.springframework.data:spring-data-redis'
					compileOnly "com.google.protobuf:protobuf-java:${RoUtils.protobufVersion}"

					compileOnly "net.devh:grpc-spring-boot-starter:${RoUtils.grpcVersion}"
					compileOnly "io.grpc:grpc-protobuf:${RoUtils.protocJavaVersion}"
					compileOnly "io.grpc:grpc-stub:${RoUtils.protocJavaVersion}"
					compileOnly "io.grpc:grpc-netty-shaded:${RoUtils.protocJavaVersion}"

					compileOnly 'io.lettuce:lettuce-core'
					compileOnly 'de.ruedigermoeller:fst:2.56'

					compileOnly 'org.apache.logging.log4j:log4j-api'
					compileOnly 'org.apache.logging.log4j:log4j-core'

					compileOnly 'org.springframework.data:spring-data-elasticsearch'
					compileOnly 'org.springframework.data:spring-data-jpa'
					compileOnly 'org.hibernate:hibernate-entitymanager'
					compileOnly 'mysql:mysql-connector-java'
					compileOnly 'org.postgresql:postgresql'
					compileOnly 'com.zaxxer:HikariCP'

					compileOnly "com.hazelcast:hazelcast:${RoUtils.hazelcastVersion}"
					compileOnly "com.hazelcast:hazelcast-spring:${RoUtils.hazelcastVersion}"

					compileOnly 'org.springframework.boot:spring-boot-autoconfigure'
					compileOnly 'org.springframework.boot:spring-boot-actuator-autoconfigure'
					compileOnly "org.modelmapper:modelmapper:${RoUtils.modelMapperVersion}"
					compileOnly 'org.springframework:spring-webmvc'
					compileOnly 'jakarta.validation:jakarta.validation-api'
					compileOnly "javax.servlet:servlet-api:${RoUtils.servletApiVersin}"

					implementation "org.javassist:javassist:${RoUtils.javaAssistVersion}"
				}
			}
		}
	}
}
