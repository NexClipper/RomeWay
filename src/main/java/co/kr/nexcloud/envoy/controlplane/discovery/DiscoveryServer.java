package co.kr.nexcloud.envoy.controlplane.discovery;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.envoyproxy.controlplane.server.V3DiscoveryServer;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap.StaticResources;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.ChannelOption;

/**
 * 
 * @author mrchopa
 * @implNote TODO_1: 현재는 upstream endpoint만 등록하는 EDS로만 동작하므로 configuration이 필요치 않아 향후 xDS 기능 추가 구현 시 configuration을 추가 구현한다.
 */
@Component
public class DiscoveryServer {
	private static final Logger LOG = LoggerFactory.getLogger(DiscoveryServer.class);
	
	@Value("${nc.discovery.server.port:18000}")
	private int discoveryServerPort;
	
	@Value("${nc.discovery.server.grpc.channel-timeout:180000}")
	private int grpcChannelTimeout;

	@Autowired
	private ConfigurationLoader configLoader;
	
	private SimpleCacheWrapper<String> cache = null;
	
	private Server grpcServer = null;
	
	@Autowired
	private DiscoveryServerCallback callback;
	
	/**
	 * Discovery server를 시작한다.
	 */
	@PostConstruct
	private void init() {
		try {
			// envoy node의 cluster명을 key로 하는 snapshot을 분류하는 cache를 생성한다.
			cache = configLoader.getCache();
			
			// V3 xDS API server 생성
			final V3DiscoveryServer discoveryServer = new V3DiscoveryServer(callback, cache);
			
			Thread grpcServerThread = new Thread(() -> {
				// Netty를 이용하는 GRPC 서버 생성 및 기동
				grpcServer = NettyServerBuilder.forPort(discoveryServerPort)
						.addService(discoveryServer.getAggregatedDiscoveryServiceImpl())
						.addService(discoveryServer.getClusterDiscoveryServiceImpl())
						.addService(discoveryServer.getEndpointDiscoveryServiceImpl())
						.addService(discoveryServer.getListenerDiscoveryServiceImpl())
						.addService(discoveryServer.getRouteDiscoveryServiceImpl())
						.withChildOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, grpcChannelTimeout)
// interceptor 구현 예제
//						.intercept(new ServerInterceptor() {
//							@Override
//							public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
//								      ServerCall<ReqT, RespT> call,
//								      Metadata headers,
//								      ServerCallHandler<ReqT, RespT> next) {
//								LOG.debug("interceptor - call: [{}], header: [{}]", call.getAttributes(), headers);
//								return next.startCall(call, headers);
//							}
//						})
						.build();
				
				try {
					grpcServer.start();
					
					LOG.info("V3DiscoveryServer has started on port {} for GRPC", grpcServer.getPort());
					
					grpcServer.awaitTermination();
					
					LOG.info("GRPC Server terminated.");
				} catch(IOException e) {
					throw new InternalError(e.getMessage(), e);
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}, "GRPC Server Thread");
			
			grpcServerThread.start();
		} catch(Exception e) {
			throw new InternalError(e.getMessage(), e);
		}
	}
	
	protected SimpleCacheWrapper<String> getCache() {
		return cache;
	}
	
	/**
	 * bean finalize 시 GRPC server를 shutdown 시킨다.
	 */
	@PreDestroy
	private void fin() {
		LOG.info("Discovery Server destroyed.");
		
		if(grpcServer != null) {
			LOG.debug("GRPC Server shutting down...");
			grpcServer.shutdown();
		}
	}
	
	/**
	 * 로딩된 Bootstrap을 cache에 업데이트한다.
	 * 
	 * @param bootstrap
	 */
	private void configureCache(Bootstrap bootstrap) {
		StaticResources staticResource = bootstrap.getStaticResources();
		
		cache.updateCluster(staticResource.getClustersList());
	}
	
	
}
