package com.amazonwebservices.blogs.containers.kubernetes.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.annotations.SerializedName;

import io.swagger.annotations.ApiModelProperty;

public class ScalingBehavior {
	
	public static final String SERIALIZED_POLICIES = "policies";
	@SerializedName(SERIALIZED_POLICIES)
	private List<ScalingPolicy> policies;
	
	public static final String SERIALIZED_COOLDOWN = "coolDown";
	@SerializedName(SERIALIZED_COOLDOWN)
	private long coolDown;
	
	//
	// Cooldown duration
	//
	@ApiModelProperty(required = true, value = "Cooldown duration")
	public long getCoolDown() {
		return coolDown;
	}

	public void setCoolDown(long coolDown) {
		this.coolDown = coolDown;
	}

	public ScalingBehavior coolDown(long duration) {
		this.coolDown = duration;
		return this;
	}
	
	//
	// Scaling policies
	//
	@ApiModelProperty(required = true, value = "Scaling policies")
	public List<ScalingPolicy> getPolicies() {
		return policies;
	}
	
	public void setPolicies(List<ScalingPolicy> policies) {
		this.policies = policies;
	}
	
	public ScalingBehavior policies(List<ScalingPolicy> policies) {
		this.policies = policies;
		return this;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		
		ScalingBehavior that = (ScalingBehavior) o;
		Collections.<ScalingPolicy>sort(this.policies);
		Collections.<ScalingPolicy>sort(that.policies);
		return Objects.equals(this.policies, that.policies)
			&& Objects.equals(this.coolDown, that.coolDown);

	}
	
	@Override
	public int hashCode() {
		return Objects.hash(policies);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("class ScalingBehavior {\n");
		sb.append("    policies: ").append(toIndentedString(policies)).append("\n");
		sb.append("    coolDown: ").append(toIndentedString(coolDown)).append("\n");
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
