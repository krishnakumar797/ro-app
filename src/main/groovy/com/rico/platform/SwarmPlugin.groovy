package com.rico.platform

import java.util.Map.Entry

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.CreateServiceResponse
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.command.RemoveServiceCmd
import com.github.dockerjava.api.model.ContainerSpec
import com.github.dockerjava.api.model.EndpointResolutionMode
import com.github.dockerjava.api.model.EndpointSpec
import com.github.dockerjava.api.model.Mount
import com.github.dockerjava.api.model.MountType
import com.github.dockerjava.api.model.Network
import com.github.dockerjava.api.model.NetworkAttachmentConfig
import com.github.dockerjava.api.model.PortConfig
import com.github.dockerjava.api.model.PortConfigProtocol
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.api.model.ResourceRequirements
import com.github.dockerjava.api.model.ResourceSpecs
import com.github.dockerjava.api.model.Service
import com.github.dockerjava.api.model.ServiceGlobalModeOptions
import com.github.dockerjava.api.model.ServiceModeConfig
import com.github.dockerjava.api.model.ServiceReplicatedModeOptions
import com.github.dockerjava.api.model.ServiceRestartCondition
import com.github.dockerjava.api.model.ServiceRestartPolicy
import com.github.dockerjava.api.model.ServiceSpec
import com.github.dockerjava.api.model.TaskSpec
import com.github.dockerjava.api.model.UpdateConfig
import com.github.dockerjava.api.model.UpdateFailureAction
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient

/**
 * Docker Swarm plugin
 *
 */
class SwarmPlugin implements Plugin<Project> {

	private DockerClient dockerClient
	private String dockerRegistry, dockerUser, dockerPassword, dockerHost

	public SwarmPlugin() {
		//Getting system environment variables and configuring the docker client
		println "Initializing swarm config"

		dockerRegistry = System.getenv('DOCKER_REGISTRY') ?: "localhost:5000"
		dockerUser = System.getenv('DOCKER_USER') ?: "0"
		dockerPassword = System.getenv('DOCKER_PASSWORD') ?: "0"
		dockerHost = System.getenv('DOCKER_HOST') ?: "tcp://127.0.0.1:2375"

		DockerClientConfig config = null

		if(!dockerUser.contentEquals("0") && !dockerPassword.contentEquals("0")) {
			config = DefaultDockerClientConfig.createDefaultConfigBuilder()
					.withDockerHost(dockerHost)
					.withDockerTlsVerify(false)
					.withRegistryUsername(dockerUser)
					.withRegistryPassword(dockerPassword)
					.withRegistryUrl(dockerRegistry)
					.build();
		}else {
			config = DefaultDockerClientConfig.createDefaultConfigBuilder()
					.withDockerHost(dockerHost)
					.withDockerTlsVerify(false)
					.withRegistryUrl(dockerRegistry)
					.build();
		}

		DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
				.dockerHost(config.getDockerHost())
				.build();

		dockerClient = DockerClientImpl.getInstance(config, httpClient);
		dockerClient.pingCmd().exec().inspect()
	}

