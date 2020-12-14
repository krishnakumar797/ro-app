package com.rico.platform

import org.gradle.api.plugins.JavaPlugin

import javax.inject.Inject

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator
import org.gradle.api.tasks.compile.JavaCompile

import com.rico.platform.utils.RoConstants
/**
 * Custom ro plugin for dependency managment and tools integration
 * 
 * @author krishna
 *
 */
class RoAppPlugin implements Plugin<Project> {


	final Instantiator instantiator;
	String restPortNumber, debugPortNumber, grpcPortNumber, tagName, dbHost, buildEnv
	def portNums = []
    def jFlags = []

	@Inject
	RoAppPlugin(Instantiator instantiator) {
		this.instantiator = instantiator;
		restPortNumber = "8080"
		debugPortNumber = "8888"
		grpcPortNumber = "8090"
		tagName = System.getenv('TAG_NAME') ?: "latest"
		dbHost = System.getenv('DB_HOST') ?: "127.0.0.1"
	}

	void apply(Project project) {
		
		def extension = project.extensions.create('appConfig', ROExtension, instantiator, project)

		//Deleting module info file if exist
		def infoFile = project.file("src/main/java/module-info.java")
		if(infoFile.exists()){
			println("Clean up module info")
			infoFile.delete();
		}

		//Setting build enviroment
		buildEnv = project.hasProperty('buildEnv') ? project.findProperty('buildEnv') : "local"

		if(buildEnv == "local"){
			jFlags = ['-Xms256m', '-agentlib:jdwp=transport=dt_socket,address=0.0.0.0:'+debugPortNumber+',server=y,suspend=n', '-Dspring.profiles.active=local', '-Dspring.devtools.restart.enabled=false']
			portNums.add(debugPortNumber)
		} else {
			jFlags = ['-Xms256m', '-Dspring.devtools.restart.enabled=false', '-Dspring.profiles.active=${buildEnv}']
		}

		def date = new Date()
		project.afterEvaluate {

			def props = new Properties()

			project.configure(project) {
				
				//Applying Java Modules
				if(extension.javaModule) {
					apply plugin: "org.beryx.jlink"
					application {
						mainModule = extension.javaMainClass
						mainClass = extension.javaMainClass
					}
					project.plugins.withType(JavaPlugin).configureEach {
						java {
							modularity.inferModulePath = true
						}
					}
					jlink {
						options = ['--strip-debug', '--compress', '2', '--no-header-files', '--no-man-pages']
						launcher{
							name = extension.javaMainClass
						}
					}
				} else {
					application {
						mainClass = extension.javaMainClass
					}
				}
				
				//Applying docker plugin
				if(extension.docker){

					apply plugin: 'com.google.cloud.tools.jib'

					println "Docker Details - User: ${RoConstants.dockerUser} Password:  ${RoConstants.dockerPassword} ImageName: ${RoConstants.dockerRegistry}/${extension.docker.imageName}"
					/**
					 Jib containerization
					 **/
					jib {
						from {
							image = 'azul/zulu-openjdk-alpine:11.0.7-jre'
						}
						to {
							image = "${RoConstants.dockerRegistry}/${extension.docker.imageName}"
							tags = [tagName]
							auth {
								username = RoConstants.dockerUser
								password = RoConstants.dockerPassword
							}
						}
						container {
							jvmFlags = jFlags
							ports = portNums
							labels = [key1:'value1', key2:'value2']
							mainClass = extension.javaMainClass
						}
						allowInsecureRegistries = true
					}


					def portMappings = ""
					if(extension.docker.hostPortMapping){
						if(extension.docker.hostPortMapping.hostRestPort){
							portMappings = portMappings.concat("${extension.docker.hostPortMapping.hostRestPort}:${restPortNumber}").concat(",")
						}
						if(extension.docker.hostPortMapping.hostGrpcPort){
							portMappings = portMappings.concat("${extension.docker.hostPortMapping.hostGrpcPort}:${grpcPortNumber}").concat(",")
						}
						if(extension.docker.hostPortMapping.hostDebugPort){
							portMappings = portMappings.concat("${extension.docker.hostPortMapping.hostDebugPort}:${debugPortNumber}")
						}
					}
					def portMappingArray = portMappings.split(",") as String[]

					if(!portMappings.isEmpty()){
						println "Ports Opened - ${portMappings}"
					}

					extension.docker.environment.put('DB_HOST', dbHost)

					if(extension.docker.environment.size() !=0) {
						println "Environment - ${extension.docker.environment}"
					}
					def volumeMappings = [:]
					if(extension.docker.volumeMapping){
						if(extension.docker.volumeMapping.volumeName && extension.docker.volumeMapping.containerPath){
							volumeMappings.put(extension.docker.volumeMapping.volumeName, extension.docker.volumeMapping.containerPath)
						}
					}
					

					if(extension.docker.swarm == null){
						def networkName = 'bridge'
						if(extension.docker.networkName) {
							networkName = extension.docker.networkName
						}
						apply plugin: 'com.rico.platform.dockerRun'
						//Applying docker run
						dockerRun {
							name extension.docker.containerName
							image extension.docker.imageName
							tag tagName
							ports portMappingArray
							network networkName
							volumes volumeMappings
							command extension.docker.commands
							env extension.docker.environment
							memoryLimitInMB extension.docker.memoryLimitInMB
							memoryReservationInMB extension.docker.memoryReservationInMB
							cpuSetLimit extension.docker.cpuSetLimit
							cpuSetReservation extension.docker.cpuSetReservation
						}
					} else {
						def networkName = 'ingress'
						if(extension.docker.networkName) {
							networkName = extension.docker.networkName
						}
						apply plugin : 'com.rico.platform.swarm'
						println "Service Name - "+extension.docker.serviceName
						//Applying Swarm service
                        swarm {
							name extension.docker.containerName
							image extension.docker.imageName
							tag tagName
							ports portMappingArray
							network networkName
							volumes volumeMappings
							command extension.docker.commands
							env extension.docker.environment
							serviceName extension.docker.serviceName
							swarmMode extension.docker.swarm.swarmMode.name()
							replicas extension.docker.swarm.replicas
							rollbackOnUpdateFailure extension.docker.swarm.rollbackOnUpdateFailure
							memoryLimitInMB extension.docker.memoryLimitInMB
							memoryReservationInMB extension.docker.memoryReservationInMB
							cpuSetLimit extension.docker.cpuSetLimit
							cpuSetReservation extension.docker.cpuSetReservation
						}
					}
				}
			}

			project.with {
				// Defining dependencies
				dependencies {
					
					def moduleInfo

					def propertyFile = file("src/main/resources/projectInfo.properties")

					implementation 'org.springframework.boot:spring-boot-starter'
					
					if(extension.javaModule) {
						moduleInfo = file("src/main/java/module-info.java")
						moduleInfo.write("module ${extension.javaMainClass} {\n")
						moduleInfo.append("requires spring.boot;\n")
						moduleInfo.append("requires spring.context;\n")
						moduleInfo.append("requires spring.beans;\n")
						moduleInfo.append("requires java.annotation;\n")
						moduleInfo.append("requires spring.boot.autoconfigure;\n")
						moduleInfo.append("requires static lombok;\n")
						moduleInfo.append("requires com.rico.common;\n")
						
					}

					//Adding spring boot test frameworks to all applications
					testImplementation('org.springframework.boot:spring-boot-starter-test') {
						exclude group: 'org.junit.vintage', module: 'junit-vintage-engine'
					}
					// JUnit testing
					testImplementation ("org.mockito:mockito-core:3.4.0")
					testImplementation 'org.mockito:mockito-inline:3.4.6'
					testImplementation ("org.junit.jupiter:junit-jupiter-api:${RoConstants.junitTestVersion}")
					testRuntimeOnly ("org.junit.jupiter:junit-jupiter-engine:${RoConstants.junitTestVersion}")
					testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.5.2")


					if(extension.security == 'y'){
						if(extension.persistence != 'springData' && extension.persistence != 'hibernate') {
							throw new GradleException('Security cant be configured without persistence config for the application')
						}
						implementation 'org.springframework.boot:spring-boot-starter-security'
						implementation 'org.springframework.security:spring-security-test'
						props.setProperty("security.enabled", 'y')
					}
					if(extension.monitoring == 'y'){
						implementation 'org.springframework.boot:spring-boot-starter-actuator'
					}
					if(extension.devTools == 'y'){
						developmentOnly 'org.springframework.boot:spring-boot-devtools'
					}
					if(extension.rest == 'y'){
						runtimeOnly "org.modelmapper:modelmapper:${RoConstants.modelMapperVersion}"
						implementation 'org.springframework.boot:spring-boot-starter-web'
						implementation 'org.springframework.boot:spring-boot-starter-validation'
						props.setProperty('spring.mvc.throw-exception-if-no-handler-found', 'true')
						if(extension.security != 'y'){
							props.setProperty("spring.autoconfigure.exclude", 'org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration')
						}

						jFlags.add('-Dserver.port='+restPortNumber)
						portNums.add(restPortNumber)
						
						if(extension.javaModule) {
							moduleInfo.append("requires spring.web;\n")
							moduleInfo.append("requires java.validation;\n")
						}
					}
					if(extension.search == 'y'){
						implementation 'org.springframework.data:spring-data-elasticsearch'

						if(extension.rest != 'y'){
							runtimeOnly('org.springframework.boot:spring-boot-starter-web'){
								exclude module: "spring-boot-tomcat"
							}
						}
					}
					if(extension.dataBase == 'couchbase') {
						implementation 'org.springframework.data:spring-data-couchbase'
						props.setProperty("database", 'couchbase')
					}
					if(extension.keyvaluestore == 'y') {
						runtimeOnly 'org.springframework.data:spring-data-redis'
						runtimeOnly 'io.lettuce:lettuce-core'
					}
					if(extension.cache == 'y') {
						runtimeOnly "com.hazelcast:hazelcast:${RoConstants.hazelcastVersion}"
						runtimeOnly "com.hazelcast:hazelcast-spring:${RoConstants.hazelcastVersion}"
						props.setProperty("cache.enabled", 'y')
					}
					if(extension.queue == 'y') {
						implementation 'org.springframework.kafka:spring-kafka'
						runtimeOnly "com.google.protobuf:protobuf-java:${RoConstants.protobufVersion}"
						runtimeOnly 'de.ruedigermoeller:fst:2.56'
					}
					// For both grpc server and grpc client
					if(extension.grpc == 'y') {
						testImplementation("io.grpc:grpc-testing:${RoConstants.grpcUnitTestVersion}")
						if(extension.rest != 'y'){
							runtimeOnly 'jakarta.validation:jakarta.validation-api'
						}
						implementation "net.devh:grpc-spring-boot-starter:${RoConstants.grpcVersion}"

						portNums.add(grpcPortNumber)

					}else if(extension.grpcServer == 'y') {
						testImplementation("io.grpc:grpc-testing:${RoConstants.grpcUnitTestVersion}")
						// For grpc server
						if(extension.rest != 'y'){
							runtimeOnly 'jakarta.validation:jakarta.validation-api'
						}
						implementation "net.devh:grpc-server-spring-boot-starter:${RoConstants.grpcVersion}"
						runtimeOnly "io.grpc:grpc-stub:${RoConstants.protocJavaVersion}"

						portNums.add(grpcPortNumber)

					}else if(extension.grpcClient == 'y') {
						testImplementation("io.grpc:grpc-testing:${RoConstants.grpcUnitTestVersion}")
						// For grpc client
						implementation "net.devh:grpc-client-spring-boot-starter:${RoConstants.grpcVersion}"
					}

					if(extension.grpc == 'y' || extension.grpcClient == 'y') {
						implementation "io.grpc:grpc-stub:${RoConstants.protocJavaVersion}"
					}
					if(extension.grpc == 'y' || extension.grpcServer == 'y' || extension.grpcClient == 'y') {
						runtimeOnly "io.grpc:grpc-protobuf:${RoConstants.protocJavaVersion}"
						runtimeOnly "io.grpc:grpc-netty-shaded:${RoConstants.protocJavaVersion}"
					}

					if(extension.persistence == 'springData') {
						implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
						props.setProperty("persistence", 'springData')
						if(extension.javaModule) {
							moduleInfo.append("requires spring.data.jpa;\n")
							moduleInfo.append("requires spring.data.commons;\n")
							moduleInfo.append("requires java.persistence;\n")
							moduleInfo.append("requires spring.tx;\n")
						}
					}

					if(extension.persistence == 'hibernate') {
						implementation "org.springframework:spring-tx:${RoConstants.springVersion}"
						implementation "jakarta.persistence:jakarta.persistence-api"
						runtimeOnly 'org.springframework.data:spring-data-jpa'
						runtimeOnly "org.springframework:spring-aspects:${RoConstants.springVersion}"
						runtimeOnly "org.springframework:spring-orm:${RoConstants.springVersion}"
						runtimeOnly 'org.hibernate:hibernate-core'
						runtimeOnly 'com.zaxxer:HikariCP'

						props.setProperty("persistence", 'hibernate')
					}

					if(extension.dataBase == 'mysql') {
						runtimeOnly 'mysql:mysql-connector-java'
						props.setProperty("dataBase", 'mysql')
					}

					if(extension.dataBase == 'postgres') {
						runtimeOnly 'org.postgresql:postgresql'
						props.setProperty("dataBase", 'postgres')
					}

					if(extension.multitenancy == 'y'){
						props.setProperty("multitenancy.enabled", 'y')
					}
					if(extension.logging == 'y') {
						implementation 'org.springframework.boot:spring-boot-starter-log4j2'
						props.setProperty("log.enabled", 'y')
						if(extension.javaModule) {
						  moduleInfo.append("requires org.apache.logging.log4j;\n")
						}
					}

					//Writing project infos
					props.setProperty("version", project.version)
					props.store propertyFile.newWriter(), "DO NOT MODIFY THIS FILE"
					
					//Ending java modules block
					if(extension.javaModule) {
						moduleInfo.append("}")

					}
				}

				//Defining custom build task
				def buildApp = tasks.register("build-${project.name}") {
					group = 'ro'
					description = 'Build application'
				}

				buildApp.configure {
					doFirst {
						if(!project.appConfig.appName) {
							throw new GradleException('App name is not configured for the application')
						}
						if(!project.appConfig.javaMainClass) {
							throw new GradleException('No Java Main class is configured for the application')
						}
						if(project.appConfig.dataBase) {
							if(project.appConfig.dataBase != "couchbase" && project.appConfig.dataBase != "postgres" && project.appConfig.dataBase != "mysql" ){
								throw new GradleException('Unsupported database '+project.appConfig.dataBase+". Supports only couchbase, postgres and mysql.")
							}
						}
						if(project.appConfig.persistence) {
							if(project.appConfig.dataBase == "couchbase"){
								throw new GradleException('Spring data persistence is implicitly configured for '+project.appConfig.dataBase+". Remove any persistence layer already added.")
							}
							if(project.appConfig.persistence != "springData" && project.appConfig.persistence != "hibernate"){
								throw new GradleException('Unsupported persistence layer '+project.appConfig.persistence+'. Supports only springData and hibernate.')
							}
						}
						if(project.appConfig.multitenancy) {
							if(project.appConfig.dataBase != "postgres"){
								throw new GradleException('Multitenancy is not supported for '+project.appConfig.dataBase+'. Supports only postgres database.')
							}
							if(project.appConfig.persistence != "springData"){
								throw new GradleException('Multitenancy is not supported for '+project.appConfig.persistence+'. Supports only springData.')
							}
							if(!project.appConfig.rest || !project.appConfig.grpc || !project.appConfig.grpcServer){
								throw new GradleException('Rest Or Grpc Server should be enabled for Multitenancy support')
							}
						}
						String enabledServices=""
						if(project.appConfig.dataBase){
							enabledServices += project.appConfig.dataBase +" "
						}
						if(project.appConfig.rest){
							enabledServices += "rest "
						}
						if(project.appConfig.persistence){
							enabledServices += project.appConfig.persistence +" "
						}
						if(project.appConfig.logging){
							enabledServices += "logging "
						}
						if(project.appConfig.keyvaluestore){
							enabledServices += "redis "
						}
						if(project.appConfig.cache){
							enabledServices += "hazelcast "
						}
						if(project.appConfig.search){
							enabledServices += "elastic-search "
						}
						if(project.appConfig.queue){
							enabledServices += "kafka "
						}
						if(project.appConfig.grpc){
							enabledServices += "grpc "
						}
						if(project.appConfig.grpcServer){
							enabledServices += "grpc-server "
						}
						if(project.appConfig.grpcClient){
							enabledServices += "grpc-client "
						}
						println "Enabled services are : ${enabledServices}"
					}
					dependsOn build
				}

				//Defining custom run task
				def runApp = tasks.register("run-${project.name}") {
					group = 'ro'
					description = 'Run application'
					dependsOn buildApp
					finalizedBy bootRun
				}
			}
		}

	}
}
