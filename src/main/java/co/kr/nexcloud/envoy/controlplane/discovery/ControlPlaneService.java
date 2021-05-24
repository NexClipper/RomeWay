package co.kr.nexcloud.envoy.controlplane.discovery;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.config.endpoint.v3.ClusterLoadAssignment;
import io.envoyproxy.envoy.config.endpoint.v3.Endpoint;
import io.envoyproxy.envoy.config.endpoint.v3.LbEndpoint;
import io.envoyproxy.envoy.config.endpoint.v3.LocalityLbEndpoints;

@Service
public class ControlPlaneService {
	private static final Logger LOG = LoggerFactory.getLogger(ControlPlaneService.class);
	
	private final Map<String, Set<LbEndpoint>> clusterEndpointMap = new ConcurrentHashMap<>();
	private final Map<String, ClusterLoadAssignment> clusterLoadAssignmentMap = new ConcurrentHashMap<>();
	private final Set<LbEndpoint> aliveCheckingSet = new HashSet<>();
	
	@Autowired
	private DiscoveryServer discoveryServer;
	
	@Autowired
	private ConfigurationLoader configLoader;
	
	@Autowired
	private ExecutorService executor;
	
	@Value("${nc.discovery.server.grpc.channel-timeout:180000}")
	private int grpcChannelTimeout;
	
	@Value("${nc.discovery.endpoint.healthcheck.interval:10}")
	private int interval;
	
	@Value("${nc.discovery.endpoint.healthcheck.max-count:50}")
	private int maxCount;
	
	@Autowired
	private RestTemplate restTemplate;
	
	public String getConfigInfo(String group) {
		return ToStringBuilder.reflectionToString(discoveryServer.getCache().getSnapshot(group));
	}
	
	public String getConfigInfo() {
		return ToStringBuilder.reflectionToString(discoveryServer.getCache());
	}
	
	/**
	 * 전체 config 파일을 리로드한다.(초기화)
	 */
	public void reloadConfigurations() {
		configLoader.loadConfigAllInPath();
	}
	
	/**
	 * 전체 endpoint를 반환한다.
	 * 
	 * @return
	 */
	public Iterable<ClusterLoadAssignment> getEndpoints() {
		return clusterLoadAssignmentMap.values().stream().collect(Collectors.toList());
	}
	
	public Iterable<ClusterLoadAssignment> getClustersEndpoints(List<Cluster> clusterList) {
		final Set<String> clusterSet = new HashSet<>(clusterList.size());
		
		clusterList.forEach(c -> {
			clusterSet.add(c.getName());
		});
		
		return clusterLoadAssignmentMap.values().stream().filter(cla -> clusterSet.contains(cla.getClusterName())).collect(Collectors.toList());
	}
	
