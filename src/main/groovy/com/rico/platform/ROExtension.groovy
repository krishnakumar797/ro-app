package com.rico.platform

import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

/**
 * Extension for configuring ro plugin
 * 
 * @author krishna
 *
 */
class ROExtension extends RoBaseExtension {
	
	String appName
	String grpcServer
	String grpcClient
	String monitoring
	String devTools
	String javaMainClass

	Project project
	Docker docker

	ROExtension(Instantiator instantiator,
	Project project) {
		super(instantiator, project)
		this.project = project
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

		static class HealthCheck {
			String healthCheckCmd
			Integer healthCheckIntervalInSec
			Long healthCheckInitialDelayInSec
		}

		enum SwarmMode {
			REPLICATED, GLOBAL
		}


		static class Swarm {
			SwarmMode swarmMode
			Integer replicas
			String serviceName
			boolean rollbackOnUpdateFailure
		}

		static class HelmChart {
			String chartName
			String chartVersion
			Map<String, String> values = new HashMap<>()
		}


		SwarmMode REPLICATED_MODE = SwarmMode.REPLICATED
		SwarmMode GLOBAL_MODE = SwarmMode.GLOBAL

		String imageName, baseImage, containerName, networkName, creationTime, filesModificationTime
		Long memoryLimitInMB, memoryReservationInMB
		Double cpuSetLimit, cpuSetReservation
		HostPortMapping hostPortMapping
		VolumeMapping volumeMapping
		HostVolumeMapping hostVolumeMapping
		HealthCheck healthCheck
		Swarm swarm
		HelmChart helmChart
		Map<String, String> environment = new HashMap<>()
		List<String> commands = new ArrayList<>()
		List<String> dns = new ArrayList<>()
		Map<String, String> labels = new HashMap<>()
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

		void healthCheck(Closure closure) {
			def healthCheck = new HealthCheck()
			this.project.configure(healthCheck, closure)
			this.healthCheck = healthCheck
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

	Docker docker(Closure closure) {
		docker = new Docker(this.project)
		this.project.configure(docker, closure)
		return docker
	}
}
