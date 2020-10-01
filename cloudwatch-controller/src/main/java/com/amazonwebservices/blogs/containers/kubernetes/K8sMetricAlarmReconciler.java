package com.amazonwebservices.blogs.containers.kubernetes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsResult;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.cloudwatch.model.MetricDataQuery;
import com.amazonaws.services.cloudwatch.model.MetricStat;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmResult;
import com.amazonaws.services.cloudwatch.model.Tag;
import com.amazonwebservices.blogs.containers.kubernetes.model.K8sMetricAlarmCustomObject;
import com.amazonwebservices.blogs.containers.kubernetes.model.K8sMetricAlarmCustomObjectList;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.generic.GenericKubernetesApi;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.spring.extended.controller.annotation.AddWatchEventFilter;
import io.kubernetes.client.spring.extended.controller.annotation.DeleteWatchEventFilter;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesReconciler;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesReconcilerReadyFunc;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesReconcilerWatch;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesReconcilerWatches;
import io.kubernetes.client.spring.extended.controller.annotation.UpdateWatchEventFilter;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

//
// Reconciler beans that are annotated with @KubernetesReconciler annotation are processed by KubernetesReconcilerProcessor which is an implementation of Spring’s BeanFactoryPostProcessor interface.
// This bean post-processor handles the task of creating a Controller for each Reconciler. 
// The Controller will be named based on the value attribute of @KubernetesReconciler annotation.
// Additionally, under the hoods, it creates ControllerWatch instances for each of the KubernetesReconcilerWatch annotations on the Reconciler bean 
// so that the Controller is set up to handle add/update/delete event notifications pertaining to all the Kubernetes resources identified by those annotations
//
@KubernetesReconciler(
		value = "k8sMetricAlarmController", 
		workerCount = 2,
		watches = @KubernetesReconcilerWatches({
			@KubernetesReconcilerWatch(
					workQueueKeyFunc = WorkQueueKeyFunFactory.KubernetesMetricAlarmCustomObjectWorkQueueKeyFunc.class,
					apiTypeClass = K8sMetricAlarmCustomObject.class, 
					resyncPeriodMillis = 60*1000L)
			}))
public class K8sMetricAlarmReconciler implements Reconciler {
	private static final Logger logger = LogManager.getLogger(K8sMetricAlarmReconciler.class);
	
	final AmazonCloudWatch cloudWatchClient = AmazonCloudWatchClientBuilder.defaultClient();

	private GenericKubernetesApi<K8sMetricAlarmCustomObject, K8sMetricAlarmCustomObjectList> apiCloudWatchAlarm;
	private SharedInformer<K8sMetricAlarmCustomObject> k8sMetricAlarmInformer;
	private Map<String,K8sMetricAlarmCustomObject> deletedObjects = new HashMap<String,K8sMetricAlarmCustomObject>();
	private Map<String,K8sMetricAlarmCustomObject> addedObjects = new HashMap<String,K8sMetricAlarmCustomObject>();
	private Map<String,K8sMetricAlarmCustomObject> updatedObjects = new HashMap<String,K8sMetricAlarmCustomObject>();
	
	public K8sMetricAlarmReconciler(ApiClient apiClient, SharedInformer<K8sMetricAlarmCustomObject> cloudWatchAlarmInformer) {
		this.k8sMetricAlarmInformer = cloudWatchAlarmInformer;
		
		this.apiCloudWatchAlarm = new GenericKubernetesApi<K8sMetricAlarmCustomObject, K8sMetricAlarmCustomObjectList>(
				K8sMetricAlarmCustomObject.class, 
				K8sMetricAlarmCustomObjectList.class,
				"containerinsights.eks.com", 
				"v1", 
				"k8smetricalarms", 
				apiClient);
	}

	@KubernetesReconcilerReadyFunc
	public boolean informerReady() {
		return k8sMetricAlarmInformer.hasSynced();
	}

	@AddWatchEventFilter(apiTypeClass = K8sMetricAlarmCustomObject.class)
	public boolean onAddFilter(K8sMetricAlarmCustomObject cloudWatchAlarm) {
		String name = cloudWatchAlarm.getMetadata().getName();
		String namespace = cloudWatchAlarm.getMetadata().getNamespace();
		addedObjects.put(name.concat(namespace), cloudWatchAlarm);
		logger.info(String.format("Handling onAdd event for CloudWatchAlarm custom resource %s.%s", name, namespace));
		return true;
	}

