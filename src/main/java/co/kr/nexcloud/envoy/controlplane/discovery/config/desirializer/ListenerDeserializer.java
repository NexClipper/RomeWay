package co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.hubspot.jackson.datatype.protobuf.ProtobufDeserializer;

import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.config.core.v3.TrafficDirection;
import io.envoyproxy.envoy.config.listener.v3.Filter;
import io.envoyproxy.envoy.config.listener.v3.FilterChain;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import io.envoyproxy.envoy.config.listener.v3.Listener.Builder;

public class ListenerDeserializer extends ProtobufDeserializer<Listener, Listener.Builder> {
	private static final Logger LOG = LoggerFactory.getLogger(ListenerDeserializer.class);
	
	private static final long serialVersionUID = -2611224945346080263L;
	
	public ListenerDeserializer() {
		super(Listener.class);
	}

	@Override
	protected void populate(Builder builder, JsonParser parser, DeserializationContext context) throws IOException {
		LOG.debug("listener deserialize start.");
		LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
		
		SocketAddress.Builder address = SocketAddress.newBuilder();
		FilterChain.Builder filterChain = FilterChain.newBuilder();
		
		
		if(JsonToken.START_OBJECT == parser.currentToken()) {
			parser.nextToken();
			
			while(JsonToken.END_OBJECT != parser.currentToken()) {
				LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
				
				switch(parser.currentName()) {
				case "name":
					parser.nextToken();
					LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
					builder.setName(parser.getText());

					break;
					
				case "address":
					// start_object : address
					parser.nextToken();
					// filed_name : socketAddress
					parser.nextToken();
					// start_object : socketAddress
					parser.nextToken();
					// field_name : address or portValue
					parser.nextToken();
					
					if("address".equals(parser.currentName())) {
						address.setAddress(parser.nextTextValue()); 
					}
					else if("portValue".equals(parser.currentName())) {
						address.setPortValue(parser.nextIntValue(0));
					}
					
					parser.nextToken();
					LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
					if("address".equals(parser.currentName())) {
						address.setAddress(parser.nextTextValue());
					}
					else if("portValue".equals(parser.currentName())) {
						address.setPortValue(parser.nextIntValue(0));
					}
					
					builder.setAddress(Address.newBuilder().setSocketAddress(address));
					LOG.debug("address : [{}]", builder);
					
					// end_object : socketAddress
					parser.nextToken();
					// end_object : socket
					parser.nextToken();
					
					break;
					
				case "filterChains":
					// start_array : filterChains
					parser.nextToken();
					// start_object : null
					parser.nextToken();
					// field_name : filters
					parser.nextToken();
					// start_array : filters
					parser.nextToken();
					// start_object : null
					parser.nextToken();
					
					filterChain = FilterChain.newBuilder();
					
					while(JsonToken.END_ARRAY != parser.currentToken()) {
						Filter filter = parser.readValueAs(Filter.class);
						LOG.debug("filterchain : [{}]", filter);
						
						filterChain.addFilters(filter);
						
						parser.nextToken();
						LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
					}
					
					builder.addFilterChains(filterChain);
					
					break;
				
				case "trafficDirection":
					LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
					parser.nextToken();
					LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
					builder.setTrafficDirection(TrafficDirection.valueOf(parser.getText()));
					
					break;
					
				default:
					LOG.debug("currentToken : [{}], currentName : [{}], currentText : [{}]", parser.currentToken(), parser.currentName(), parser.getText());
					throw new AssertionError("Not implemented parser for - " + parser.currentName() + " yet.");
				}

				parser.nextToken();
			}
			
			parser.nextToken();
		}
		
		parser.nextToken();
		
		LOG.debug("listener builder : [{}]", builder);
		LOG.debug("listener deserialize complete.");
	}
}