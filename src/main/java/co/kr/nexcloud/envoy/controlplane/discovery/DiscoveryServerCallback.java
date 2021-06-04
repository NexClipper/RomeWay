package co.kr.nexcloud.envoy.controlplane.discovery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.protobuf.Value;

import io.envoyproxy.controlplane.server.DiscoveryServerCallbacks;
import io.envoyproxy.envoy.config.core.v3.Node;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;

@Component
public class DiscoveryServerCallback implements DiscoveryServerCallbacks {
	private static final Logger LOG = LoggerFactory.getLogger(DiscoveryServerCallback.class);
	
	private Map<Long, StreamNode> streamNodeMap = new ConcurrentHashMap<>();
	
	@Autowired
	private ControlPlaneService cpService;

	@Override
	public void onStreamOpen(long streamId, String typeUrl) {
		LOG.debug("streamId : [{}] - typeUrl : [{}]", streamId, typeUrl);
	}
	
	@Override
	public void onStreamClose(long streamId, String typeUrl) {
		LOG.debug("streamId : [{}] - typeUrl : [{}]", streamId, typeUrl);
		
		deleteEndpoint(streamId);
	}
	
	@Override
	public void onStreamCloseWithError(long streamId, String typeUrl, Throwable error) {
		LOG.debug("streamId : [{}] - typeUrl : [{}]", streamId, typeUrl);
		
		deleteEndpoint(streamId);
	}
	
	/**
	 * streamId에 해당하는 endpoint 정보를 삭제한다.
	 * 
	 * @param streamId
	 */
	private void deleteEndpoint(long streamId) {
		if(streamNodeMap.containsKey(streamId)) {
			synchronized(streamNodeMap) {
				StreamNode sNode = streamNodeMap.get(streamId);
				
				String service = sNode.getService();
				String address = sNode.getAddress();
				int port = sNode.getPort();
				
				if(!StringUtils.isEmpty(service) && !StringUtils.isEmpty(address) && port != 0) {
					cpService.deleteEndpoint(service, address, port);
				}
				
				streamNodeMap.remove(streamId);
			}
		}
	}

	@Override
	public void onV2StreamRequest(long streamId, io.envoyproxy.envoy.api.v2.DiscoveryRequest request) {
		throw new IllegalStateException("Unexpected v2 request in v3 test");
	}

	@Override
	public void onStreamResponse(
			long streamId, 
			io.envoyproxy.envoy.api.v2.DiscoveryRequest request, 
			io.envoyproxy.envoy.api.v2.DiscoveryResponse response) {
		throw new IllegalStateException("Unexpected v2 response in v3 test");
	}

	@Override
	public void onV3StreamResponse(long streamId, DiscoveryRequest request, DiscoveryResponse response) {
		LOG.debug("streamId : [{}]{}response : [{}]", streamId, System.lineSeparator(), ToStringBuilder.reflectionToString(response));
	}

	@Override
	public void onV3StreamRequest(long streamId, io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest request) {
		if(LOG.isDebugEnabled()) {
			Node node = request.getNode();
			
			LOG.debug("streamId : [{}]{}node : [id : {}, cluster: {}, meta: {}]{}type : [{}]{}error : [{}]", 
				streamId, 
				System.lineSeparator(), 
				node.getId(), node.getCluster(), node.getMetadata(),
				System.lineSeparator(), 
				request.getTypeUrl(), 
				System.lineSeparator(), 
				request.getErrorDetail());
		}
		
		if(request.getErrorDetail() == null || request.getErrorDetail().getCode() == 0) { 
			// 신규 streamId인 경우 endpoint 정보를 추가한다.
			if(!streamNodeMap.containsKey(streamId)) {
				synchronized(streamNodeMap) {
					Node node = request.getNode();
					Map<String,Value> meta = node.getMetadata().getFieldsMap();
					String service = null;
					String address = null;
					int port = 0;
					boolean checkAlive = false;
					String type = null;
					
					if(meta.containsKey("service")) {
						service = meta.get("service").getStringValue();
					}
					
					if(meta.containsKey("address")) {
						address = meta.get("address").getStringValue();
					}
					
					if(meta.containsKey("port")) {
						port = (int)meta.get("port").getNumberValue();
					}
					
					if(meta.containsKey("check_alive")) {
						checkAlive = meta.get("check_alive").getBoolValue();
					}
					
					if(meta.containsKey("type")) {
						type = meta.get("type").getStringValue();
					}
					
					StreamNode sNode = new StreamNode();
					sNode.setStreamId(streamId);
					sNode.setCluster(node.getCluster());
					sNode.setNodeId(node.getId());
					
					if(!StringUtils.isEmpty(service) && !StringUtils.isEmpty(address) && port != 0) {
						sNode.setService(service);
						sNode.setAddress(address);
						sNode.setPort(port);
						
						cpService.addEndpoint(service, address, port, checkAlive, type);
					}
					
					streamNodeMap.putIfAbsent(streamId, sNode);
				}
			}
		}
	}
}
