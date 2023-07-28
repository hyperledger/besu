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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class IncrementingNonceGeneratorTest {

  @Test
  public void firstValueProvidedIsSuppliedAtConstruction() {
    final long initialValue = 0L;
    final IncrementingNonceGenerator generator = new IncrementingNonceGenerator(initialValue);

    assertThat(generator.iterator().next()).isEqualTo(initialValue);
  }

  @Test
  public void rollOverFromMaxResetsToZero() {
    final long initialValue = 0xFFFFFFFFFFFFFFFFL;
    final IncrementingNonceGenerator generator = new IncrementingNonceGenerator(initialValue);

    assertThat(generator.iterator().next()).isEqualTo(initialValue);
    final Long nextValue = generator.iterator().next();
    assertThat(Long.compareUnsigned(nextValue, 0)).isEqualTo(0);
  }
}
