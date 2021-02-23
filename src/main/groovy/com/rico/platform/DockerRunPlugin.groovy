package com.rico.platform

import java.util.Map.Entry

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.command.InspectExecResponse
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.command.RemoveContainerCmd
import com.github.dockerjava.api.command.StartContainerCmd
import com.github.dockerjava.api.command.StopContainerCmd
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Mount
import com.github.dockerjava.api.model.MountType
import com.github.dockerjava.api.model.Network
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.api.model.RestartPolicy
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient

/**
 * Docker run plugin
 *
 */
class DockerRunPlugin implements Plugin<Project> {

	private DockerClient dockerClient

	private String dockerRegistry, dockerUser, dockerPassword, dockerHost


	public DockerRunPlugin() {
		//Getting system environment variables and configuring the docker client
		println "Initializing docker config"
		dockerRegistry = System.getenv('DOCKER_REGISTRY') ?: "localhost:5000"
		dockerUser = System.getenv('DOCKER_USER') ?: "0"
		dockerPassword = System.getenv('DOCKER_PASSWORD') ?: "0"
		dockerHost = System.getenv('DOCKER_HOST') ?: ""

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
		DockerRunExtension ext = project.extensions.create('dockerRun', DockerRunExtension)

		DefaultTask dockerRunStatus = project.tasks.create('dockerRunStatus', DefaultTask, {
			group = 'Docker Run'
			description = 'Checks the run status of the container'
		})

		DefaultTask dockerRun = project.tasks.create('dockerRun', DefaultTask, {
			group = 'Docker Run'
			description = 'Runs the specified container with port mappings'
		})

		DefaultTask dockerStop = project.tasks.create('dockerStop', DefaultTask, {
			group = 'Docker Run'
			description = 'Stops the named container if it is running'
		})

		DefaultTask dockerRemoveContainer = project.tasks.create('dockerRemoveContainer', DefaultTask, {
			group = 'Docker Run'
			description = 'Removes the persistent container associated with the Docker Run tasks'
		})

		DefaultTask dockerNetworkModeStatus = project.tasks.create('dockerNetworkModeStatus', DefaultTask, {
			group = 'Docker Run'
			description = 'Checks the network configuration of the container'
		})

		project.afterEvaluate {
			dockerRunStatus.configure {
				doLast {
					this.execInspect(ext.name, dockerClient)
				}
			}

			dockerNetworkModeStatus.configure {
				doLast {
					this.execNetworkInspect(ext.network, dockerClient)
				}
			}

			dockerRun.configure {
				doLast {
					Map<String,String> args = new HashMap<>()
					//Adding clean
					if (ext.clean) {
						args.put("clean", "y")
					}
					//Adding network name
					if (ext.network) {
						args.put('network', ext.network)
					}
					//Adding memory limits and memory reservation
					if (ext.memoryLimitInMB != null) {
						args.put("memoryLimitInMB", ext.memoryLimitInMB.toString())
					}
					if (ext.memoryReservationInMB != null) {
						args.put("memoryReservationInMB", ext.memoryReservationInMB.toString())
					}

					//Adding cpu limits
					if (ext.cpuSetLimit != null) {
						args.put("cpuSetLimit", String.valueOf(ext.cpuSetLimit))
					}
					
					//Adding ports
					ExposedPort[] exposedPortArray = new ExposedPort[ext.ports.size()]
					Ports portBindings = new Ports();
					for (int i=0;i<ext.ports.size();i++) {
						String[] portMapping = ext.ports[i].split(':');
						exposedPortArray[i] = ExposedPort.tcp(portMapping[1].toInteger());
						portBindings.bind(exposedPortArray[i], Ports.Binding.bindPort(portMapping[0].toInteger()));
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
					this.execCreateAndRun(this.dockerRegistry+"/"+ext.image, ext.tag, ext.name, args, exposedPortArray, portBindings, mountList, envs, ext.command, dockerClient)
				}
			}

			dockerStop.configure {
				doLast {

					this.execStop(ext.name, dockerClient)
				}
			}

			dockerRemoveContainer.configure {
				doLast{

					this.execRemove(ext.name, dockerClient)
				}
			}
		}
	}


	/**
	 * Method to inspect the docker
	 *
	 * @param containerName
	 * @param dockerClient
	 * @return
	 */
	private void execInspect(String containerName, DockerClient dockerClient) {
		InspectExecResponse inspectExecResponse = dockerClient.inspectExecCmd(containerName).exec();
		println inspectExecResponse.inspect();
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
	private void execCreateAndRun(String imageRepository, String tag, String name, Map<String,String> args, ExposedPort[] exposedPortArray, Ports portBindings,
			List<Mount> mountList, List<String> envs, List<String> commandList, DockerClient dockerClient) {
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
		//Creating container
		CreateContainerCmd containerCmd = dockerClient.createContainerCmd(imageRepository+":"+tag).withName(name)

		HostConfig hc = new HostConfig();
		hc.withNetworkMode(args.get("network"))

		if(args.get("memoryLimitInMB") != null) {
			hc.withMemory((Long.parseLong(args.get("memoryLimitInMB"))*1000000))
		}
		if(args.get("cpuSetLimit") != null) {
			hc.withNanoCPUs((Long)(Double.parseDouble(args.get("cpuSetLimit"))*1000000000))
		}
	
		if(args.get("memoryReservationInMB") != null) {
			hc.withMemoryReservation((Long.parseLong(args.get("memoryReservationInMB"))*1000000))
		}
		
		if(exposedPortArray.length >0) {
			containerCmd = containerCmd.withExposedPorts(exposedPortArray)
		}
		if(portBindings.getBindings().size() > 0) {
			hc = hc.withPortBindings(portBindings)
		}
		if(!mountList.isEmpty()) {
			hc=	hc.withMounts(mountList)
		}
		if(!envs.isEmpty()) {
			containerCmd = containerCmd.withEnv(envs)
		}
		if(!commandList.isEmpty()) {
			containerCmd = containerCmd.withCmd(commandList)
		}
		//Setting restart policy
		hc = hc.withRestartPolicy(RestartPolicy.alwaysRestart())
		println "Starting the container "+name
		CreateContainerResponse container = containerCmd.withHostConfig(hc).withAttachStdout(true).withAttachStdin(true).withAttachStderr(true).withTty(false).exec();
		StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(container.getId()).exec();
	}

	/**
	 * Method to remove a container
	 * 
	 * @param containerName
	 * @param dockerClient
	 * @return
	 */
	private void execRemove(String containerName, DockerClient dockerClient) {
		RemoveContainerCmd removeContainerCmd = dockerClient.removeContainerCmd(containerName).exec();
	}


	/**
	 * Method to stop a container
	 *
	 * @param containerName
	 * @param dockerClient
	 * @return
	 */
	private void execStop(String containerName, DockerClient dockerClient) {
		StopContainerCmd stopContainerCmd = dockerClient.stopContainerCmd(containerName).exec();
	}
}
