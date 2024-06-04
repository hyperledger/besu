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
package org.hyperledger.besu.cli.converter;

import static org.hyperledger.besu.cli.converter.IpSubnetFilterRuleConverter.parseSubnetRules;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.netty.handler.ipfilter.IpSubnetFilterRule;
import org.junit.jupiter.api.Test;

public class IpFilterRuleConverterTest {

  @Test
  void testCreateIpRestrictionHandlerWithValidSubnets() {
    List<String> allowedSubnets = Arrays.asList("192.168.1.0/24", "10.0.0.0/8");
    List<IpSubnetFilterRule> rules = parseSubnetRules(allowedSubnets);
    assertEquals(2, rules.size());
  }

  @Test
  void testCreateIpRestrictionHandlerWithInvalidSubnet() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          List<String> allowedSubnets = Collections.singletonList("abc");
          parseSubnetRules(allowedSubnets);
        });
  }

  @Test
  void testCreateIpRestrictionHandlerMissingCIDR() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          List<String> allowedSubnets = Collections.singletonList("192.168.1.0");
          parseSubnetRules(allowedSubnets);
        });
  }

  @Test
  void testCreateIpRestrictionHandlerBigCIDR() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          List<String> allowedSubnets = Collections.singletonList("192.168.1.0:25");
          parseSubnetRules(allowedSubnets);
        });
  }

  @Test
  void testCreateIpRestrictionHandlerWithInvalidCIDR() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          List<String> allowedSubnets = Collections.singletonList("192.168.1.0/abc");
          parseSubnetRules(allowedSubnets);
        });
  }

  @Test
  void testCreateIpRestrictionHandlerWithEmptyList() {
    List<String> allowedSubnets = Collections.emptyList();
    List<IpSubnetFilterRule> rules = parseSubnetRules(allowedSubnets);
    assertEquals(0, rules.size()); // No rules should be present
  }

  @Test
  void testCreateIpRestrictionHandlerWithNullList() {
    List<IpSubnetFilterRule> rules = parseSubnetRules(null);
    assertEquals(0, rules.size()); // No rules should be present
  }
}
