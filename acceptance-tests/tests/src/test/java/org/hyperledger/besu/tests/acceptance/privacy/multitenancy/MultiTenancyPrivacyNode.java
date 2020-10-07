/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
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
