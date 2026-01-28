/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.Address;

import java.math.BigInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class CodeDelegationDeserializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void shouldDeserializeWithYParity() throws Exception {
    // EIP-7702 spec uses "yParity" field name
    final String json =
        """
        {
          "chainId": "0x1",
          "address": "0x1234567890123456789012345678901234567890",
          "nonce": "0x0",
          "yParity": "0x0",
          "r": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
          "s": "0xfedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321"
        }
        """;

    final CodeDelegation codeDelegation = objectMapper.readValue(json, CodeDelegation.class);

    assertThat(codeDelegation.chainId()).isEqualTo(BigInteger.ONE);
    assertThat(codeDelegation.address())
        .isEqualTo(Address.fromHexString("0x1234567890123456789012345678901234567890"));
    assertThat(codeDelegation.nonce()).isEqualTo(0L);
    assertThat(codeDelegation.v()).isEqualTo((byte) 0);
  }

  @Test
  public void shouldDeserializeWithV() throws Exception {
    // Legacy format uses "v" field name
    final String json =
        """
        {
          "chainId": "0x1",
          "address": "0x1234567890123456789012345678901234567890",
          "nonce": "0x0",
          "v": "0x1",
          "r": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
          "s": "0xfedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321"
        }
        """;

    final CodeDelegation codeDelegation = objectMapper.readValue(json, CodeDelegation.class);

    assertThat(codeDelegation.chainId()).isEqualTo(BigInteger.ONE);
    assertThat(codeDelegation.address())
        .isEqualTo(Address.fromHexString("0x1234567890123456789012345678901234567890"));
    assertThat(codeDelegation.nonce()).isEqualTo(0L);
    assertThat(codeDelegation.v()).isEqualTo((byte) 1);
  }

  @Test
  public void shouldPreferYParityOverV() throws Exception {
    // If both are provided, yParity takes precedence
    final String json =
        """
        {
          "chainId": "0x1",
          "address": "0x1234567890123456789012345678901234567890",
          "nonce": "0x0",
          "v": "0x1",
          "yParity": "0x0",
          "r": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
          "s": "0xfedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321"
        }
        """;

    final CodeDelegation codeDelegation = objectMapper.readValue(json, CodeDelegation.class);

    // yParity (0x0) should be used, not v (0x1)
    assertThat(codeDelegation.v()).isEqualTo((byte) 0);
  }

  @Test
  public void shouldFailWhenNeitherVNorYParityProvided() throws Exception {
    final String json =
        """
        {
          "chainId": "0x1",
          "address": "0x1234567890123456789012345678901234567890",
          "nonce": "0x0",
          "r": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
          "s": "0xfedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321"
        }
        """;

    assertThatThrownBy(() -> objectMapper.readValue(json, CodeDelegation.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Either 'v' or 'yParity' must be provided");
  }

  @Test
  public void shouldDeserializeAuthorizationListWithYParity() throws Exception {
    // Test full authorization list as it would appear in eth_estimateGas
    final String json =
        """
        {
          "from": "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
          "to": "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
          "value": "0x1",
          "authorizationList": [
            {
              "chainId": "0x1",
              "address": "0x1234567890123456789012345678901234567890",
              "nonce": "0x0",
              "yParity": "0x0",
              "r": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
              "s": "0xfedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321"
            }
          ]
        }
        """;

    // This should deserialize successfully using CallParameter
    final org.hyperledger.besu.ethereum.transaction.CallParameter callParameter =
        objectMapper.readValue(json, org.hyperledger.besu.ethereum.transaction.CallParameter.class);

    assertThat(callParameter.getCodeDelegationAuthorizations()).isNotEmpty();
    assertThat(callParameter.getCodeDelegationAuthorizations()).hasSize(1);

    final org.hyperledger.besu.datatypes.CodeDelegation auth =
        callParameter.getCodeDelegationAuthorizations().get(0);

    assertThat(auth.chainId()).isEqualTo(BigInteger.ONE);
    assertThat(auth.address())
        .isEqualTo(Address.fromHexString("0x1234567890123456789012345678901234567890"));
    assertThat(auth.nonce()).isEqualTo(0L);
    assertThat(auth.v()).isEqualTo((byte) 0);
  }
}
