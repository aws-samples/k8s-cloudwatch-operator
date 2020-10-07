package com.amazonwebservices.blogs.containers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.ListTagsForResourceRequest;
import com.amazonaws.services.cloudwatch.model.ListTagsForResourceResult;
import com.amazonaws.services.cloudwatch.model.SetAlarmStateRequest;
import com.amazonaws.services.cloudwatch.model.StateValue;
import com.amazonaws.services.cloudwatch.model.Tag;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNS;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.amazonwebservices.blogs.containers.kubernetes.model.K8sMetricAlarmCustomObject;
import com.amazonwebservices.blogs.containers.kubernetes.model.K8sMetricAlarmCustomObjectList;
import com.amazonwebservices.blogs.containers.kubernetes.model.K8sMetricAlarmCustomObjectSpec;
import com.amazonwebservices.blogs.containers.kubernetes.model.ScalingBehavior;
import com.amazonwebservices.blogs.containers.kubernetes.model.ScalingPolicy;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.CustomClientBuilder;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.vertx.core.json.JsonObject;

public class CloudWatchAlarmHandler implements RequestHandler<SNSEvent, Object> {
	
	private static final Logger logger = LogManager.getLogger(CloudWatchAlarmHandler.class);
	
	private enum ComparisonOperator {GreaterThanOrEqualToThreshold, GreaterThanThreshold, LessThanOrEqualToThreshold, LessThanThreshold};

	private final static String K8S_NAME = "kubernetes-name";
	private final static String K8S_NAMESPACE = "kubernetes-namespace";
	
	private final static String AWS_REGION = "us-east-1";
	
	private final static String ANNOTATION_ALARM_NAME = "cloudwatch.alarm.name";	
	private final static String ANNOTATION_ALARM_TRIGGER_REASON = "cloudwatch.alarm.trigger.reason";	
	private final static String ANNOTATION_ALARM_TRIGGER_TIME = "cloudwatch.alarm.trigger.time";

	final AmazonCloudWatch cloudWatchClient = AmazonCloudWatchClientBuilder.defaultClient();

    final private static DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.dateTime();

	private ApiClient apiClient = null;
	private GenericKubernetesApi<K8sMetricAlarmCustomObject, K8sMetricAlarmCustomObjectList> apiCloudWatchAlarm = null;
	private GenericKubernetesApi<V1Deployment, V1DeploymentList> apiDeployment = null;
	private boolean isInitialized = false;
	
	public void initialize () {
		try {
			logger.info("Intializing the API client");
			apiClient = CustomClientBuilder.custom();
			
			this.apiCloudWatchAlarm = new GenericKubernetesApi<K8sMetricAlarmCustomObject, K8sMetricAlarmCustomObjectList>(
					K8sMetricAlarmCustomObject.class, 
					K8sMetricAlarmCustomObjectList.class,
					"containerinsights.eks.com", 
					"v1", 
					"k8smetricalarms", 
					apiClient);
			
			this.apiDeployment = new GenericKubernetesApi<V1Deployment, V1DeploymentList>(
					V1Deployment.class, 
					V1DeploymentList.class,
					"apps", 
					"v1", 
					"deployments", 
					apiClient);
			}
		catch (Exception ex) {
			logger.error("Exception initializating the Kubernetes API client", ex);
		}
	}
	
	@Override
	public Object handleRequest(SNSEvent input, Context context) {
		if (!isInitialized) {
			initialize();
			isInitialized = true;
		}
		
		for (SNSRecord record : input.getRecords()) {
		
			//
			// Get the ARN of the CloudWatch alarm that triggered the SNS notification
			//
			SNS event = record.getSNS();
			String alarmMessage = event.getMessage();
			JsonObject alarmMessageObject = new JsonObject (alarmMessage);
			processCloudWatchAlarmMessage(alarmMessageObject);
		}
		
		return null;
	}