	@UpdateWatchEventFilter(apiTypeClass = K8sMetricAlarmCustomObject.class)
	public boolean onUpdateFilter(K8sMetricAlarmCustomObject oldCloudWatchAlarm, K8sMetricAlarmCustomObject newCloudWatchAlarm) {
		if (oldCloudWatchAlarm.equals(newCloudWatchAlarm)) return false;
		String name = newCloudWatchAlarm.getMetadata().getName();
		String namespace = newCloudWatchAlarm.getMetadata().getNamespace();
		updatedObjects.put(name.concat(namespace), newCloudWatchAlarm);
		logger.info(String.format("Handling onUpdate event for CloudWatchAlarm custom resource %s.%s", name, namespace));
		return true;
	}

	@DeleteWatchEventFilter(apiTypeClass = K8sMetricAlarmCustomObject.class)
	public boolean onDeleteFilter(K8sMetricAlarmCustomObject cloudWatchAlarm, boolean deletedFinalStateUnknown) {
		String name = cloudWatchAlarm.getMetadata().getName();
		String namespace = cloudWatchAlarm.getMetadata().getNamespace();
		deletedObjects.put(name.concat(namespace), cloudWatchAlarm);
		logger.info(String.format("Handling onDelete event for CloudWatchAlarm custom resource %s.%s", name, namespace));
		return true;
	}

	@Override
	public Result reconcile(Request request) {
		logger.info(String.format("Triggered reconciliation for %s.%s", request.getName(), request.getNamespace()));
		modifyCloudWatchAlarm (request.getName(), request.getNamespace());
		return new Result(false);
	}
	
	private void modifyCloudWatchAlarm (String name, String namespace) {
		try {
			boolean isAdded = false;
			boolean isUpdated = false;
			boolean isDeleted = false;
			
			K8sMetricAlarmCustomObject cloudWatchAlarm = null;
			String objKey = name.concat(namespace);
			if (addedObjects.containsKey(objKey)) {
				isAdded = true;
				cloudWatchAlarm = addedObjects.get(objKey);
				addedObjects.remove(objKey);
			}
			else if (updatedObjects.containsKey(objKey)) {
				isUpdated = true;
				cloudWatchAlarm = updatedObjects.get(objKey);
				updatedObjects.remove(objKey);
			}
			else if (deletedObjects.containsKey(objKey)) {
				isDeleted = true;
				cloudWatchAlarm = deletedObjects.get(objKey);
				deletedObjects.remove(objKey);
			}
			
			JsonObject scaleUpAlarmConfigObject = parseCloudWatchAlarmConfig (cloudWatchAlarm.getSpec().getScaleUpAlarmConfig());
			JsonObject scaleDownAlarmConfigObject = parseCloudWatchAlarmConfig (cloudWatchAlarm.getSpec().getScaleDownAlarmConfig());
			logger.info(String.format("Scale up alarm configuration:\n %s", scaleUpAlarmConfigObject.encodePrettily()));			
			logger.info(String.format("Scale down alarm configuration:\n %s", scaleDownAlarmConfigObject.encodePrettily()));
			
			if (isAdded) {
				createCloudWatchAlarm (scaleUpAlarmConfigObject);
				createCloudWatchAlarm (scaleDownAlarmConfigObject);
			}
			else if (isUpdated) {
				deleteCloudWatchAlarm (scaleUpAlarmConfigObject);
				deleteCloudWatchAlarm (scaleDownAlarmConfigObject);

				createCloudWatchAlarm (scaleUpAlarmConfigObject);
				createCloudWatchAlarm (scaleDownAlarmConfigObject);
			}
			else if (isDeleted) {
				deleteCloudWatchAlarm (scaleUpAlarmConfigObject);
				deleteCloudWatchAlarm (scaleDownAlarmConfigObject);
			}
		} catch (Exception e) {
			logger.error(String.format("Exception occured when updating CloudWatchAlarm '%s.%s'; %s", name, namespace, e.getMessage()), e);
		}
	}
	
	
	private JsonObject parseCloudWatchAlarmConfig (String config) {
		JsonObject configObject = new JsonObject(config);
		return configObject;
	}
	
	private void deleteCloudWatchAlarm (JsonObject config) {
		DeleteAlarmsRequest request = new DeleteAlarmsRequest().withAlarmNames(config.getString("AlarmName"));
		DeleteAlarmsResult response = cloudWatchClient.deleteAlarms(request);
		logger.info(String.format("Successfully deleted CloudWatch Metric Alarm '%s'", config.getString("AlarmName")));
	}
	
