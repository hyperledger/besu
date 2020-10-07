package org.hyperledger.besu.tests.acceptance.privacy.multitenancy;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MultiTenancyPrivacyGroup {

  private final Map<MultiTenancyPrivacyNode, List<String>> map;

  public MultiTenancyPrivacyGroup() {
    this.map = new HashMap<>();
  }

  public MultiTenancyPrivacyGroup addNodeWithTenants(
      final MultiTenancyPrivacyNode privacyNode, final List<String> tenants) {
    map.put(privacyNode, tenants);
    return this;
  }

  public List<MultiTenancyPrivacyNode> getPrivacyNodes() {
    return map.keySet().stream().collect(Collectors.toList());
  }

  public List<String> getTenantsForNode(final MultiTenancyPrivacyNode privacyNode) {
    return map.get(privacyNode);
  }

  public List<String> getTenants() {
    return map.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
  }

  public PrivacyNode getGroupCreatingPrivacyNode() {
    return getPrivacyNodes().get(0).getPrivacyNode();
  }

  public String getGroupCreatingTenant() {
    return getPrivacyNodes().get(0).getTenants().get(0);
  }
}
