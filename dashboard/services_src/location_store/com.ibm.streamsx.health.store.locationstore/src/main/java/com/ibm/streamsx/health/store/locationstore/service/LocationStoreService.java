package com.ibm.streamsx.health.store.locationstore.service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.ibm.streamsx.health.ingest.types.connector.JsonToModelConverter;
import com.ibm.streamsx.topology.TStream;
import com.ibm.streamsx.topology.Topology;
import com.ibm.streamsx.topology.context.ContextProperties;
import com.ibm.streamsx.topology.context.StreamsContext.Type;
import com.ibm.streamsx.topology.context.StreamsContextFactory;
import com.ibm.streamsx.topology.function.Supplier;
import com.ibm.streamsx.topology.json.JSONSchemas;
import com.ibm.streamsx.topology.spl.SPLStreams;
import com.lambdaworks.redis.RedisClient;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.ssl.SslProvider;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.Timer;
import rx.Scheduler;

public class LocationStoreService {

	private Topology topo;
	private Supplier<String> topicSupplier;
	private Supplier<String> appConfigNameSupplier;
	private Supplier<Long> expireSupplier;
	
	public LocationStoreService() throws IOException {
		topo = new Topology("LocationStoreService");
		topo.addClassDependency(RedisClient.class);
		topo.addClassDependency(ChannelGroup.class);
		topo.addClassDependency(Timer.class);
		topo.addClassDependency(PooledByteBufAllocator.class);
		topo.addClassDependency(SslProvider.class);
		topo.addClassDependency(Scheduler.class);
		topo.addClassDependency(AddressResolverGroup.class);
		topo.addClassDependency(MessageToByteEncoder.class);
		topo.addClassDependency(JsonObject.class);
		topo.addClassDependency(Location.class);
		
		topicSupplier = topo.createSubmissionParameter("topic", String.class);
		appConfigNameSupplier = topo.createSubmissionParameter("app.config.name", String.class);
		expireSupplier = topo.createSubmissionParameter("expiry.seconds", 60l);
	}
	
	public Topology getTopology() {
		return topo;
	}
	
	public void build() {
		TStream<Location> obsStream = SPLStreams.subscribe(topo, topicSupplier, JSONSchemas.JSON)
			.transform(new JsonToModelConverter<Location>(Location.class));
		obsStream.sink(new RedisConsumer(appConfigNameSupplier, expireSupplier));
	}

    public void run(Type contextType, Map<String, Object> submissionParams) throws Exception {
        build();
    
        Map<String, Object> configParams = new HashMap<String, Object>();
        configParams.put(ContextProperties.SUBMISSION_PARAMS, submissionParams);
    
        StreamsContextFactory.getStreamsContext(contextType).submit(topo, configParams).get();
    }   

	public static void main(String[] args) throws Exception {
		LocationStoreService svc = new LocationStoreService();
		svc.run(Type.BUNDLE, Collections.emptyMap());
	}

}