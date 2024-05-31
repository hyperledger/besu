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

import java.util.ArrayList;
import java.util.List;

import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;
import io.netty.handler.ipfilter.RuleBasedIpFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IpFilterRuleCreator {
  private static final Logger LOG = LoggerFactory.getLogger(IpFilterRuleCreator.class);

  /**
   * Creates a RuleBasedIpFilter based on a list of allowed subnets.
   *
   * @param allowedSubnets A list of allowed subnets in CIDR notation.
   * @return A RuleBasedIpFilter configured with rules based on the allowed subnets.
   */
  public static RuleBasedIpFilter createRuleBasedIpFilter(final List<String> allowedSubnets) {
    if (allowedSubnets == null || allowedSubnets.isEmpty()) {
      return new RuleBasedIpFilter(true); // No restrictions
    }
    return new RuleBasedIpFilter(false, parseSubnetRules(allowedSubnets));
  }

  /**
   * Parses a list of allowed subnets into an array of IpSubnetFilterRule objects.
   *
   * @param allowedSubnets A list of allowed subnets in CIDR notation.
   * @return An array of IpSubnetFilterRule objects.
   */
  public static IpSubnetFilterRule[] parseSubnetRules(final List<String> allowedSubnets) {
    if (allowedSubnets == null || allowedSubnets.isEmpty()) {
      return new IpSubnetFilterRule[0];
    }
    List<IpSubnetFilterRule> rulesList = new ArrayList<>();
    for (String subnet : allowedSubnets) {
      try {
        IpSubnetFilterRule rule = new IpSubnetFilterRule(subnet, IpFilterRuleType.ACCEPT);
        rulesList.add(rule);
      } catch (IllegalArgumentException e) {
        LOG.trace("Skipping invalid subnet: {} subnet ({})", subnet, e.getMessage());
      }
    }
    return rulesList.toArray(new IpSubnetFilterRule[0]);
  }
}
