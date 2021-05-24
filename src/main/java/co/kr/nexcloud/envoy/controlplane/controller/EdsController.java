package co.kr.nexcloud.envoy.controlplane.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.kr.nexcloud.envoy.controlplane.controller.data.Endpoint;
import co.kr.nexcloud.envoy.controlplane.discovery.ControlPlaneService;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.config.endpoint.v3.ClusterLoadAssignment;
import io.envoyproxy.envoy.config.endpoint.v3.LbEndpoint;
import io.envoyproxy.envoy.config.endpoint.v3.LocalityLbEndpoints;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@RestController
@RequestMapping("/eds")
@Api("Endpoint Discovery Service 컨트롤러 클래스")
public class EdsController {
	@Autowired
	private ControlPlaneService service;
	
	@PostMapping("/registration")
	@ApiOperation("endpoint를 등록한다.")
	public void registerEndpoint(
			@ApiParam(value="upstream 서비스가 포함되는 cluster 이름", required = true) @RequestParam String cluster,
			@ApiParam(value="upstream 서비스가 호출될 호스트명 or 도메인명 or ip", required=true) @RequestParam String address,
			@ApiParam(value="upstream 서비스가 호출될 포트", required=true) @RequestParam int port,
			@ApiParam(value="upstream 서비스 alive 상태 확인 여부", required=true) @RequestParam boolean checkAlive,
			@ApiParam(value="upstream 서비스 타입(http / grpc)", required=true) @RequestParam String type) {
		service.addEndpoint(cluster, address, port, checkAlive, type);
	}
	
	@DeleteMapping("/registration")
	@ApiOperation("endpoint를 삭제한다.")
	public void deleteEndpoint(
			@ApiParam(value="upstream 서비스가 포함되는 cluster 이름", required = true) @RequestParam String cluster,
			@ApiParam(value="upstream 서비스가 호출될 호스트명 or 도메인명 or ip", required=true) @RequestParam String address, 
			@ApiParam(value="upstream 서비스가 호출될 포트", required=true) @RequestParam int port) {
		service.deleteEndpoint(cluster, address, port);
	}
	
	@GetMapping("/endpoints")
	@ApiOperation("endpoint 목록을 반환한다.")
	public List<Endpoint> getEndpoints() {
		List<Endpoint> list = new ArrayList<>();
		
		for(ClusterLoadAssignment cla : service.getEndpoints()) {
			String cluster = cla.getClusterName();
			
			for(LocalityLbEndpoints le : cla.getEndpointsList()) {
				for(LbEndpoint e : le.getLbEndpointsList()) {
					SocketAddress address = e.getEndpoint().getAddress().getSocketAddress();
				
					Endpoint endpoint = new Endpoint();
					endpoint.setCluster(cluster);
					endpoint.setAddress(address.getAddress());
					endpoint.setPort(address.getPortValue());
					
					list.add(endpoint);
				}
			}
		}
		
		return list;
	}
}
