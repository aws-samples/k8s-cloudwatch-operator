package com.amazonwebservices.blogs.containers.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class PingHandler implements Handler<RoutingContext> {

	@Override
	public void handle(RoutingContext context) {
		context.response().setStatusCode(200);
		context.response().end("Hello from K8sMetricAlarmController!");		
	}
}
