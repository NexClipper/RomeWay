package co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer;

import java.io.IOException;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.hubspot.jackson.datatype.protobuf.ProtobufDeserializer;

import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap.Builder;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap.StaticResources;
import io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import io.envoyproxy.envoy.config.route.v3.Route;

public class BootstrapDeserializer extends ProtobufDeserializer<Bootstrap, Bootstrap.Builder> {
	private static final Logger LOG = LoggerFactory.getLogger(BootstrapDeserializer.class);
	
	private static final long serialVersionUID = -2611224945346080263L;
	
	public BootstrapDeserializer() {
		super(Bootstrap.class);
	}

	@Override
	protected void populate(Builder builder, JsonParser parser, DeserializationContext context) throws IOException {
		LOG.debug("bootstrap deserialize start.");
		LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
		
//		JsonToken nextToken = parser.nextToken();
		
		StaticResources.Builder staticResource = StaticResources.newBuilder();
		
		parser.nextToken();
		LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
		
		while(JsonToken.END_OBJECT != parser.currentToken()) {
			try {
				switch(parser.currentName()) {
					case "staticResources":
						// start_object : staticResources
						parser.nextToken();
						// field_name : listeners or clusters
						parser.nextToken();
						
						while(JsonToken.END_OBJECT != parser.currentToken()) {
							if("clusters".equals(parser.currentName())) {
								parser.nextToken();
								
								if(JsonToken.START_ARRAY == parser.currentToken()) {
									parser.nextToken();
									
									while(JsonToken.END_ARRAY != parser.currentToken()) {
										staticResource.addClusters(parser.readValueAs(Cluster.class));
										
										parser.nextToken();
									}
								}
							}
							else if("listeners".equals(parser.currentName())) {
								parser.nextToken();
								
								if(JsonToken.START_ARRAY == parser.currentToken()) {
									parser.nextToken();
									
									while(JsonToken.END_ARRAY != parser.currentToken()) {
										staticResource.addListeners(parser.readValueAs(Listener.class));
										
										parser.nextToken();
									}
								}
							}
							
							parser.nextToken();
							LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
						}
						
						builder.setStaticResources(staticResource);
						LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
						
						break;
					case "dynamicResource":
						break;
				default:
					while(JsonToken.END_OBJECT != parser.currentToken()) {
						parser.nextToken();
					}
	//				throw new AssertionError("Not implemented parser for - " + parser.currentName() + " yet.");
				}
			} catch(Exception e) {
				LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
				throw new RuntimeException(e);
			}
			
			parser.nextToken();
		}
		
		LOG.debug(ToStringBuilder.reflectionToString(builder));
		LOG.debug("bootstrap deserialize complete.");
	}
}