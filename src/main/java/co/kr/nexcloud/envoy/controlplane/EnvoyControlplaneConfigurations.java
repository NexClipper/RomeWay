package co.kr.nexcloud.envoy.controlplane;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import co.kr.nexcloud.envoy.controlplane.discovery.config.EnvoyJacksonModule;
import co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer.BootstrapDeserializer;
import co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer.ListenerDeserializer;
import co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer.ListenerFilterDeserializer;
import co.kr.nexcloud.envoy.controlplane.discovery.config.desirializer.TracingDeserializer;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap;
import io.envoyproxy.envoy.config.listener.v3.Filter;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import io.envoyproxy.envoy.config.trace.v3.Tracing.Http;
import io.swagger.annotations.Api;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
@ComponentScan(basePackages = {"co.kr.nexcloud"})
public class EnvoyControlplaneConfigurations {
	@Bean(value = "objectMapper")
	public ObjectMapper getObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
		mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
		
		EnvoyJacksonModule module = new EnvoyJacksonModule();
		module.getDeserializers().addDeserializer(Bootstrap.class, new BootstrapDeserializer().buildAtEnd());
		module.getDeserializers().addDeserializer(Listener.class, new ListenerDeserializer().buildAtEnd());
		module.getDeserializers().addDeserializer(Filter.class, new ListenerFilterDeserializer(mapper).buildAtEnd());
		module.getDeserializers().addDeserializer(Http.class, new TracingDeserializer(mapper).buildAtEnd());
		
		mapper.registerModule(module);
		
		return mapper;
	}
	
	@Bean
	public Docket docket() {
		String version ="v1";
		
		return new Docket(DocumentationType.SWAGGER_2)
				.groupName("Envoy control-plane API")
				.useDefaultResponseMessages(true)
				.groupName(version)
				.apiInfo(apiInfo(version))
				.select().apis(RequestHandlerSelectors.withClassAnnotation(Api.class)).paths(PathSelectors.any())
				.build();
	}
	
	private ApiInfo apiInfo(String version) {
		return new ApiInfoBuilder().title("Envoy control-plane API")
				.description("NexCloud Envoy control-plane API documentation")
				.version(version)
				.build();
	}
	
	@Bean
	public ExecutorService threadPool() {
		return Executors.newCachedThreadPool();
	}
	
	@Bean
	public RestTemplate restTemplate() {
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setConnectTimeout(3000);
		factory.setReadTimeout(3000);
		factory.setHttpClient(
				HttpClientBuilder.create()
					.setMaxConnTotal(50)
					.setMaxConnPerRoute(50)
					.build()
		);
		
		return new RestTemplate(factory);
	}
}
