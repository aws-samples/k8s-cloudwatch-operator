package com.amazonwebservices.blogs.containers.kubernetes.model;

import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;

@ApiModel(description = "K8sMetricAlarmCustomObjectSpec describes how a user wants their resource to appear")
public class K8sMetricAlarmCustomObjectSpec {
	public static final String SERIALIZED_MIN_REPLICAS = "minReplicas";
	@SerializedName(SERIALIZED_MIN_REPLICAS)
	private int minReplicas;
	
	public static final String SERIALIZED_MAX_REPLICAS = "maxReplicas";
	@SerializedName(SERIALIZED_MAX_REPLICAS)
	private int maxReplicas;
	
	public static final String SERIALIZED_DEPLOYMENT = "deployment";
	@SerializedName(SERIALIZED_DEPLOYMENT)
	private String deployment;
	
	public static final String SERIALIZED_SCALEUP_BEHAVIOR = "scaleUpBehavior";
	@SerializedName(SERIALIZED_SCALEUP_BEHAVIOR)
	private ScalingBehavior scaleUpBehavior;
	
	public static final String SERIALIZED_SCALEDOWN_BEHAVIOR = "scaleDownBehavior";
	@SerializedName(SERIALIZED_SCALEDOWN_BEHAVIOR)
	private ScalingBehavior scaleDownBehavior;

	public static final String SERIALIZED_SCALEUP_COFIG = "scaleUpAlarmConfig";
	@SerializedName(SERIALIZED_SCALEUP_COFIG)
	private String scaleUpAlarmConfig;
	
	public static final String SERIALIZED_SCALEDOWN_COFIG = "scaleDownAlarmConfig";
	@SerializedName(SERIALIZED_SCALEDOWN_COFIG)
	private String scaleDownAlarmConfig;
	
	//
	// Minimum replicas
	//
	@ApiModelProperty(required = true, value = "Minimum replicas")
	public int getMinReplicas() {
		return minReplicas;
	}

	public void setMinReplicas(int minReplicas) {
		this.minReplicas = minReplicas;
	}
	
	public K8sMetricAlarmCustomObjectSpec minReplicas(int minReplicas) {
		this.minReplicas = minReplicas;
		return this;
	}
	
	//
	// Maximum replicas
	//
	@ApiModelProperty(required = true, value = "Maximum replicas")
	public int getMaxReplicas() {
		return maxReplicas;
	}

	public void setMaxReplicas(int maxReplicas) {
		this.maxReplicas = maxReplicas;
	}
	
	public K8sMetricAlarmCustomObjectSpec maxReplicas(int maxReplicas) {
		this.maxReplicas = maxReplicas;
		return this;
	}
	
	//
	// Scale Up Behavior
	//
	@ApiModelProperty(required = true, value = "Scaleup Behavior")
	public ScalingBehavior getScaleUpBehavior() {
		return scaleUpBehavior;
	}

	public void setScaleUpBehavior(ScalingBehavior behavior) {
		this.scaleUpBehavior = behavior;
	}
	
	public K8sMetricAlarmCustomObjectSpec scaleUpBehavior(ScalingBehavior behavior) {
		this.scaleUpBehavior = behavior;
		return this;
	}

	//
	// Scale Down Behavior
	//
	@ApiModelProperty(required = true, value = "Scaledown Behavior")
	public ScalingBehavior getScaleDownBehavior() {
		return scaleDownBehavior;
	}

	public void setScaleDownBehavior(ScalingBehavior behavior) {
		this.scaleDownBehavior = behavior;
	}
	
	public K8sMetricAlarmCustomObjectSpec scaleDownBehavior(ScalingBehavior behavior) {
		this.scaleDownBehavior = behavior;
		return this;
	}

	//
	// Deployment
	//
	@ApiModelProperty(required = true, value = "Deployment")
	public String getDeployment() {
		return deployment;
	}

	public void setDeployment(String deployment) {
		this.deployment = deployment;
	}
	
	public K8sMetricAlarmCustomObjectSpec deployment(String deployment) {
		this.deployment = deployment;
		return this;
	}

	//
	// Scale Up Alarm Configuration
	//
	@ApiModelProperty(required = true, value = "Scaleup Alarm Configuration")
	public K8sMetricAlarmCustomObjectSpec scaleUpAlarmConfig(String config) {
		this.scaleUpAlarmConfig = config;
		return this;
	}

	public String getScaleUpAlarmConfig() {
		return scaleUpAlarmConfig;
	}

	public void setScaleUpAlarmConfig(String scaleUpAlarmConfig) {
		this.scaleUpAlarmConfig = scaleUpAlarmConfig;
	}

	//
	// Scale Down Alarm Configuration
	//
	@ApiModelProperty(required = true, value = "Scaledown Alarm Configuration")
	public K8sMetricAlarmCustomObjectSpec scaleDownAlarmConfig(String config) {
		this.scaleDownAlarmConfig = config;
		return this;
	}

	public String getScaleDownAlarmConfig() {
		return scaleDownAlarmConfig;
	}

	public void setScaleDownAlarmConfig(String scaleDownAlarmConfig) {
		this.scaleDownAlarmConfig = scaleDownAlarmConfig;
	}

	@Override
	public boolean equals(java.lang.Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		K8sMetricAlarmCustomObjectSpec that = (K8sMetricAlarmCustomObjectSpec) o;
		return Objects.equals(this.minReplicas, that.minReplicas)
			&& Objects.equals(this.maxReplicas, that.maxReplicas)
			&& Objects.equals(this.deployment, that.deployment)
			&& Objects.equals(this.scaleUpBehavior, that.scaleUpBehavior)
			&& Objects.equals(this.scaleDownBehavior, that.scaleDownBehavior)
			&& Objects.equals(this.scaleUpAlarmConfig, that.scaleUpAlarmConfig)
			&& Objects.equals(this.scaleDownAlarmConfig, that.scaleDownAlarmConfig);
	}

	@Override
	public int hashCode() {
		return Objects.hash(minReplicas, maxReplicas, deployment, scaleUpBehavior, scaleDownBehavior, scaleUpAlarmConfig, scaleDownAlarmConfig);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("class V1K8sMetricAlarmCustomObjectSpec {\n");
		sb.append("    minReplicas: ").append(toIndentedString(minReplicas)).append("\n");
		sb.append("    maxReplicas: ").append(toIndentedString(maxReplicas)).append("\n");
		sb.append("    deployment: ").append(toIndentedString(deployment)).append("\n");
		sb.append("    scaleUpBehavior: ").append(toIndentedString(scaleUpBehavior)).append("\n");
		sb.append("    scaleDownBehavior: ").append(toIndentedString(scaleDownBehavior)).append("\n");
		sb.append("    scaleUpAlarmConfig: ").append(toIndentedString(scaleUpAlarmConfig)).append("\n");
		sb.append("    scaleDownAlarmConfig: ").append(toIndentedString(scaleDownAlarmConfig)).append("\n");
		sb.append("}");
		return sb.toString();
	}

	private String toIndentedString(java.lang.Object o) {
		if (o == null) {
			return "null";
		}
		return o.toString().replace("\n", "\n    ");
	}
}
