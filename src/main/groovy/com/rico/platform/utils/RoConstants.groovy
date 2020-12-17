package com.rico.platform.utils

/**
 * Constants for RoApp
 * 
 * @author krishna
 *
 */
interface RoConstants {
    // Dependent library versions
	def protobufVersion = "3.11.4"
	def protocJavaVersion = "1.27.1"
	def grpcVersion = "2.10.1.RELEASE"
	def springVersion = "5.2.3.RELEASE"
	def hazelcastVersion = "4.0.2"
	def servletApiVersin = "2.5"
	def modelMapperVersion = "2.3.5"
	def junitTestVersion = "5.5.2"
	def grpcUnitTestVersion = "1.32.1"
	def javaAssistVersion = "3.27.0-GA"
	def jakartaVersion = "2.0.0"
	def elasticSearch = "7.10.0"
	def jwtVersion = "3.11.0"
	
	//Environment variables
	String dockerRegistry = System.getenv('DOCKER_REGISTRY') ?: "localhost:5000"
	String dockerUser = System.getenv('DOCKER_USER') ?: "0"
	String dockerPassword = System.getenv('DOCKER_PASSWORD') ?: "0"
	String dockerHost = System.getenv('DOCKER_HOST') ?: "tcp://127.0.0.1:2375"
}