	@Override
	void apply(Project project) {
		DockerRunExtension ext = project.extensions.create('swarm', DockerRunExtension)

		DefaultTask swarmServiceStatus = project.tasks.create('swarmServiceStatus', DefaultTask, {
			group = 'Swarm'
			description = 'Checks the run status of the swarm service'
		})

		DefaultTask swarmServiceRun = project.tasks.create('swarmServiceRun', DefaultTask, {
			group = 'Swarm'
			description = 'Runs the specified service with port mappings'
		})

		DefaultTask swarmServiceStop = project.tasks.create('swarmServiceStop', DefaultTask, {
			group = 'Swarm'
			description = 'Stops the named service if it is running'
		})

		DefaultTask swarmRemoveService = project.tasks.create('swarmRemoveService', DefaultTask, {
			group = 'Swarm'
			description = 'Removes the service associated with the Swarm Service Run'
		})

		DefaultTask swarmNetworkModeStatus = project.tasks.create('swarmNetworkModeStatus', DefaultTask, {
			group = 'Swarm'
			description = 'Checks the network configuration of the container'
		})

		project.afterEvaluate {
			swarmServiceStatus.configure {
				doLast {
					this.execInspect(ext.serviceName, dockerClient)
				}
			}

			swarmNetworkModeStatus.configure {
				doLast {
					this.execNetworkInspect(ext.network, dockerClient)
				}
			}

			swarmServiceRun.configure {
				doLast {
					Map<String,String> args = new HashMap<>()
					args.put("mode", ext.swarmMode == null?ext.swarmMode:"REPLICATED")
					args.put("replicas", ext.replicas == null?ext.replicas:1)
					//Adding network name
					if (ext.network) {
						args.put("network", ext.network)
					}
					//Adding memory limits and memory reservation
					if (ext.memoryLimitInMB != null) {
						args.put("memoryLimitInMB", ext.memoryLimitInMB.toString())
					}
					if (ext.memoryReservationInMB != null) {
						args.put("memoryReservationInMB", ext.memoryReservationInMB.toString())
					}

					//Adding cpu limits and cpu reservation
					if (ext.cpuSetLimit != null) {
						args.put("cpuSetLimit", String.valueOf(ext.cpuSetLimit))
					}
					if (ext.cpuSetReservation != null) {
						args.put("cpuSetReservation", String.valueOf(ext.cpuSetReservation))
					}

					args.put("rollbackonUpdateFailure", ext.rollbackOnUpdateFailure?"1":"0")
					//Adding ports
					List<PortConfig> pConfigList = new ArrayList()
					for (int i=0;i<ext.ports.size();i++) {
						String[] portMapping = ext.ports[i].split(':');
						PortConfig pConfig = new PortConfig()
						pConfig = pConfig.withProtocol(PortConfigProtocol.TCP)
						pConfig = pConfig.withPublishedPort(portMapping[0].toInteger())
						pConfig = pConfig.withTargetPort(portMapping[1].toInteger())
						pConfigList.add(pConfig)
					}
					//Adding volumes
					List<Mount> mountList = new ArrayList<>()
					for (Entry<String,String> volume : ext.volumes.entrySet()) {
						Mount mount = new Mount();
						mount.withType(MountType.VOLUME)
						mount.withSource(volume.getKey()).withTarget(volume.getValue())
						mountList.add(mount)
					}
					//Adding environments
					final List<String> envs = new ArrayList<>(ext.env.size());
					for (Entry<String,String> env : ext.env.entrySet()) {
						envs.add(env.getKey() + '=' + env.getValue());
					}

					//					if (!ext.arguments.isEmpty()) {
					//						args.addAll(ext.arguments)
					//					}

					println "Image Name - "+ext.image
					//Creating and starting docker container
					this.execCreateAndRun(this.dockerRegistry+"/"+ext.image, ext.tag, ext.serviceName, args, pConfigList, mountList, envs, ext.command, dockerClient)
				}
			}

			swarmServiceStop.configure {
				doLast {

					this.execStop(ext.serviceName, dockerClient)
				}
			}

			swarmRemoveService.configure {
				doLast{

					this.execRemove(ext.serviceName, dockerClient)
				}
			}
		}
	}


	/**
	 * Method to inspect the swarm service
	 *
	 * @param serviceId
	 * @param dockerClient
	 * @return
	 */
	private void execInspect(String serviceName, DockerClient dockerClient) {
		Service service = dockerClient.inspectServiceCmd(serviceName).exec();
		println service.inspect();
	}

	/**
	 * Method to inspect the network
	 * 
	 * @param networkId
	 * @param dockerClient
	 * @return
	 */
	private void execNetworkInspect(String networkId, DockerClient dockerClient) {
		Network network = dockerClient.inspectNetworkCmd().withNetworkId(networkId).exec();
		println network.inspect();
	}