	private void processCloudWatchAlarmMessage (JsonObject alarmMessageObject) {
		logger.info(alarmMessageObject);
		
		String alarmName = alarmMessageObject.getString("AlarmName");
		String accountID = alarmMessageObject.getString("AWSAccountId");
		String alarmTriggerReason = alarmMessageObject.getString("NewStateReason");
		String alarmArn = String.format("arn:aws:cloudwatch:%s:%s:alarm:%s", AWS_REGION, accountID, alarmName);
		ComparisonOperator operator = Enum.valueOf(ComparisonOperator.class, alarmMessageObject.getJsonObject("Trigger").getString("ComparisonOperator"));
		
		logger.info(String.format("Alarm ARN = %s", alarmArn));
		logger.info(String.format("Reason for Trigger = %s", alarmTriggerReason));
		
		//
		// Get the name/namespace of the K8sMetricAlarm custom resource from the tags associated with the CloudWatch alarm
		//
		ListTagsForResourceRequest request = new ListTagsForResourceRequest().withResourceARN(alarmArn);
		ListTagsForResourceResult response = cloudWatchClient.listTagsForResource(request);
		List<Tag> tags = response.getTags();
		String resourceName = null;
		String resoueceNamespace = null;
		for (Tag t : tags) {
			switch (t.getKey()) {
				case K8S_NAME:
					resourceName = t.getValue();
					break;
				case K8S_NAMESPACE:
					resoueceNamespace = t.getValue();
					break;
				default:
					break;
			}
		}
		if (resourceName == null || resoueceNamespace == null) {
			logger.error(String.format("Unable to identify the Kubernetes name and namespace of the K8sMetricAlarm custom resource for alarm '%s'", alarmName));
			return;
		}
		
		//
		// Fetch the K8sMetricAlarm custom resource from the API server
		// The custom resource contains the name of the Deployment resource to be scaled
		//
		logger.info(String.format("Retrieving K8sMetricAlarm custom resource '%s.%s'", resourceName, resoueceNamespace));
		K8sMetricAlarmCustomObject cloudWatchAlarm = apiCloudWatchAlarm.get(resoueceNamespace, resourceName).getObject();
		String alarmStateResetReason;
		if (cloudWatchAlarm != null) {
			K8sMetricAlarmCustomObjectSpec cloudWatchAlarmSpec = cloudWatchAlarm.getSpec();
			int minReplicas = cloudWatchAlarmSpec.getMinReplicas();
			int maxReplicas = cloudWatchAlarmSpec.getMaxReplicas();
			ScalingBehavior scaleUpBehavior = cloudWatchAlarmSpec.getScaleUpBehavior();
			ScalingBehavior scaleDownBehavior = cloudWatchAlarmSpec.getScaleDownBehavior();
			String deploymentName = cloudWatchAlarmSpec.getDeployment();

			//
			// Fetch the Deployment resource from the API server
			// Compute the number of replicas to be scaled up or down based on scaling policies
			// Update the Deployment resource with the new number of replicas.
			//
			logger.info(String.format("Retrieving Deployment resource '%s.%s'", deploymentName, resoueceNamespace));
			V1Deployment deployment = apiDeployment.get(resoueceNamespace, deploymentName).getObject();
			V1ObjectMeta metadata = deployment.getMetadata();
			boolean isCoolingDown = isResourceCoolingDown (metadata, operator, scaleUpBehavior, scaleDownBehavior);
			if (isCoolingDown) {
				alarmStateResetReason = String.format("Deployment '%s.%s' is still cooling down. Suspending further scaling", deploymentName, resoueceNamespace);
				logger.info(alarmStateResetReason);
			}
			else {
				int replicas = deployment.getSpec().getReplicas();
				int scaledReplicas = computeScaling(operator, minReplicas, maxReplicas, replicas, scaleUpBehavior, scaleDownBehavior);
				updateDeployment(deployment, metadata, replicas, scaledReplicas, alarmName, alarmTriggerReason);
				alarmStateResetReason = String.format("Scaled Deployment '%s.%s' from %d to %d replicas", resoueceNamespace, deploymentName, replicas, scaledReplicas);
			}
		} else {
			alarmStateResetReason = String.format("Unable to retrieve K8sMetricAlarm custom resource '%s.%s'", resoueceNamespace, resourceName);
			logger.error(alarmStateResetReason);
		}
		
		//
		// After the scaling activity is completed/suspended, set the alarm status to OK
		//
		SetAlarmStateRequest setStateRequest = new SetAlarmStateRequest()
				.withAlarmName(alarmName)
				.withStateReason(alarmStateResetReason)
				.withStateValue(StateValue.OK);
		cloudWatchClient.setAlarmState(setStateRequest);
		logger.info(String.format("State of alarm '%s' set to %s", alarmName, StateValue.OK.toString()));
	}
	
	//
	// Update the Deployment with the new replica count
	// Add custom annotations to the metadata that indicate which alarm was triggered and when the scaling occurred.
	//
	private void updateDeployment (V1Deployment deployment, V1ObjectMeta metadata, int replicas, int scaledReplicas, String alarmName, String alarmTriggerReason) {
		String alarmTriggerTime = new DateTime().toString(dateTimeFormatter);
		metadata.getAnnotations().put(ANNOTATION_ALARM_NAME, alarmName);
		metadata.getAnnotations().put(ANNOTATION_ALARM_TRIGGER_TIME, alarmTriggerTime);
		
		deployment.metadata(metadata);
		deployment.getSpec().replicas(scaledReplicas);
		apiDeployment.update(deployment);
		logger.info(String.format("Scaled Deployment '%s.%s' from %d to %d replicas", metadata.getName(), metadata.getNamespace(), replicas, scaledReplicas));
	}
	
