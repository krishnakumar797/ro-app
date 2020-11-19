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
	String search
	String logging
	String monitoring
	String devTools
	String javaMainClass

	static class Docker {

		static class HostPortMapping {
			Integer hostRestPort, hostGrpcPort, hostDebugPort
		}

		static class VolumeMapping {
			String volumeName, containerPath
		}

		enum SwarmMode {
			REPLICATED, GLOBAL
		}


		static class Swarm {
			SwarmMode swarmMode
			Integer replicas
			boolean rollbackOnUpdateFailure
		}

		SwarmMode REPLICATED_MODE = SwarmMode.REPLICATED
		SwarmMode GLOBAL_MODE = SwarmMode.GLOBAL

		String imageName, containerName, networkName, serviceName
		Long memoryLimitInMB, memoryReservationInMB
		Double cpuSetLimit, cpuSetReservation
		HostPortMapping hostPortMapping
		VolumeMapping volumeMapping
		Swarm swarm
		Map<String, String> environment = new HashMap<>()
		List<String> commands = new ArrayList<>()
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

		void swarm(Closure closure) {
			def swarm = new Swarm()
			this.project.configure(swarm, closure)
			this.swarm = swarm
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
