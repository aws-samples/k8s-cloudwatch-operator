package com.amazonwebservices.blogs.containers.kubernetes;

import java.util.function.Function;

import com.amazonwebservices.blogs.containers.kubernetes.model.K8sMetricAlarmCustomObject;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

public interface WorkQueueKeyFunFactory {
	class KubernetesMetricAlarmCustomObjectWorkQueueKeyFunc implements Function<K8sMetricAlarmCustomObject, Request> {
		@Override
		public Request apply(K8sMetricAlarmCustomObject obj) {
			V1ObjectMeta objectMeta = obj.getMetadata();
			return new Request(objectMeta.getNamespace(), objectMeta.getName());
		}
	}
}
