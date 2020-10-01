## Autoscaling Kubernetes deployments with AWS Lambda

A Lambda function that is triggered to execute when CloudWatch alarm sends notifications to the SNS topic. It performs the role of a Kubernetes client and executes autoscaling operations by invoking the Kubernetes API server. It authenticates with the API server using a token generated with the <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-authenticating-requests.html">AWS Signature Version 4</a>  algorithm, adopting the same scheme used by the <a href="https://github.com/kubernetes-sigs/aws-iam-authenticator">AWS IAM Authenticator for Kubernetes</a> to construct the authentication token. 

## Build Requirements

<ul>
  <li><a href="https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html">Java SE Development Kit 8</a></li>
  <li><a href="https://maven.apache.org/download.cgi">Apache Maven 3.6.2</a></li>
  <li><a href="https://www.docker.com/products/container-runtime">Docker 19.03</a></li>
</ul>

## Build and Installation Instructions

To build the JAR file, type <b>mvn clean</b> followed by <b>mvn package</b> at the command line. Upload the JAR file to an S3 bucket.

Update the JSON file <b>functionDefinition.json</b> specifying appropriate values for the following fields:

<ul>
  <li><b>Role</b>: Execution role for the Lambda function</li>
  <li><b>Code.S3Bucket</b>: S3 bucket where the JAR file from the above build has been uploaded.
  <li><b>Environment.Variables.REGION</b>: AWS Region where your cluster resides</li>
  <li><b>Environment.Variables.STS_ENDPOINT</b>: AWS Region specific endpoint for AWS Security Token Service</li>
  <li><b>Environment.Variables.ASSUMED_ROLE</b>: An IAM role mapped to a Kubernetes group <i>lambda-client</i> in the <i>mapRoles</i> section of the <i>aws-auth</i> ConfigMap in the Amazon EKS cluster. The Lambda function generates the EKS authentication token using the temporary credentials granted to this IAM role. This IAM role does not need to be attached to any IAM permission policies. A Role and RoleBinding definition in the Amazon EKS cluster that grants the *lambda-client* Kubernetes group permission to list K8sMetricAlarm custom resources as well as list/update Deployment resources.</li>
  <li><b>Environment.Variables.ACCESS_KEY_ID</b></li>
  <li><b>Environment.Variables.SECRET_ACCESS_KEY</b>: Credentials of IAM user that has permissions to assume the above IAM role</li>
  <li><b>Environment.Variables.CLUSTER_NAME</b>: Amazon EKS cluster name</li>
  <li><b>Environment.Variables.API_SERVER</b>: API server endpoint URL for the Amazon EKS cluster</li>
  <li><b>Environment.Variables.CERT_AUTHORITY</b>: Certificate authority data for the Amazon EKS cluster</li>
</ul>
  
Deploy the Lambda function with the following command:
<b>aws lambda create-function --cli-input-json file://functionDefinition.json</b>

## Setup EventBridge Rule to Trigger Lambda Function

Run the following set of commands to create an EventBridge rule that matches IAM event notifications and add the Lambda function as its target.

<code>
  
EVENT_RULE_ARN=$(aws events put-rule --name IAMUserGroupRule --event-pattern "{\"source\":[\"aws.iam\"]}" --query RuleArn --output text)

aws lambda add-permission \
--function-name K8sClientForIAMEvents \
--statement-id 'd6f44629-efc0-4f38-96db-d75ba7d06579' \
--action 'lambda:InvokeFunction' \
--principal events.amazonaws.com \
--source-arn $EVENT_RULE_ARN

aws events put-targets --rule IAMUserGroupRule --targets file://lambdaTarget.json

</code>
