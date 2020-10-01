#!/bin/bash
cd .. && mvn clean && mvn package && cd docker-build

JAR_SOURCE="../target/cloudwatchController.jar"
JAR_TARGET="cloudwatchController.jar"

/bin/rm -f $JAR_TARGET
/bin/cp $JAR_SOURCE $JAR_TARGET

NAME=k8s-cloudwatch-controller
TAG=latest

SOURCE_IMG=$NAME:$TAG
/usr/local/bin/docker build -t $SOURCE_IMG .

SHA256=$(/usr/local/bin/docker inspect --format='{{index .Id}}' $SOURCE_IMG)
IFS=':' read -ra TOKENS <<< "$SHA256"

TARGET_IMG=123456789012.dkr.ecr.us-east-1.amazonaws.com/$NAME:$TAG
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com
/usr/local/bin/docker tag $SOURCE_IMG $TARGET_IMG
/usr/local/bin/docker push $TARGET_IMG
