package com.rico.platform

import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

/**
 * Extension for configuring ro plugin
 * 
 * @author krishna
 *
 */
class ROExtension {
	
	String appName
	String security
	String dataBase
	String persistence
	String multitenancy
	String keyvaluestore
	String cache
	String queue
	String grpc
	String grpcServer
	String grpcClient
	String rest
	String web
	String search
	String logging
	String monitoring
	String devTools
	String javaModule
	String javaMainClass

	static class IdentityManager {

		enum IdmName {
			UAA, KEYCLOAK
		}

		IdmName UAA = IdmName.UAA
		IdmName KEYCLOAK = IdmName.KEYCLOAK
		IdmName idmName
		String uaaClient, uaaResourceServer
		Project project

		IdentityManager(Project project) {
			this.project = project
		}
	}

	IdentityManager identityManager

	IdentityManager identityManager(Closure closure) {
		identityManager = new IdentityManager(this.project)
		this.project.configure(identityManager, closure)
		return identityManager
	}

	static class Docker {

		static class HostPortMapping {
			Integer hostRestPort, hostGrpcPort, hostDebugPort
		}

		static class VolumeMapping {
			String volumeName, containerPath
		}

		static class HostVolumeMapping {
			String hostPath, containerPath
		}

		enum SwarmMode {
			REPLICATED, GLOBAL
		}


		static class Swarm {
			SwarmMode swarmMode
			Integer replicas
			boolean rollbackOnUpdateFailure
		}

		static class HelmChart {
			String chartName
			String chartVersion
			Map<String, String> values = new HashMap<>()
		}


		SwarmMode REPLICATED_MODE = SwarmMode.REPLICATED
		SwarmMode GLOBAL_MODE = SwarmMode.GLOBAL

		String imageName, baseImage, containerName, networkName, serviceName
		Long memoryLimitInMB, memoryReservationInMB
		Double cpuSetLimit, cpuSetReservation
		HostPortMapping hostPortMapping
		VolumeMapping volumeMapping
		HostVolumeMapping hostVolumeMapping
		Swarm swarm
		HelmChart helmChart
		Map<String, String> environment = new HashMap<>()
		List<String> commands = new ArrayList<>()
		List<String> dns = new ArrayList<>()
		Project project

		Docker(Project project) {
			this.project = project
		}

		void hostPortMapping(Closure closure) {
			def hostPortMapping = new HostPortMapping()
			this.project.configure(hostPortMapping, closure)
			this.hostPortMapping = hostPortMapping
		}

		void volumeMapping(Closure closure) {
			def volumeMapping = new VolumeMapping()
			this.project.configure(volumeMapping, closure)
			this.volumeMapping = volumeMapping
		}

		void hostVolumeMapping(Closure closure) {
			def hostVolumeMapping = new HostVolumeMapping()
			this.project.configure(hostVolumeMapping, closure)
			this.hostVolumeMapping = hostVolumeMapping
		}

		void swarm(Closure closure) {
			def swarm = new Swarm()
			this.project.configure(swarm, closure)
			this.swarm = swarm
		}

		void helmChart(Closure closure) {
			def helmChart = new HelmChart()
			this.project.configure(helmChart, closure)
			this.helmChart = helmChart
		}

		@Override
		String toString() {
			return imageName + " " + containerName
		}
	}

	Project project
	Docker docker

	ROExtension(Instantiator instantiator,
	Project project) {
		this.project = project
	}

	Docker docker(Closure closure) {
		docker = new Docker(this.project)
		this.project.configure(docker, closure)
		return docker
	}
}
