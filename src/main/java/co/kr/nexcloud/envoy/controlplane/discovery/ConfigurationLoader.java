package co.kr.nexcloud.envoy.controlplane.discovery;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import io.envoyproxy.controlplane.cache.NodeGroup;
import io.envoyproxy.controlplane.cache.v3.Snapshot;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap.StaticResources;
import io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.envoyproxy.envoy.config.core.v3.Node;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import io.envoyproxy.envoy.extensions.transport_sockets.tls.v3.Secret;

@Component
public class ConfigurationLoader {
	private static final Logger LOG = LoggerFactory.getLogger(ConfigurationLoader.class);
	
	@Value("${nc.discovery.config.path:}")
	private String configPath;
	
	@Value("${nc.discovery.config.default-name:default}")
	private String defaultConfigName;
	
	private final AtomicBoolean runConfigObserver = new AtomicBoolean(false);
	
	private final AtomicLong lastConfigModified = new AtomicLong(0);
	
	private Bootstrap defaultConfig;
	
	private Map<String, Bootstrap> groupConfigMap = new ConcurrentHashMap<>();
	
	@Autowired
	private ObjectMapper mapper;
	
	@Autowired
	private ControlPlaneService service;
	
	private SimpleCacheWrapper<String> cache = ConfigurationLoader.makeCacheForGroupingWithNodeCluster();
	
	private boolean isReady = false;
	
	/** config load가 완료된 후 cache를 반환하기 위한 lock object */
	private Object lockObj = new Object();
	
	@PostConstruct
	private void init() {
		loadConfigAllInPath();
		isReady = true;
		
		// config path 변경 감지 & reload 쓰레드 시작
		runConfigurationWatcher();
	}
	
	public SimpleCacheWrapper<String> getCache() {
		if(!isReady) {
			// config load가 완료될 때까지 cache 반환을 지연한다.
			synchronized(lockObj) {
				try {
					lockObj.wait();
				} catch (InterruptedException e) {
					LOG.error(e.getMessage(), e);
					Thread.currentThread().interrupt();
				}
			}
		}
		
		return cache;
	}
	
	/**
	 * file에 해당하는 config를 reload 한다.
	 * 
	 * @param file
	 */
	public void reloadConfig(File file) {
		String groupName = getGroupName(file.getName());
		
		if(groupName != null) {
			Bootstrap changedConfig = loadConfig(file);
			
			// default config가 수정된 경우
			if(groupName.equals(defaultConfigName)) {
				defaultConfig = changedConfig;
				
				// cache의 snapshot을 reset 한다.
				resetCache();
				
				LOG.info("reload default config file : [{}]", file.getName());
			}
			// 특정 group config가 수정된 경우
			else {
				groupConfigMap.put(groupName, changedConfig);
				
				// groupName의 snapshot을 업데이트한다.
				updateSnapshot(groupName, changedConfig);
				
				LOG.info("reload config file : [{}]", file.getName());
			}
			
			lastConfigModified.set(System.currentTimeMillis());
		}
	}
	
	/**
	 * config path에 있는 전체 config file을 load 한다.
	 */
	public void loadConfigAllInPath() {
		synchronized(lockObj) {
			final File path = Paths.get(configPath).toFile();
			
			if(!path.exists() || !path.isDirectory()) {
				throw new RuntimeException(MessageFormat.format("configuration path not exist - path : [{0}], absolutePath : [{1}]", path, path.getAbsolutePath()));
			}
			
			LOG.info("configuration load from - [{}]", path.getName());
			
			groupConfigMap.clear();
	
			Arrays.stream(path.listFiles()).forEach(f -> {
				LOG.debug("load file : [{}]", f.getName());
				
				String groupName = getGroupName(f.getName());
				
				if(groupName != null) {
					Bootstrap changedConfig = loadConfig(f);
					
					if(groupName.equals(defaultConfigName)) {
						defaultConfig = changedConfig;
					}
					else {
						groupConfigMap.put(groupName, changedConfig);
					}
				}
			});
			
			resetCache();
			
			lastConfigModified.set(System.currentTimeMillis());
			
			lockObj.notifyAll();
		}
	}
	
	/**
	 * cache의 스냅샷을 reset 한다.
	 */
	private void resetCache() {
		if(defaultConfig != null) {
			updateDefaultSnapshot(defaultConfig);
		}
		
		groupConfigMap.keySet().forEach(group -> {
			updateSnapshot(group, groupConfigMap.get(group));
		});
		
		LOG.info("all snapshot updated by reset cache");
	}
	
