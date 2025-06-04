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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.junit.jupiter.api.Test;

public class SubnetInfoConverterTest {

  @Test
  void testCreateIpRestrictionHandlerWithValidSubnets() {
    String subnet = "192.168.1.0/24";
    assertThat(parseSubnetRules(subnet).getCidrSignature()).isEqualTo(subnet);
  }

  @Test
  void testCreateIpRestrictionHandlerWithInvalidSubnet() {
    assertThrows(IllegalArgumentException.class, () -> parseSubnetRules("abc"));
  }

  @Test
  void testCreateIpRestrictionHandlerMissingCIDR() {
    assertThrows(IllegalArgumentException.class, () -> parseSubnetRules("192.168.1.0"));
  }

  @Test
  void testCreateIpRestrictionHandlerBigCIDR() {
    assertThrows(IllegalArgumentException.class, () -> parseSubnetRules("192.168.1.0:25"));
  }

  @Test
  void testCreateIpRestrictionHandlerWithInvalidCIDR() {
    assertThrows(IllegalArgumentException.class, () -> parseSubnetRules("192.168.1.0/abc"));
  }

  @Test
  void testCreateIpRestrictionHandlerWithEmptyString() {
    assertThrows(IllegalArgumentException.class, () -> parseSubnetRules(""));
  }

  private SubnetInfo parseSubnetRules(final String subnet) {
    return new SubnetInfoConverter().convert(subnet);
  }
}
