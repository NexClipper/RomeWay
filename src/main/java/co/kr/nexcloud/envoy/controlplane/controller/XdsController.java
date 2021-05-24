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
}
