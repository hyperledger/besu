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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.Before;
import org.junit.Test;

public class SignatureAlgorithmFactoryTest {

  @Before
  public void setUp() {
    SignatureAlgorithmFactory.resetInstance();
  }

  @Test
  public void shouldReturnSECP256K1InstanceByDefault() {
    SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    assertThat(signatureAlgorithm.getClass().getSimpleName())
        .isEqualTo(SECP256K1.class.getSimpleName());
  }

  @Test
  public void shouldReturnSECP256K1InstanceWhenSet() {
    SignatureAlgorithmFactory.setInstance(SignatureAlgorithmType.create("secp256k1"));

    SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    assertThat(signatureAlgorithm.getClass().getSimpleName())
        .isEqualTo(SECP256K1.class.getSimpleName());
  }

  @Test
  public void shouldThrowExceptionWhenSetMoreThanOnce() {
    SignatureAlgorithmFactory.setInstance(SignatureAlgorithmType.create("secp256k1"));
    assertThat(SignatureAlgorithmFactory.isInstanceSet()).isTrue();

    assertThatThrownBy(
            () -> SignatureAlgorithmFactory.setInstance(SignatureAlgorithmType.create("secp256k1")))
        .isInstanceOf(RuntimeException.class);
  }
}
