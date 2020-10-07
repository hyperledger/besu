package org.hyperledger.besu.tests.acceptance.privacy.multitenancy;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MultiTenancyPrivacyNode {

  private final PrivacyNode privacyNode;
  private final Map<String, String> tenantToTokenMap;

  public MultiTenancyPrivacyNode(final PrivacyNode privacyNode) {
    this.privacyNode = privacyNode;
    this.tenantToTokenMap = new HashMap<>();
  }

  public MultiTenancyPrivacyNode addTenantWithToken(final String tenant, final String token) {
    tenantToTokenMap.put(tenant, token);
    return this;
  }

  public List<String> getTenants() {
    return tenantToTokenMap.keySet().stream().collect(Collectors.toList());
  }

  public String getTokenForTenant(final String tenant) {
    return tenantToTokenMap.get(tenant);
  }

  public PrivacyNode getPrivacyNode() {
    return privacyNode;
  }
}
