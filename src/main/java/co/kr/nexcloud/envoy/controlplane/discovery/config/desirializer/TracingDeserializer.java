package co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.hubspot.jackson.datatype.protobuf.ProtobufDeserializer;

import io.envoyproxy.envoy.config.trace.v3.Tracing.Http;
import io.envoyproxy.envoy.config.trace.v3.Tracing.Http.Builder;

public class TracingDeserializer extends ProtobufDeserializer<Http, Http.Builder> {
	private static final long serialVersionUID = -5131516733435414187L;

	private static final Logger LOG = LoggerFactory.getLogger(TracingDeserializer.class);
	
	public static final String PROVIDER = "envoy.tracers.zipkin";
	
	private ObjectMapper mapper;
	
	public TracingDeserializer(ObjectMapper mapper) {
		super(Http.class);
		
		this.mapper = mapper;
	}

	@Override
	protected void populate(Builder builder, JsonParser parser, DeserializationContext context) throws IOException {
		LOG.debug("tracing deserialize start.");
		LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
		
		// provider
		if(JsonToken.START_OBJECT == parser.currentToken()) {
			parser.nextToken();
			
			while(JsonToken.END_OBJECT != parser.currentToken()) {
				LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
				
				switch(parser.currentName()) {
					case "name" :
						builder.setName(parser.nextTextValue());
						break;
					case "typedConfig" :
						parser.nextToken();
						parser.nextToken();
						
						Map<String, Object> configMap = parser.readValueAs(Map.class);
						String type = (String)configMap.get("@type");
						
						if(type == null) {
							throw new AssertionError("Not implemented parser for not contained @type filters yet.");
						}
						
						String className = "io.envoyproxy."+type.substring(type.indexOf("/")+1);
						Class<?> clz = null;
						
						LOG.debug("@type implements class name : [{}]", className);
						
						try {
							clz = Class.forName(className);
						} catch (ClassNotFoundException e) {
							throw new AssertionError("Not exist class for @type filter - " + className);
						}
						
						Object config = mapper.convertValue(configMap, clz);
						LOG.debug("reflection config className : [{}], contents : [{}]", config.getClass().getName(), ToStringBuilder.reflectionToString(config));
						
						builder.setTypedConfig(Any.pack((Message)config));
						
						LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
						break;
				}
				
				parser.nextToken();
			}
		}
		
		LOG.debug("builder : [{}]", builder);
		LOG.debug("tracing deserialize complete.");
		
	}
}
