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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import picocli.CommandLine;

/** The Ip subnet filter converter for CLI options. */
public class SubnetInfoConverter implements CommandLine.ITypeConverter<List<SubnetInfo>> {
  /** Default Constructor. */
  public SubnetInfoConverter() {}

  /**
   * Converts a comma-separated string of IP addresses with CIDR notation into a list of SubnetInfo
   * objects.
   *
   * @param value The comma-separated string of IP addresses with CIDR notation.
   * @return A list of SubnetInfo objects, or an empty list if the input is null or blank.
   */
  @Override
  public List<SubnetInfo> convert(final String value) {
    // Check if the input string is null or blank, and return an empty list if true.
    if (value == null || value.isBlank()) {
      return List.of();
    }
    List<String> list = Stream.of(value.split(",")).toList();
    return parseSubnetRules(list);
  }

  /**
   * Converts a list of IP addresses with CIDR notation into a list of SubnetInfo objects. Each IP
   * address is accepted by the filter rule.
   *
   * @param allowedSubnets A list of IP addresses with CIDR notation.
   * @return A list of SubnetInfo objects, or an empty list if the input is null or blank.
   */
  @VisibleForTesting
  public static List<SubnetInfo> parseSubnetRules(final List<String> allowedSubnets) {
    if (allowedSubnets == null || allowedSubnets.isEmpty()) {
      return List.of();
    }
    List<SubnetInfo> rulesList = new ArrayList<>();
    for (String subnet : allowedSubnets) {
      SubnetInfo info = new SubnetUtils(subnet).getInfo();
      rulesList.add(info);
    }
    return rulesList;
  }
}