	/**
	 * default 스냅샷을 업데이트한다.
	 * 
	 * @param defaultConfig
	 */
	private void updateDefaultSnapshot(Bootstrap defaultConfig) {
		StaticResources resources = defaultConfig.getStaticResources();
		
		cache.setSnapshot(
			defaultConfigName, 
			Snapshot.create(
				resources.getClustersList(), 
				ImmutableList.of(),
				resources.getListenersList(),
				ImmutableList.of(),
				resources.getSecretsList(),
				cache.makeNewVersion()
			)
		);
		
		LOG.info("[{}] (default) group snapshot updated.", defaultConfigName);
	}
	
	/**
	 * groupName의 스냅샷을 업데이트한다.
	 * 
	 * @param groupName 그룹명
	 * @param groupConfig 그룹 config
	 */
	private void updateSnapshot(String groupName, Bootstrap groupConfig) {
		StaticResources resources = groupConfig.getStaticResources();
		
		List<Cluster> clusterList = new ArrayList<Cluster>();
		List<Listener> listenerList = new ArrayList<Listener>();
		List<Secret> secretList = new ArrayList<Secret>();
		
		// default config 가 존재하는 경우 default config를 추가한다.
		if(defaultConfig != null) {
			StaticResources defaultResources = defaultConfig.getStaticResources();
			
			clusterList.addAll(defaultResources.getClustersList());
			listenerList.addAll(defaultResources.getListenersList());
			secretList.addAll(defaultResources.getSecretsList());
		}
		
		// default config에 group의 config를 추가한다.
		clusterList.addAll(resources.getClustersList());
		listenerList.addAll(resources.getListenersList());
		secretList.addAll(resources.getSecretsList());
		
		// group의 스냅샷을 업데이트한다.
		cache.setSnapshot(
			groupName, 
			Snapshot.create(
				clusterList, 
				service.getClustersEndpoints(clusterList),
				listenerList,
				ImmutableList.of(),
				secretList,
				cache.makeNewVersion()
			)
		);
		
		LOG.info("[{}] group snapshot updated.", groupName);
	}
	
	/**
	 * file 의 config를 로드하여 Bootstrap을 반환한다.
	 * 
	 * @param file
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Bootstrap loadConfig(File file) {
		Yaml yml = new Yaml();
		
		try(
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
		) {
			Map<String, Object> map = null;
			
			Iterable<Object> iter = yml.loadAll(br);
			Iterator<Object> iterator = iter.iterator();
			
			Object root = iterator.next();
			
			LOG.debug("yaml root object class : [{}], value : [{}]", root.getClass().getName(), root.toString());
			
			map = translateAllKeyNames((Map<String, Object>)root);
			
			LOG.debug("translated key name map : [{}]", map);
			LOG.debug("{}", mapper);
			
			return mapper.convertValue(map, Bootstrap.class);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * file 이름에서 그룹 이름을 반환한다. 그룹명을 반환할 수 없는 경우 null을 반환한다. 그룹 이름은 envoy node.cluster 이름과 매칭된다.
	 * <pre>
	 * snapshot 그룹명은 확장자가 yaml 또는 yml인 config 파일의 이름으로 정해진다.
	 * default.yml => default
	 * front-proxy.yaml => front-proxy
	 * sidecar.YML => sidecar
	 * </pre>
	 * 
	 * @param fileName
	 * @return
	 */
	private String getGroupName(String fileName) {
		String[] spltName = fileName.split("\\.");
		
		if(spltName == null || spltName.length < 2) {
			return null;
		}
		
		String groupName = spltName[0];
		String extension = spltName[spltName.length - 1].toLowerCase();
		
		LOG.debug("fileName : [{}], groupName : [{}], extension : [{}]", fileName, groupName, extension);
		
		if(extension.equals("yml") || extension.equals("yaml")) {
			return groupName;
		}
		
		return null;
	}
	
