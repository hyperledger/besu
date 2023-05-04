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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class SendRawTransactionConditionalParameterTest {
  //  private static final String METHOD = "eth_sendRawTransactionConditional";

  @Test
  public void noConditionsParamDecodesCorrectly() {
    final SendRawTransactionConditionalParameter param = parameterWithNoConditions();
    assertThat(param.getTimestampMin()).isEmpty();
    assertThat(param.getTimestampMax()).isEmpty();
    assertThat(param.getBlockNumberMin()).isEmpty();
    assertThat(param.getBlockNumberMax()).isEmpty();
    assertThat(param.getKnownAccounts()).isEmpty();
  }

  @Test
  public void blockNumberConditionParamDecodesCorrectly() {
    final SendRawTransactionConditionalParameter param =
        parameterWithBlockNumberConditions(5L, 15L);
    assertThat(param.getTimestampMin()).isEmpty();
    assertThat(param.getTimestampMax()).isEmpty();
    assertThat(param.getBlockNumberMin().get()).isEqualTo(5L);
    assertThat(param.getBlockNumberMax().get()).isEqualTo(15L);
    assertThat(param.getKnownAccounts()).isEmpty();
  }

  @Test
  public void timestampConditionParamDecodesCorrectly() {
    final SendRawTransactionConditionalParameter param =
        parameterWithTimestampConditions(1777L, 1999L);
    assertThat(param.getTimestampMin().get()).isEqualTo(1777L);
    assertThat(param.getTimestampMax().get()).isEqualTo(1999L);
    assertThat(param.getBlockNumberMin()).isEmpty();
    assertThat(param.getBlockNumberMax()).isEmpty();
    assertThat(param.getKnownAccounts()).isEmpty();
  }

  @Test
  public void knownAccountConditionsParamDecodesCorrectly() {
    final Map<Address, Hash> knownAccounts = new HashMap<>();
    knownAccounts.put(Address.ZERO, Hash.ZERO);
    final SendRawTransactionConditionalParameter param =
        parameterWithKnownAccountConditions(knownAccounts);
    assertThat(param.getTimestampMin()).isEmpty();
    assertThat(param.getTimestampMax()).isEmpty();
    assertThat(param.getBlockNumberMin()).isEmpty();
    assertThat(param.getBlockNumberMax()).isEmpty();
    assertThat(param.getKnownAccounts().get()).containsExactly(Map.entry(Address.ZERO, Hash.ZERO));
  }

  private SendRawTransactionConditionalParameter parameterWithNoConditions() {
    return new SendRawTransactionConditionalParameter(null, null, null, null, null);
  }

  private SendRawTransactionConditionalParameter parameterWithBlockNumberConditions(
      final long blockNumberMin, final long blockNumberMax) {
    return new SendRawTransactionConditionalParameter(
        blockNumberMin, blockNumberMax, null, null, null);
  }

  private SendRawTransactionConditionalParameter parameterWithTimestampConditions(
      final long timestampMin, final long timestampMax) {
    return new SendRawTransactionConditionalParameter(null, null, null, timestampMin, timestampMax);
  }

  private SendRawTransactionConditionalParameter parameterWithKnownAccountConditions(
      final Map<Address, Hash> knownAccounts) {
    return new SendRawTransactionConditionalParameter(null, null, knownAccounts, null, null);
  }

  //  private SendRawTransactionConditionalParameter readJsonAsConditionalParameter(final String
  // json) throws java.io.IOException {
  //    return new ObjectMapper().readValue(json, SendRawTransactionConditionalParameter.class);
  //  }
}
