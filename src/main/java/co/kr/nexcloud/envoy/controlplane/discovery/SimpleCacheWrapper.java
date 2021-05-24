package co.kr.nexcloud.envoy.controlplane.discovery;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.envoyproxy.controlplane.cache.NodeGroup;
import io.envoyproxy.controlplane.cache.v3.SimpleCache;
import io.envoyproxy.controlplane.cache.v3.Snapshot;
import io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.envoyproxy.envoy.config.cluster.v3.Cluster.DiscoveryType;
import io.envoyproxy.envoy.config.endpoint.v3.ClusterLoadAssignment;

/**
 * SimpleCach wrapper 클래스
 * 
 * @author mrchopa
 *
 * @param <T>
 */
public class SimpleCacheWrapper<T> extends SimpleCache<T>{
	private static final Logger LOG = LoggerFactory.getLogger(SimpleCacheWrapper.class);
	
	private final HashSet<T> groups = new HashSet<T>();
	
	public SimpleCacheWrapper(NodeGroup<T> nodeGroup) {
		super(nodeGroup);
	}
	
	@Override
	public Snapshot getSnapshot(T group) {
		LOG.debug("called by : [{}]", group);
		
		return super.getSnapshot(group);
	}
	
	@Override
	public void setSnapshot(T group, Snapshot snapshot) {
		super.setSnapshot(group, snapshot);
		
		groups.add(group);
	}
	
	@Override
	public boolean clearSnapshot(T group) {
		if(super.clearSnapshot(group)) {
			groups.remove(group);
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public Collection<T> groups() {
		return ImmutableSet.copyOf(groups);
	}
	
	/**
	 * cache의 ClusterLoadAssignment(Endpoints) 정보를 갱신한다.
	 *
	 * @param clusterLoadAssignments
	 */
	public void updateClusterLoadAssignments(final Iterable<ClusterLoadAssignment> clusterLoadAssignments) {
		this.groups().forEach(group -> {
			Snapshot oldSnapshot = this.getSnapshot(group);
			
			String oldVersion = oldSnapshot.endpoints().version();
			String newVersion = makeNewVersion();
			
//			final Map<String, ClusterLoadAssignment> claMap = new HashMap<>();
//			
//			clusterLoadAssignments.forEach(cla -> {
//				claMap.put(cla.getClusterName(), cla);
//			});
//			
//			List<Cluster> updatedClusterList = oldSnapshot.clusters().resources().values().stream().map(cluster -> {
//				if(claMap.containsKey(cluster.getName())) {
//					return Cluster.newBuilder(cluster)
//							.setLoadAssignment(claMap.get(cluster.getName())).build();
//				}
//				
//				return cluster;
//			}).collect(Collectors.toList());
			
			this.setSnapshot(
					group,
					Snapshot.create(
							oldSnapshot.clusters().resources().values().stream().collect(Collectors.toList()), 
							clusterLoadAssignments, 
							oldSnapshot.listeners().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.routes().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.secrets().resources().values().stream().collect(Collectors.toList()), 
							newVersion
					)
			);
			
			LOG.debug("{} group's ClusterLoadAssignments updated as old version {} to new version {} snapshot", group, oldVersion, newVersion);
		});
	}
	
	/**
	 * 단일 클러스터 정보를 업데이트한다.
	 * 
	 * @param cluster
	 */
	public void updateCluster(final Cluster cluster) {
		this.groups().forEach(group -> {
			Snapshot oldSnapshot = this.getSnapshot(group);
			
			String oldVersion = oldSnapshot.endpoints().version();
			String newVersion = makeNewVersion();
			
			Map<String, Cluster> oldClusterResourceMap = oldSnapshot.clusters().resources();
			
			List<Cluster> clusterList = oldClusterResourceMap.keySet().stream().filter(key -> !key.equals(cluster.getName())).map(key -> oldClusterResourceMap.get(key)).collect(Collectors.toList());
			clusterList.add(cluster);
			
			this.setSnapshot(
					group,
					Snapshot.create(
							clusterList,
							oldSnapshot.endpoints().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.listeners().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.routes().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.secrets().resources().values().stream().collect(Collectors.toList()), 
							newVersion
					)
			);
			
			LOG.debug("{} group's ClusterLoadAssignments updated as old version {} to new version {} snapshot", oldVersion, newVersion);
		});
	}
	
	/**
	 * 클러스터 목록을 업데이트한다.
	 * @param clusters
	 */
	public void updateCluster(final List<Cluster> clusters) {
		Set<String> newClusterNameSet = clusters.stream().map(c -> c.getName()).collect(Collectors.toSet());
		LOG.debug("groups size : [{}]", this.groups().size());
		
		this.groups().forEach(group -> {
			LOG.debug("{} groups cluster update start.", group);
			
			Snapshot oldSnapshot = this.getSnapshot(group);
			
			String oldVersion = oldSnapshot.endpoints().version();
			String newVersion = makeNewVersion();
			
			Map<String, Cluster> oldClusterResourceMap = oldSnapshot.clusters().resources();
			
			List<Cluster> clusterList = oldClusterResourceMap.keySet().stream().filter(key -> !newClusterNameSet.contains(key)).map(key -> oldClusterResourceMap.get(key)).collect(Collectors.toList());
			clusterList.addAll(clusters);
			
			this.setSnapshot(
					group,
					Snapshot.create(
							clusterList,
							oldSnapshot.endpoints().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.listeners().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.routes().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.secrets().resources().values().stream().collect(Collectors.toList()), 
							newVersion
					)
			);
			
			LOG.debug("{} group's ClusterLoadAssignments updated as old version {} to new version {} snapshot", group, oldVersion, newVersion);
		});
	}
	
	/**
	 * clusterName에 해당하는 클러스터의 LoadAssignment를 업데이트한다.
	 * 
	 * @param clusterName
	 * @param updatedCla
	 */
	public void updateCluster(String clusterName, ClusterLoadAssignment updatedCla) {
		this.groups().forEach(group -> {
			Snapshot oldSnapshot = this.getSnapshot(group);
			
			String oldVersion = oldSnapshot.endpoints().version();
			String newVersion = makeNewVersion();
			
			Map<String, Cluster> oldClusterResourceMap = oldSnapshot.clusters().resources();
			
			Cluster updatedCluster = Cluster.newBuilder()
										.setName(clusterName)
										.setType(DiscoveryType.STATIC)
										.setLoadAssignment(updatedCla)
										.build();
			
			List<Cluster> clusterList = oldClusterResourceMap.keySet().stream().filter(key -> !key.equals(clusterName)).map(key -> oldClusterResourceMap.get(key)).collect(Collectors.toList());
			clusterList.add(updatedCluster);
			
			this.setSnapshot(
					group,
					Snapshot.create(
							clusterList, 
							oldSnapshot.endpoints().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.listeners().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.routes().resources().values().stream().collect(Collectors.toList()), 
							oldSnapshot.secrets().resources().values().stream().collect(Collectors.toList()), 
							newVersion
					)
			);
			
			LOG.debug("{} group's ClusterLoadAssignments updated as old version {} to new version {} snapshot", group, oldVersion, newVersion);
		});
	}
	
	/**
	 * snapshot의 새로운 버전을 생성한다.
	 * <pre>
	 * snapshot의 버전은 equals()로 비교된다.
	 * </pre>
	 * 
	 * @return
	 */
	public String makeNewVersion() {
		return Long.toString(System.currentTimeMillis());
	}
}
