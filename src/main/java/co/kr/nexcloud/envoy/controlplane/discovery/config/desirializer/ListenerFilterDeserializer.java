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

import io.envoyproxy.envoy.config.listener.v3.Filter;
import io.envoyproxy.envoy.config.listener.v3.Filter.Builder;

public class ListenerFilterDeserializer extends ProtobufDeserializer<Filter, Filter.Builder> {
	private static final Logger LOG = LoggerFactory.getLogger(ListenerFilterDeserializer.class);
	
	private static final long serialVersionUID = 6084404401822067548L;
	
	public static final String HTTP_CONNECTION_MANAGER = "envoy.filters.network.http_connection_manager";
	
	private ObjectMapper mapper;
	
	public ListenerFilterDeserializer(ObjectMapper mapper) {
		super(Filter.class);
		
		this.mapper = mapper;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void populate(Builder builder, JsonParser parser, DeserializationContext context) throws IOException {
		LOG.debug("filter deserialize start.");
		LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
		
		if(JsonToken.START_OBJECT == parser.currentToken()) {
			parser.nextToken();
			
			while(JsonToken.END_OBJECT != parser.currentToken()) {
				LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
				
				switch(parser.currentName()) {
					case "name":
						builder.setName(parser.nextTextValue());
						break;
					case "typedConfig":
						parser.nextToken();
						parser.nextToken();
						
						Map<String, Object> filterMap = parser.readValueAs(Map.class);
						String type = (String)filterMap.get("@type");
						
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
						
						Object filter = mapper.convertValue(filterMap, clz);
						LOG.debug("reflection filter className : [{}], contents : [{}]", filter.getClass().getName(), ToStringBuilder.reflectionToString(filter));
						
						builder.setTypedConfig(Any.pack((Message)filter));
						
						LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
						break;
					default:
						throw new AssertionError("Not implemented parser for - " + parser.getText() + " yet.");
				}
				
				parser.nextToken();
			}
		}
		
		LOG.debug("filter deserialize complete.");
	}
}
