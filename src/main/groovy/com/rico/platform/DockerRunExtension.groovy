package com.rico.platform

import static com.google.common.base.Preconditions.checkNotNull

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet

/**
 * Docker run extensions
 *
 */
class DockerRunExtension {

	private String name
	private String serviceName
	private String image
	private String tag
	private String network
	private String swarmMode
	private Integer replicas
	private Long memoryLimitInMB
	private Long memoryReservationInMB
	private Double cpuSetLimit
	private Double cpuSetReservation
	private boolean rollbackOnUpdateFailure
	private Set<String> ports = ImmutableSet.of()
	private Map<String,String> env = ImmutableMap.of()
	private List<String> arguments = ImmutableList.of()
	private List<String> command = ImmutableList.of()
	private Map<String,String> volumes = ImmutableMap.of()
	private boolean clean = false

	public String getName() {
		return name
	}

	public void setName(String name) {
		this.name = name
	}


	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public String getSwarmMode() {
		return swarmMode;
	}

	public void setSwarmMode(String swarmMode) {
		this.swarmMode = swarmMode;
	}

	public Integer getReplicas() {
		return replicas;
	}

	public void setReplicas(Integer replicas) {
		this.replicas = replicas;
	}

	public boolean isRollbackOnUpdateFailure() {
		return rollbackOnUpdateFailure;
	}

	public void setRollbackOnUpdateFailure(boolean rollbackOnUpdateFailure) {
		this.rollbackOnUpdateFailure = rollbackOnUpdateFailure;
	}

	public boolean getClean() {
		return clean
	}

	public void setClean(boolean clean) {
		this.clean = clean
	}

	public String getImage() {
		return image
	}

	public void setImage(String image) {
		this.image = image
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public Set<String> getPorts() {
		return ports
	}

	public List<String> getCommand() {
		return command
	}

	public Map<Object,String> getVolumes() {
		return volumes
	}

	public void command(List<String> command) {
		this.command = ImmutableList.copyOf(command)
	}

	public void setNetwork(String network) {
		this.network = network
	}

	public String getNetwork() {
		return network
	}

	private void setEnvSingle(String key, String value) {
		this.env.put(checkNotNull(key, "key"), checkNotNull(value, "value"))
	}

	public void env(Map<String,String> env) {
		this.env = ImmutableMap.copyOf(env)
	}

	public Map<String, String> getEnv() {
		return env
	}

	public void arguments(String... arguments) {
		this.arguments = ImmutableList.copyOf(arguments)
	}

	public List<String> getArguments() {
		return arguments
	}


	public Long getMemoryLimitInMB() {
		return memoryLimitInMB;
	}

	public void setMemoryLimitInMB(Long memoryLimitInMB) {
		this.memoryLimitInMB = memoryLimitInMB;
	}

	public Long getMemoryReservationInMB() {
		return memoryReservationInMB;
	}

	public void setMemoryReservationInMB(Long memoryReservationInMB) {
		this.memoryReservationInMB = memoryReservationInMB;
	}

	public Double getCpuSetLimit() {
		return cpuSetLimit;
	}

	public void setCpuSetLimit(Double cpuSetLimit) {
		this.cpuSetLimit = cpuSetLimit;
	}

	public Double getCpuSetReservation() {
		return cpuSetReservation;
	}

	public void setCpuSetReservation(Double cpuSetReservation) {
		this.cpuSetReservation = cpuSetReservation;
	}

	public void ports(String[] ports) {
		ImmutableSet.Builder builder = ImmutableSet.builder()
		for (String port : ports) {
			String[] mapping = port.split(':', 2)
			if (mapping.length == 1) {
				checkPortIsValid(mapping[0])
				builder.add("${mapping[0]}:${mapping[0]}")
			} else {
				checkPortIsValid(mapping[0])
				checkPortIsValid(mapping[1])
				builder.add("${mapping[0]}:${mapping[1]}")
			}
		}
		this.ports = builder.build()
	}

	public void volumes(Map<Object,String> volumes) {
		this.volumes = ImmutableMap.copyOf(volumes)
	}

	private static void checkPortIsValid(String port) {
		if(port == null || port.isEmpty()){
			return
		}
		int val = Integer.parseInt(port)
		Preconditions.checkArgument(0 < val && val <= 65536, "Port must be in the range [1,65536]")
	}
}