	/**
	 * endpoint를 추가한다.
	 * <pre>
	 * 기존에 존재하는 endpoint의 경우 추가하지 않는다.
	 * </pre>
	 * 
	 * @param clusterName
	 * @param address
	 * @param port
	 * @param checkAlive 엔드포인트 추가 전 alive 상태 확인 여부
	 * @param type endpoint type (http / grpc) TODO: 구현 필요
	 */
	public void addEndpoint(final String clusterName, final String address, final int port, boolean checkAlive, String type) {
		LOG.debug("clusterName : [{}], address : [{}], port : [{}]", clusterName, address, port);
		
		final LbEndpoint endpoint = createLbEndpoint(address, port);
		
		if(checkAlive) {
			aliveCheckingSet.add(endpoint);
			
			// 상태 체크 쓰레드 실행
			executor.execute(() -> {
				int statusCode = 0;
				
				try {
					for(int i=0; i<maxCount; i++) {
						// aliveCheckSet에 endpoint가 존재하지 않으면(다른 곳에서 삭제됨) 처리 중지 -> endpoint 추가되지 않음
						if(!aliveCheckingSet.contains(endpoint)) break;
						
						statusCode = 0;
						ResponseEntity<Void> response = null;
						
						try{
							response = restTemplate.exchange("http://"+address+":"+port, HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), Void.class);
							
							if(response != null) {
								statusCode = response.getStatusCodeValue();
							}
						} catch(HttpClientErrorException ex) {
							statusCode = ex.getRawStatusCode();
						} catch(Exception e) {
							LOG.warn(e.getMessage(), e);
						}
						
						LOG.debug("clusterName : [{}], address : [{}], port : [{}], statusCode : [{}]", clusterName, address, port, statusCode);
						
						// endpoint service가 open되지 않았으면 interval 후 재시도
						if(statusCode == 0 || statusCode == 502 || statusCode == 503 || statusCode == 504) {
							Thread.sleep(interval * 1000);
							continue;
						}
						
						// endpoint 추가
						addEndpoint(clusterName, endpoint);
						break;
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch(Exception e) {
					LOG.warn("{} - cluster : {}, address : {}, port : {}", e.getMessage(), clusterName, address, port, e);
				} finally {
					synchronized(aliveCheckingSet) {
						aliveCheckingSet.remove(endpoint);
					}
				}
			});
		}
		else {
			addEndpoint(clusterName, endpoint);
		}
	}
	
	private void addEndpoint(String clusterName, LbEndpoint endpoint) {
		synchronized(clusterEndpointMap) {
			if(!clusterEndpointMap.containsKey(clusterName)) {
				clusterEndpointMap.put(clusterName, new HashSet<LbEndpoint>());
			}
			
			Set<LbEndpoint> endpointSet = clusterEndpointMap.get(clusterName);
			SocketAddress sa = endpoint.getEndpoint().getAddress().getSocketAddress();
			
			// 기존에 존재하지 않는 endpoint의 경우 추가
			if(!endpointSet.contains(endpoint)) {
				endpointSet.add(endpoint);
				
				clusterLoadAssignmentMap.put(clusterName, createClusterLoadAssignment(clusterName, endpointSet));
				
				discoveryServer.getCache().updateClusterLoadAssignments(clusterLoadAssignmentMap.values().stream().collect(Collectors.toList()));
				
				LOG.info("Endpoint added - clusterName : [{}], address : [{}], port : [{}]", clusterName, sa.getAddress(), sa.getPortValue());
			}
			else {
				LOG.debug("Endpoint already exist - clusterName : [{}], address : [{}], port : [{}]", clusterName, sa.getAddress(), sa.getPortValue());
			}
		}
	}
	
	/**
	 * endpoint를 삭제한다.
	 * 
	 * @param clusterName
	 * @param address
	 * @param port
	 */
	public void deleteEndpoint(String clusterName, String address, int port) {
		LOG.debug("clusterName : [{}], address : [{}], port : [{}]", clusterName, address, port);
		
		LbEndpoint endpoint = createLbEndpoint(address, port);
		
		// alive check 상태의 endpoint가 존재하면 중지 처리
		aliveCheckingSet.remove(endpoint);
		
		synchronized(clusterEndpointMap) {
			if(clusterEndpointMap.containsKey(clusterName)) {
				Set<LbEndpoint> endpointSet = clusterEndpointMap.get(clusterName);
				
				if(endpointSet.remove(endpoint)) {
					ClusterLoadAssignment updatedCla = createClusterLoadAssignment(clusterName, endpointSet);
					clusterLoadAssignmentMap.put(clusterName, updatedCla);
					
//					discoveryServer.getCache().updateCluster(clusterName, updatedCla);
					discoveryServer.getCache().updateClusterLoadAssignments(clusterLoadAssignmentMap.values().stream().collect(Collectors.toList()));
					
					LOG.info("Endpoint removed - clusterName : [{}], address : [{}], port : [{}]", clusterName, address, port);
				}
				else {
					LOG.debug("Endpoint not exist - clusterName : [{}], address : [{}], port : [{}]", clusterName, address, port);
				}
			}
			else {
				LOG.debug("Cluster not exist - clusterName : [{}], address : [{}], port : [{}]", clusterName, address, port);
			}
		}
	}
	
	/**
	 * LbEndPoint를 생성한다.
	 * 
	 * @param address
	 * @param port
	 * @return
	 */
	private LbEndpoint createLbEndpoint(String address, int port) {
		return LbEndpoint.newBuilder()
				.setEndpoint(
					Endpoint.newBuilder()
					.setAddress(
						Address.newBuilder()
						.setSocketAddress(
							SocketAddress.newBuilder()
							.setAddress(address)
							.setPortValue(port)
							.setProtocol(SocketAddress.Protocol.TCP)
						)
					)
				).build();
	}
	
	/**
	 * 클러스터의 loadAssignment를 생성한다.
	 * 
	 * @param clusterName
	 * @param endpointSet
	 * @return
	 */
	private ClusterLoadAssignment createClusterLoadAssignment(String clusterName, Set<LbEndpoint> endpointSet) {
		return ClusterLoadAssignment.newBuilder()
				.setClusterName(clusterName)
				.addEndpoints(
					LocalityLbEndpoints.newBuilder().addAllLbEndpoints(endpointSet.stream().collect(Collectors.toList()))
				).build();
	}
	
//	private Cluster createCluster(String clusterName, long connectTimeout, Cluster.DiscoveryType clusterType, Custer.)
}
