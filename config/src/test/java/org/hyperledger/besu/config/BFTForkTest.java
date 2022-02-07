/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.Address;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

public class BFTForkTest {

  @Test
  public void getMiningBeneficiary_fromEmptyConfig() {
    ObjectNode config = JsonUtil.objectNodeFromMap(Collections.emptyMap());

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary()).isEmpty();
  }

  @Test
  public void getMiningBeneficiary_withValidAddress() {
    final String beneficiary = "0x1111111111111111111111111111111111111111";
    ObjectNode config =
        JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, beneficiary));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary().map(Address::toHexString)).contains(beneficiary);
  }

  @Test
  public void getMiningBeneficiary_withValidAddressMissingPrefix() {
    final String beneficiary = "0x1111111111111111111111111111111111111111";
    ObjectNode config =
        JsonUtil.objectNodeFromMap(
            Map.of(BftFork.MINING_BENEFICIARY_KEY, beneficiary.substring(2)));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary().map(Address::toHexString)).contains(beneficiary);
  }

  @Test
  public void getMiningBeneficiary_withValidAddressAndEmptySpace() {
    final String beneficiary = "0x1111111111111111111111111111111111111111";
    ObjectNode config =
        JsonUtil.objectNodeFromMap(
            Map.of(BftFork.MINING_BENEFICIARY_KEY, "\t" + beneficiary + "  "));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary().map(Address::toHexString)).contains(beneficiary);
  }

  @Test
  public void getMiningBeneficiary_withInvalidValue() {
    // Address is only 19 bytes
    final String beneficiary = "random";
    ObjectNode config =
        JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, beneficiary));

    final BftFork bftFork = new BftFork(config);
    assertThatThrownBy(bftFork::getMiningBeneficiary)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Illegal character");
  }

  @Test
  public void getMiningBeneficiary_withInvalidAddress() {
    // Address is only 19 bytes
    final String beneficiary = "0x11111111111111111111111111111111111111";
    ObjectNode config =
        JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, beneficiary));

    final BftFork bftFork = new BftFork(config);
    assertThatThrownBy(bftFork::getMiningBeneficiary)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("An account address must be be 20 bytes long");
  }

  @Test
  public void getMiningBeneficiary_withInvalidAddressAndWhitespace() {
    // Address is only 19 bytes
    final String beneficiary = "0x11111111111111111111111111111111111111  ";
    ObjectNode config =
        JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, beneficiary));

    final BftFork bftFork = new BftFork(config);
    assertThatThrownBy(bftFork::getMiningBeneficiary)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("An account address must be be 20 bytes long");
  }

  @Test
  public void getMiningBeneficiary_whenEmptyValueIsProvided() {
    ObjectNode config = JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, "    "));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary()).isEmpty();
  }
}