	/**
	 * Method to create and run the docker
	 *  
	 * @param imageRepository
	 * @param registry
	 * @param tag
	 * @param name
	 * @param dockerClient
	 * @param commands
	 */
	private void execCreateAndRun(String imageRepository, String tag, String serviceName, Map<String,String> args, List<PortConfig> pConfigList,
			List<Mount> mountList, List<String> envs, List<String> commandList, DockerClient dockerClient) {
		//Pulling docker image
		ByteArrayOutputStream bos = new ByteArrayOutputStream()
		ResultCallback.Adapter<PullResponseItem> resultCallback = new PullImageResultCallback() {
					@Override
					public void onNext(PullResponseItem item) {
						if (item.getStatus() != null && item.getProgressDetail() != null) {
							println item.getId() + ":" + item.getStatus()
						}
						super.onNext(item);
					}
				};
		dockerClient.pullImageCmd(imageRepository).withTag(tag).exec(resultCallback)
		resultCallback.awaitCompletion()

		//Creating container spec
		ContainerSpec containerSpec = new ContainerSpec().withImage(imageRepository+":"+tag)
		if(!mountList.isEmpty()) {
			containerSpec=	containerSpec.withMounts(mountList)
		}
		if(!envs.isEmpty()) {
			containerSpec = containerSpec.withEnv(envs)
		}
		if(!commandList.isEmpty()) {
			containerSpec = containerSpec.withCommand(commandList)
		}

		//Creating service
		ServiceSpec serviceSpec = new ServiceSpec()
		serviceSpec.withName(serviceName)

		//Creating TaskSpec
		TaskSpec taskSpec = new TaskSpec().withContainerSpec(containerSpec)

		//Adding resource requirements
		ResourceRequirements resourceRequirements = new ResourceRequirements()
		ResourceSpecs resourceSpecsLimits = new ResourceSpecs()
		if(args.get("memoryLimitInMB") != null) {
			resourceSpecsLimits.withMemoryBytes((Long.parseLong(args.get("memoryLimitInMB"))*1000000))
		}
		if(args.get("cpuSetLimit") != null) {
			resourceSpecsLimits.withNanoCPUs((Long)(Double.parseDouble(args.get("cpuSetLimit"))*1000000000))
		}
		if(args.get("memoryLimitInMB") != null || args.get("cpuSetLimit") != null) {
			resourceRequirements.withLimits(resourceSpecsLimits)
		}
	
		ResourceSpecs resourceSpecsReservation = new ResourceSpecs()		
		if(args.get("memoryReservationInMB") != null) {
			resourceSpecsReservation.withMemoryBytes((Long.parseLong(args.get("memoryReservationInMB"))*1000000))
		}
		if(args.get("cpuSetReservation") != null) {
			resourceSpecsReservation.withNanoCPUs((Long)(Double.parseDouble(args.get("cpuSetReservation"))*1000000000))
		}
		if(args.get("memoryReservationInMB") != null || args.get("cpuSetReservation") != null) {
			resourceRequirements.withReservations(resourceSpecsReservation)
		}
        taskSpec.withResources(resourceRequirements)


		//Adding restart policy
		ServiceRestartPolicy policy = new ServiceRestartPolicy()
		policy.withCondition(ServiceRestartCondition.ON_FAILURE)
		taskSpec.withRestartPolicy(policy)

		//Attaching network
		if(!args.get("network").contentEquals("ingress")) {
			List<NetworkAttachmentConfig> networkAttachmentlist = new ArrayList<>()
			NetworkAttachmentConfig networkConfig = new NetworkAttachmentConfig()
			networkConfig.withTarget(args.get("network"))
			networkAttachmentlist.add(networkConfig)
			taskSpec.withNetworks(networkAttachmentlist)
		}

		serviceSpec.withTaskTemplate(taskSpec)

		//Adding Replicated or Global services
		ServiceModeConfig serviceModeConfig = new ServiceModeConfig()
		if(args.get("mode").contentEquals("REPLICATED")) {
			ServiceReplicatedModeOptions serviceReplicatedModeOptions = new ServiceReplicatedModeOptions()
			serviceReplicatedModeOptions.withReplicas(args.get("replicas"))
			serviceModeConfig.withReplicated(serviceReplicatedModeOptions)
		} else {
			ServiceGlobalModeOptions serviceGlobalModeOptions = new ServiceGlobalModeOptions()
			serviceModeConfig.withGlobal(serviceGlobalModeOptions)
		}
		serviceSpec.withMode(serviceModeConfig)
		//Adding update config
		UpdateConfig updateConfig = new UpdateConfig()
		updateConfig.withParallelism(1)
		if(args.get("rollbackonUpdateFailure").contentEquals("1")) {
			updateConfig.withFailureAction(UpdateFailureAction.ROLLBACK)
		} else {
			updateConfig.withFailureAction(UpdateFailureAction.CONTINUE)
		}
		serviceSpec.withUpdateConfig(updateConfig)

		//Attaching published ports
		if(!pConfigList.isEmpty()) {
			EndpointSpec endpoint = new EndpointSpec()
			endpoint.withMode(EndpointResolutionMode.VIP)
			endpoint.withPorts(pConfigList)
			serviceSpec.withEndpointSpec(endpoint)
		}

		println "Starting the service "+serviceName
		CreateServiceResponse serviceResponse = dockerClient.createServiceCmd(serviceSpec).exec()

	}

	/**
	 * Method to remove a Service
	 * 
	 * @param serviceId
	 * @param dockerClient
	 * @return
	 */
	private void execRemove(String serviceName, DockerClient dockerClient) {
		List<String> serviceNameList = new ArrayList<>()
		serviceNameList.add(serviceName)
		dockerClient.listServicesCmd().withNameFilter(serviceNameList).exec().forEach{ service ->
			RemoveServiceCmd removeServiceCmd = dockerClient.removeServiceCmd(service.getId()).exec();
		}
	}


	/**
	 * Method to stop a service
	 *
	 * @param serviceId
	 * @param dockerClient
	 * @return
	 */
	private void execStop(String serviceName, DockerClient dockerClient) {
		List<String> serviceNameList = new ArrayList<>()
		serviceNameList.add(serviceName)
		dockerClient.listServicesCmd().withNameFilter(serviceNameList).exec().forEach{ service ->
			ServiceSpec serviceSpec = service.getSpec();
			ServiceModeConfig serviceModeConfig = serviceSpec.getMode();
			ServiceReplicatedModeOptions serviceReplicatedModeOptions = serviceModeConfig.getReplicated().withReplicas(0);
			serviceModeConfig.withReplicated(serviceReplicatedModeOptions);
			serviceSpec.withMode(serviceModeConfig);
			dockerClient.updateServiceCmd(service.getId(), serviceSpec).withVersion(service.getVersion().getIndex()).exec();
		}
	}
}
