##!/bin/bash
CLUSTER_NAME=""
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text)
OIDC_PROVIDER=$(aws eks describe-cluster --name $CLUSTER_NAME --query "cluster.identity.oidc.issuer" --output text | sed -e "s/^https:\/\///")
SERVICE_ACCOUNT_NAMESPACE=kube-system
SERVICE_ACCOUNT_NAME=cloudwatchalarm-controller
SERVICE_ACCOUNT_IAM_ROLE=EKS-CloudWatch-Role
SERVICE_ACCOUNT_IAM_ROLE_DESCRIPTION="IAM role to be used by a K8s service account with full CloudWatch access"
SERVICE_ACCOUNT_IAM_POLICY=arn:aws:iam::aws:policy/CloudWatchFullAccess

#
# Setup a trust policy designed for a specific combination of K8s service account and namespace to sign in from a Kubernetes cluster which hosts the OIDC Idp.
#
read -r -d '' TRUST_RELATIONSHIP <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::${AWS_ACCOUNT_ID}:oidc-provider/${OIDC_PROVIDER}"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "${OIDC_PROVIDER}:sub": "system:serviceaccount:${SERVICE_ACCOUNT_NAMESPACE}:${SERVICE_ACCOUNT_NAME}"
        }
      }
    }
  ]
}
EOF
echo "${TRUST_RELATIONSHIP}" > TrustPolicy.json

#
# Create the IAM Role with the above trust policy
#
SERVICE_ACCOUNT_IAM_ROLE_ARN=$(aws iam get-role --role-name $SERVICE_ACCOUNT_IAM_ROLE --query 'Role.Arn' --output text)
if [ "$SERVICE_ACCOUNT_IAM_ROLE_ARN" = "" ]; 
then
  #
  # Create the IAM role for service account
  #
  SERVICE_ACCOUNT_IAM_ROLE_ARN=$(aws iam create-role \
  --role-name $SERVICE_ACCOUNT_IAM_ROLE \
  --assume-role-policy-document file://TrustPolicy.json \
  --description "$SERVICE_ACCOUNT_IAM_ROLE_DESCRIPTION" \
  --query "Role.Arn" --output text)

  #
  # Attach the required IAM policies to the IAM role create above
  #
  aws iam attach-role-policy \
  --role-name $SERVICE_ACCOUNT_IAM_ROLE \
  --policy-arn $SERVICE_ACCOUNT_IAM_POLICY  
else
    echo "$SERVICE_ACCOUNT_IAM_ROLE_ARN IAM role already exists"
fi
echo $SERVICE_ACCOUNT_IAM_ROLE_ARN

#
# Create the K8s service account 
# Annotate the service account with the above IAM role
#
kubectl create namespace $SERVICE_ACCOUNT_NAMESPACE
kubectl create sa $SERVICE_ACCOUNT_NAME -n $SERVICE_ACCOUNT_NAMESPACE
kubectl annotate sa $SERVICE_ACCOUNT_NAME eks.amazonaws.com/role-arn=$SERVICE_ACCOUNT_IAM_ROLE_ARN -n $SERVICE_ACCOUNT_NAMESPACE

#
# EKS cluster hosts an OIDC provider with a public discovery endpoint.
# Associate this Idp with AWS IAM so that the latter can validate and accept the OIDC tokens issued by Kubernetes to service accounts.
# Doing this with eksctl is the easier and best approach.
#
eksctl utils associate-iam-oidc-provider --cluster $CLUSTER_NAME --approve