	//
	// Check if the Deployment is still cooling down after the last scaling event
	// Regardless of the direction of the proposed scaling event, it is suspended if the target resource is still cooling down.
	//
	private boolean isResourceCoolingDown (V1ObjectMeta metadata, 
			ComparisonOperator operator, 
			ScalingBehavior scaleUpBehavior, 
			ScalingBehavior scaleDownBehavior) {
		
		Map<String, String> annotations = metadata.getAnnotations();
		if (annotations.containsKey(ANNOTATION_ALARM_TRIGGER_TIME)) {
			ScalingBehavior behavior = null;
			if (Objects.equals(operator, ComparisonOperator.GreaterThanOrEqualToThreshold) ||
			    Objects.equals(operator, ComparisonOperator.GreaterThanThreshold)) {
				behavior = scaleUpBehavior;
			} else if (Objects.equals(operator, ComparisonOperator.LessThanOrEqualToThreshold) ||
					   Objects.equals(operator, ComparisonOperator.LessThanThreshold)) {
				behavior = scaleDownBehavior;
			}
			
		    DateTime currentTime = new DateTime();
			String dateString = annotations.get(ANNOTATION_ALARM_TRIGGER_TIME);
			DateTime lastScalingTime = dateTimeFormatter.parseDateTime(dateString);
			long duration = new Duration (lastScalingTime, currentTime).getStandardSeconds();
			long coolDownDuration = behavior.getCoolDown();
			if (duration < coolDownDuration) return true;
		}
		return false;
	}
	
	// Compute the number of replicas to be used in the scale up/down operation using the corresponding scaling behavior
	//
	private int computeScaling (ComparisonOperator operator, 
			int minReplicas, 
			int maxReplicas, 
			int replicas, 
			ScalingBehavior scaleUpBehavior, 
			ScalingBehavior scaleDownBehavior) {
		
		ScalingBehavior behavior = null;
		boolean scalingUp = true;
		
		if (Objects.equals(operator, ComparisonOperator.GreaterThanOrEqualToThreshold) ||
			Objects.equals(operator, ComparisonOperator.GreaterThanThreshold)) {
			behavior = scaleUpBehavior;
			scalingUp = true;
		}
		else if (Objects.equals(operator, ComparisonOperator.LessThanOrEqualToThreshold) ||
				 Objects.equals(operator, ComparisonOperator.LessThanThreshold)) {
			behavior = scaleDownBehavior;
			scalingUp = false;
		}
		
		List<ScalingPolicy> policies = behavior.getPolicies();
		int changeInReplicas = Integer.MIN_VALUE;
		for (ScalingPolicy policy : policies) {
			if (Objects.equals(policy.getType(), ScalingPolicy.ScalingType.Pods)) {
				int value = policy.getValue();
				if (value >= changeInReplicas) changeInReplicas = value;
			}
			else if (Objects.equals(policy.getType(), ScalingPolicy.ScalingType.Percent)) {
				int value = policy.getValue() * replicas / 100;
				if (value >= changeInReplicas) changeInReplicas = value;
			}
		}
		
		int scaledReplicas = -1;
		if (scalingUp) {
			scaledReplicas = replicas + changeInReplicas;
			if (scaledReplicas > maxReplicas) scaledReplicas = maxReplicas;
		}
		else {
			scaledReplicas = replicas - changeInReplicas;
			if (scaledReplicas < minReplicas) scaledReplicas = minReplicas;
		}
		logger.info(String.format("Number of replicas after scaling set to %d", scaledReplicas));
		return scaledReplicas;
	}
	
	
	public static void main(String[] args) throws IOException {
		JsonObject config = getConfiguration(args);
		CloudWatchAlarmHandler handler = new CloudWatchAlarmHandler();
		if (!handler.isInitialized) {
			handler.initialize();
			handler.isInitialized = true;
		}
		handler.processCloudWatchAlarmMessage(config);
	}
	
	private static JsonObject getConfiguration(String[] args) {
		if (args.length != 0) {
			String path = args[0];
			String cwd = Paths.get(".").toAbsolutePath().normalize().toString();
			try (Scanner scanner = new Scanner(new File(cwd, path)).useDelimiter("\\A")) {
				JsonObject config = new JsonObject(scanner.next());
				return config;
			}
			catch (Exception ex) {
				System.out.println(String.format("Exception while parsing the JSON input file %s", path));
				ex.printStackTrace();
			}
		}
		return null;
	}
}