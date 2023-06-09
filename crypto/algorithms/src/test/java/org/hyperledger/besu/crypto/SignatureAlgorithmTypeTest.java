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
package org.hyperledger.besu.crypto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;

public class SignatureAlgorithmTypeTest {
  @Test
  public void shouldReturnSECP256K1Instance() {
    SignatureAlgorithmType signatureAlgorithmType = SignatureAlgorithmType.create("secp256k1");

    assertThat(signatureAlgorithmType.getInstance().getClass().getSimpleName())
        .isEqualTo(SECP256K1.class.getSimpleName());
  }

  @Test
  public void shouldThrowExceptionWhenInvalidParameterIsGiven() {
    assertThatThrownBy(() -> SignatureAlgorithmType.create("abcd"))
        .hasMessage("abcd is not in the list of valid elliptic curves [secp256k1, secp256r1]");
  }
}