	/**
	 * yaml을 parsing하여 생성된 map의 모든 데이터의 key를 kebab case에서 camel case로 변환한다.
	 * <pre>
	 * static_resources => staticResources
	 * filter_chains => filterChains
	 * </pre>
	 * 
	 * @param map
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Map<String, Object> translateAllKeyNames(final Map<String, Object> map) {
		final Map<String, Object> newMap = new HashMap<String, Object>(map.size());
		
		map.keySet().stream().map(key -> {
			Object value = map.get(key);
			
			if(key.equals("domains")) {
				LOG.debug("domains - class : [{}], value : [{}]", value.getClass().getName(), value);
			}
			
			if(value instanceof Map) {
				value = translateAllKeyNames((Map<String, Object>) value);
			}
			else if(value instanceof List){
				List list = (List)value;
				
				value = list.stream().map(item -> {
						if(item instanceof Map) {
							return translateAllKeyNames((Map<String, Object>)item);
						}
						else {
							return item;
						}
				}).collect(Collectors.toList());
			}
			
			newMap.put(translateNameKebabToCamel(key), value);
			
			return key;
		}).count();
		
		return newMap;
	}
	
	/**
	 * 입력받은 kebab case 문자열을 camel case로 변환한다.
	 * <pre>
	 * static_resources => staticResources
	 * filter_chains => filterChains
	 * </pre>
	 * 
	 * @param input
	 * @return
	 */
	private static String translateNameKebabToCamel(String input) {
    	int length = input.length();
    	StringBuilder result = new StringBuilder(length);
    	
    	for (int i=0; i<length; i++) {
    		char c = input.charAt(i);
    		
    		if(c == '-' || c == '_') {
    			c = input.charAt(i+1);
    			result.append(Character.toUpperCase(c));
    			i++;
    			continue;
    		}
    		
    		result.append(c);
    	}
    	
    	LOG.debug("before : [{}], after : [{}]", input, result.toString());
    	
    	return result.toString();
	}
	
	/**
	 * node의 cluster로 grouping 하는 cache를 생성한다.
	 * 
	 * @return
	 */
	private static SimpleCacheWrapper<String> makeCacheForGroupingWithNodeCluster() {
		return new SimpleCacheWrapper<>(new NodeGroup<String>() {
			@Override
			public String hash(io.envoyproxy.envoy.api.v2.core.Node node) {
				return null;
			}

			@Override
			public String hash(Node node) {
				LOG.debug("node : [id: {}, cluster: {}, meta: {}]", node.getId(), node.getCluster(), node.getMetadata());
				
				return node.getCluster();
			}
	    });
	}
	
	/**
	 * Control plane의 설정 파일을 변경을 추적하는 쓰레드를 실행한다.
	 * <pre>
	 * 파일이 변경되면 parseConfiguration()을 호출하여 변경된 config를 reload 한다.
	 * </pre>
	 * 
	 * @throws IOException
	 */
	private void runConfigurationWatcher() {
		LOG.debug("target Path : [{}]", configPath);
		
		try {
			final WatchService watchService = FileSystems.getDefault().newWatchService();
			
			final Path path = Paths.get(configPath);
			path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
			
			Thread observer = new Thread(() -> {
				LOG.info("Control-plane's envoy configuration file change observer start. watch path : [{}]", path);
				
				try {
					WatchKey watchKey = null;
					
					setRunConfigObserver(true);
					
					while(runConfigObserver.get()) {
						try {
							LOG.info("waiting configuration changed event.");
							
							watchKey = watchService.take();
							
							LOG.info("configuration changed event start.");
							
							watchKey.pollEvents().forEach(event -> {
								if(StandardWatchEventKinds.ENTRY_MODIFY.equals(event.kind())) {
									
									Path changed = (Path) event.context();
									LOG.info("changed file full path : [{}]", changed);
							
									reloadConfig(new File(configPath+"/"+changed.toFile()));
								}
							});
							
							watchKey.reset();
							
							LOG.info("configuration changed event complete.");
						}catch (InterruptedException e) {
							LOG.error(MessageFormat.format("Envoy configuration file watch interrupted - {0}", e.getMessage()), e);
							Thread.currentThread().interrupt();
						}catch (Exception e) {
							LOG.error(e.getMessage(), e);
						}
					}
					
					setRunConfigObserver(false);
				} finally {
					LOG.info("Control-plane's envoy configuration file change observer stop.");
				}
			});
			
			observer.setDaemon(true);
			observer.start();
		} catch(Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}
	
	private void setRunConfigObserver(boolean run) {
		runConfigObserver.set(run);
	}
	
	public long lastConfigModified() {
		return lastConfigModified.get();
	}
}
