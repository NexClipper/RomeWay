package co.kr.nexcloud.envoy.controlplane.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import co.kr.nexcloud.envoy.controlplane.discovery.ControlPlaneService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@RestController
@Api("Discovery Service 컨트롤러 클래스")
public class XdsController {
	@Autowired
	private ControlPlaneService service;
	
	@GetMapping("/configurations")
	@ApiOperation("전체 설정 정보를 조회한다.")
	public String getConfigurations() {
		return service.getConfigInfo();
	}
	
	@GetMapping("/configurations/{group}")
	@ApiOperation("그룹 설정 정보를 조회한다.")
	public String getConfiguration(
			@ApiParam(value="그룹명(node.cluster)") @PathVariable String group) {
		return service.getConfigInfo(group);
	}
	
	@PutMapping("/configurations")
	@ApiOperation("전체 설정 정보를 리로드한다.")
	public void reloadConfigurations() {
		service.reloadConfigurations();
	}
	
	@GetMapping("/clusters")
	@ApiOperation("전체 클러스터를 조회한다.")
	public String getClutster() {
		return "Not implemented yet.";
	}
	
	@GetMapping("/clusters/{clusterName}")
	@ApiOperation("클러스터 그룹을 조회한다.")
	public String getClutster(
			@ApiParam("value=클러스터명(node.metadata.service)") @PathVariable String clusterName) {
		return "Not implemented yet.";
	}
	
	
	@GetMapping("/clusters/{clusterName}/configuration")
	@ApiOperation("클러스터 그룹의 설정 정보를 조회한다.")
	public String getClusterConfig(
			@ApiParam(value="클러스터명(node.metadata.service)") @PathVariable String clusterName) {
		return "Not implemented yet.";
	}
}
