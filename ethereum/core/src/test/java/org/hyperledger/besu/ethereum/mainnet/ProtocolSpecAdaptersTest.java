/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProtocolSpecAdaptersTest {

  @Mock private Function<ProtocolSpecBuilder, ProtocolSpecBuilder> firstModifier;

  @Mock private Function<ProtocolSpecBuilder, ProtocolSpecBuilder> secondModifier;

  @Test
  public void specAdapterFindsTheModifierBelowRequestedBlock() {

    final ProtocolSpecAdapters adapters =
        new ProtocolSpecAdapters(Map.of(3L, firstModifier, 5L, secondModifier));

    assertThat(adapters.getModifierForBlock(3)).isEqualTo(firstModifier);
    assertThat(adapters.getModifierForBlock(4)).isEqualTo(firstModifier);
    assertThat(adapters.getModifierForBlock(5)).isEqualTo(secondModifier);
    assertThat(adapters.getModifierForBlock(6)).isEqualTo(secondModifier);
    assertThat(adapters.getModifierForBlock(0)).isEqualTo(Function.identity());
  }
}