	private void createCloudWatchAlarm (JsonObject config) throws Exception {
		
		List<Tag> tags = new ArrayList<Tag> ();
		if (config.containsKey("Tags")) {
			JsonArray tagsArray = config.getJsonArray("Tags");
			for (int i = 0; i < tagsArray.size(); i++) {
				JsonObject tagObject = tagsArray.getJsonObject(i);
				tags.add(new Tag()
						.withKey(tagObject.getString("Key"))
						.withValue(tagObject.getString("Value")));
			}
		}
		else {
			throw new Exception ("Cannot create CloudWatch Alarm without specifying tags");
		}
		
		if (config.containsKey("Metrics")) {
			List<MetricDataQuery> metricsDataQueryCollection = new ArrayList<MetricDataQuery>();
			JsonArray metricsArray = config.getJsonArray("Metrics");
			for (int i = 0; i < metricsArray.size(); i++) {
				
				JsonObject metricsDataQueryObject = metricsArray.getJsonObject(i);
				
				if (metricsDataQueryObject.containsKey("MetricStat")) {
					JsonObject metricStatObject = metricsDataQueryObject.getJsonObject("MetricStat");
					JsonObject metricObject = metricStatObject.getJsonObject("Metric");
					JsonArray dimensionsArray = metricObject.getJsonArray("Dimensions");
					
					List<Dimension> dimensions = new ArrayList<Dimension> ();
					for (int d = 0; d < dimensionsArray.size(); d++) {
						JsonObject dimensionObject = dimensionsArray.getJsonObject(d);
						dimensions.add(new Dimension()
								.withName(dimensionObject.getString("Name"))
								.withValue(dimensionObject.getString("Value")));
					}

					Metric metric = new Metric()
							.withMetricName(metricObject.getString("MetricName"))
							.withNamespace(metricObject.getString("Namespace"))
							.withDimensions(dimensions);
					
					MetricStat metricStat = new MetricStat()
							.withMetric(metric)
							.withPeriod(metricStatObject.getInteger("Period"))
							.withStat(metricStatObject.getString("Stat"));
					
					MetricDataQuery metricDataQuery = new MetricDataQuery()
							.withId(metricsDataQueryObject.getString("Id"))
							.withLabel(metricsDataQueryObject.getString("Label"))
							.withMetricStat(metricStat)				
							.withReturnData(metricsDataQueryObject.getBoolean("ReturnData"));
					
					metricsDataQueryCollection.add(metricDataQuery);
				}
				else if (metricsDataQueryObject.containsKey("Expression")) {
					
					MetricDataQuery metricDataQuery = new MetricDataQuery()
							.withId(metricsDataQueryObject.getString("Id"))
							.withLabel(metricsDataQueryObject.getString("Label"))
							.withPeriod(metricsDataQueryObject.getInteger("Period"))
							.withExpression(metricsDataQueryObject.getString("Expression"))
							.withReturnData(metricsDataQueryObject.getBoolean("ReturnData"));
					
					metricsDataQueryCollection.add(metricDataQuery);
				}
			}
			
			JsonArray alarmActionsArray = config.getJsonArray("AlarmActions");
			List<String> alarmActions = new ArrayList<String>();
			for (int j = 0; j < alarmActionsArray.size(); j++) {
				alarmActions.add(alarmActionsArray.getString(j));
			}
			
			PutMetricAlarmRequest request = new PutMetricAlarmRequest()
				    .withAlarmName(config.getString("AlarmName"))
				    .withAlarmDescription(config.getString("AlarmDescription"))
				    .withActionsEnabled(config.getBoolean("ActionsEnabled"))
				    .withAlarmActions(alarmActions)
				    .withEvaluationPeriods(config.getInteger("EvaluationPeriods"))
				    .withDatapointsToAlarm(config.getInteger("DatapointsToAlarm"))
				    .withThreshold(config.getDouble("Threshold"))
				    .withComparisonOperator(config.getString("ComparisonOperator"))
				    .withMetrics(metricsDataQueryCollection)
				    .withTags(tags);
				  
			
			PutMetricAlarmResult response = cloudWatchClient.putMetricAlarm(request);
			logger.info(String.format("Successfully created CloudWatch Metric Alarm '%s'", config.getString("AlarmName")));
		}
	}
}

