package co.kr.nexcloud.envoy.controlplane;

import java.util.TimeZone;

import javax.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.kr.nexcloud.envoy.controlplane.discovery.config.EnvoyJacksonModule;
import co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer.BootstrapDeserializer;
import co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer.ListenerDeserializer;
import co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer.ListenerFilterDeserializer;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap;
import io.envoyproxy.envoy.config.listener.v3.Filter;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableAutoConfiguration
public class EnvoyControlplaneApplication {
	public static void main(String[] args) {
		SpringApplication.run(EnvoyControlplaneApplication.class, args);
	}
	
	@PostConstruct
	public void setUp() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}
}
