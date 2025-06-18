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

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import picocli.CommandLine;

/** The SubnetInfo converter for CLI options. */
public class SubnetInfoConverter implements CommandLine.ITypeConverter<SubnetInfo> {
  /** Default Constructor. */
  public SubnetInfoConverter() {}

  /**
   * Converts an IP addresses with CIDR notation into SubnetInfo
   *
   * @param value The IP addresses with CIDR notation.
   * @return the SubnetInfo
   */
  @Override
  public SubnetInfo convert(final String value) {
    return new SubnetUtils(value).getInfo();
  }
}
