FROM openjdk:8
RUN /bin/mkdir /home/controller
ADD cloudwatchController.jar /home/controller
WORKDIR /home/controller
EXPOSE 8080
ENTRYPOINT ["/usr/local/openjdk-8/bin/java", "-Djdk.tls.client.protocols=TLSv1.2", "-jar", "cloudwatchController.jar"]



