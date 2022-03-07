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
  private final String BENEFICIARY = "0x1111111111111111111111111111111111111111";
  private final String TRUNCATED_BENEFICIARY = BENEFICIARY.substring(0, BENEFICIARY.length() - 2);

  @Test
  public void getMiningBeneficiary_fromEmptyConfig() {
    ObjectNode config = JsonUtil.objectNodeFromMap(Collections.emptyMap());

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary()).isEmpty();
  }

  @Test
  public void getMiningBeneficiary_withValidAddress() {
    ObjectNode config =
        JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, BENEFICIARY));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary().map(Address::toHexString)).contains(BENEFICIARY);
  }

  @Test
  public void getMiningBeneficiary_withValidAddressMissingPrefix() {
    ObjectNode config =
        JsonUtil.objectNodeFromMap(
            Map.of(BftFork.MINING_BENEFICIARY_KEY, BENEFICIARY.substring(2)));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary().map(Address::toHexString)).contains(BENEFICIARY);
  }

  @Test
  public void getMiningBeneficiary_withValidAddressAndEmptySpace() {
    ObjectNode config =
        JsonUtil.objectNodeFromMap(
            Map.of(BftFork.MINING_BENEFICIARY_KEY, "\t" + BENEFICIARY + "  "));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary().map(Address::toHexString)).contains(BENEFICIARY);
  }

  @Test
  public void getMiningBeneficiary_withInvalidValue() {
    final String beneficiary = "random";
    testGetMiningBeneficiaryWithInvalidAddress(beneficiary);
  }

  @Test
  public void getMiningBeneficiary_withInvalidAddress() {
    testGetMiningBeneficiaryWithInvalidAddress(TRUNCATED_BENEFICIARY);
  }

  @Test
  public void getMiningBeneficiary_withInvalidAddressAndWhitespace() {
    final String beneficiary = TRUNCATED_BENEFICIARY + "  ";
    testGetMiningBeneficiaryWithInvalidAddress(beneficiary);
  }

  private void testGetMiningBeneficiaryWithInvalidAddress(final String miningBeneficiary) {
    ObjectNode config =
        JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, miningBeneficiary));

    final BftFork bftFork = new BftFork(config);
    assertThatThrownBy(bftFork::getMiningBeneficiary)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Mining beneficiary in transition config is not a valid ethereum address");
  }

  @Test
  public void getMiningBeneficiary_whenEmptyValueIsProvided() {
    ObjectNode config = JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, "    "));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.getMiningBeneficiary()).isEmpty();
  }

  @Test
  public void isMiningBeneficiaryConfigured_noKeySet() {
    final String jsonStr = "{}";
    final ObjectNode config = JsonUtil.objectNodeFromString(jsonStr);
    final BftFork bftFork = new BftFork(config);

    assertThat(bftFork.isMiningBeneficiaryConfigured()).isFalse();
  }

  @Test
  public void isMiningBeneficiaryConfigured_whenEmptyValueIsProvided() {
    ObjectNode config = JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, ""));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.isMiningBeneficiaryConfigured()).isTrue();
  }

  @Test
  public void isMiningBeneficiaryConfigured_whenNonEmptyValueIsProvided() {
    ObjectNode config =
        JsonUtil.objectNodeFromMap(Map.of(BftFork.MINING_BENEFICIARY_KEY, BENEFICIARY));

    final BftFork bftFork = new BftFork(config);
    assertThat(bftFork.isMiningBeneficiaryConfigured()).isTrue();
  }
}
