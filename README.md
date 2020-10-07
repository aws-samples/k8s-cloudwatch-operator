## Autoscaling Kubernetes deployments based on custom Prometheus metrics using CloudWatch Container Insights

This Git repository contains software artifacts that enable autoscaling microservices deployed to an Amazon EKS cluster or a self-managed Kubernetes cluster on AWS, based on custom Prometheus metrics collected from the workloads. It has a custom Kubernetes controller to manage Amazon CloudWatch metric alarms that watch custom metrics data and trigger scaling actions. AWS Lambda is used to autoscale the microservices.

Please refer to this blog post for details about how this works.

## Architecture
The architecture used to implement this autoscaling solution comprises the following elements.

<ul>
<li>
A Kubernetes Operator implemented using <a href="https://github.com/kubernetes-client/java">Kubernetes Java SDK</a>. This operator packages a custom resource named <i>K8sMetricAlarm</i> defined by a CustomResourceDefinition, a custom controller implemented as a Deployment, which responds to events in the Kubernetes cluster pertaining to add/update/delete actions on the K8sMetricAlarm custom resource, and Role/RoleBinding resources to grant necessary permissions to the custom controller. The customer controller runs under the identity of a Kubernetes service account which is associated with an IAM role that has permissions to manages resources in CloudWatch.
</li>
<li>
<a href="https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/ContainerInsights-Prometheus-Setup.html">CloudWatch agent for Prometheus</a> metrics collection which is installed as a Deployment with a single replica in the Amazon EKS cluster.
</li>
<li>
Amazon CloudWatch metric alarms which are managed by the custom controller in conjunction with the K8sMetricAlarm custom resource.
</li>
<li>
Amazon SNS topic which is configured to receive notifications when a CloudWatch alarm breaches a specified threshold.
</li>
<li>
AWS Lambda function whose execution is triggered when a notification is sent to the Amazon SNS topic. The Lambda function acts as a Kubernetes client and performs the autoscaling operation on the target resource.
</li>
<li>
One or more microservices deployed to the cluster that are the target of autoscaling. These services have been instrumented with Prometheus client library to collect application-specific metrics and expose them over HTTP, to be read by the CloudWatch agent for Prometheus.
</li>
</ul>

<img class="wp-image-1960 size-full" src="images/Architecture.png" alt="Autoscaling architecture" width="854" height="527" />

## Build & Installation Instructions

First, build the Docker image for the custom controller per instructions <a href="https://github.com/aws-samples/k8s-cloudwatch-operator/blob/main/cloudwatch-controller">here</a>.

Next, build and deploy the Lambda Kubernetes client per the instructions <a href="https://github.com/aws-samples/k8s-cloudwatch-operator/blob/main/cloudwatch-lambda">here</a>.

Execute the shell script <b>createIRSA.sh</b> after defining the variable CLUSTER_NAME with the name of the Kubernetes cluster. The script executes the following tasks:
<ul>
<li>Create an IAM role named <b>EKS-CloudWatch-Role</b></li>
<li>Attach the AWS managed policy named <b>CloudWatchFullAccess</b> to this role</li>
<li>Create a Kubernetes service account <b>cloudwatchalarm-controller</b> in the <b>kube-system</b> namespace and associate it with the above IAM role using a Kubernetes annotation. The custom controller is configured to run under the identity of this service account</li>
</ul>

Then, deploy the operator to a Kubernetes cluster as follows:<br/>
<b>kubectl apply -f operator.yaml</b><br/>
The custom controller is deployed with an image from a public repository. You may want to replace it with the image URL from your repository.

Make the following changes to the YAML manifest <b>aws-auth-configmap.yaml</b>
<ul>
<li>Replace WORKER_NODE_ROLE_ARN with the ARN of the IAM role assigned to the worker nodes in the EKS cluster.</li>
<li>Replace LAMBDA_ROLE_ARN with the ARN of the IAM role that was used in the <b>Environment.Variables.ASSUMED_ROLE</b> configuration parameter for the Lambda function. This role is mapped to a Kubernetes group <i>lambda-client</i>.
</ul>

Update this ConfigMap as follows:<br/>
<b>kubectl apply -f aws-auth-configmap.yaml</b> 

We will have to grant the *lambda-client* Kubernetes group permission to list *K8sMetricAlarm* custom resources as well as list/update *Deployment* resources. In order to do that, create a Kubernetes ClusterRole and ClusterRoleBinding as follows:</br>
<b>kubectl apply -f rbac-lambda-client.yaml</b>

Sample definitions of the <i>K8sMetricAlarm</i> custom resource are provided in <b>http-rate-alarm.yaml</b> and <b>sqs-alarm.yaml</b>

## License

This library is licensed under the MIT-0 License. See the LICENSE file.

