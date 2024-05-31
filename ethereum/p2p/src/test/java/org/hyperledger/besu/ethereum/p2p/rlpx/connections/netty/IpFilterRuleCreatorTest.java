/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.netty.handler.ipfilter.IpFilterRule;
import org.junit.jupiter.api.Test;

public class IpFilterRuleCreatorTest {

  @Test
  void testCreateIpRestrictionHandlerWithValidSubnets() {
    List<String> allowedSubnets = Arrays.asList("192.168.1.0/24", "10.0.0.0/8");
    IpFilterRule[] rules = IpFilterRuleCreator.parseSubnetRules(allowedSubnets);
    assertEquals(2, rules.length);
  }

  @Test
  void testCreateIpRestrictionHandlerWithInvalidSubnetFormat() {
    List<String> allowedSubnets = Collections.singletonList("192.168.1.0"); // Missing CIDR prefix
    IpFilterRule[] rules = IpFilterRuleCreator.parseSubnetRules(allowedSubnets);
    assertEquals(0, rules.length);
  }

  @Test
  void testCreateIpRestrictionHandlerWithEmptyList() {
    List<String> allowedSubnets = Collections.emptyList();
    IpFilterRule[] rules = IpFilterRuleCreator.parseSubnetRules(allowedSubnets);
    assertEquals(0, rules.length); // No rules should be present
  }

  @Test
  void testCreateIpRestrictionHandlerWithNullList() {
    IpFilterRule[] rules = IpFilterRuleCreator.parseSubnetRules(null);
    assertEquals(0, rules.length); // No rules should be present
  }
}
