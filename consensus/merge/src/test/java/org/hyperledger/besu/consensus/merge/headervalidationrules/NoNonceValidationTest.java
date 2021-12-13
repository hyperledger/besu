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

package org.hyperledger.besu.consensus.merge.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NoNonceValidationTest {

  @Mock private ProtocolContext protocolContext;

  @Test
  public void nonceMustBeZero() {
    final NoNonceRule rule = new NoNonceRule();
    final BlockHeader parentHeader = mock(BlockHeader.class);

    final BlockHeader invalidHeader = mock(BlockHeader.class);
    when(invalidHeader.getNonce()).thenReturn(Long.valueOf(42L));
    assertThat(rule.validate(invalidHeader, parentHeader, protocolContext)).isFalse();

    final BlockHeader validHeader = mock(BlockHeader.class);
    when(validHeader.getNonce()).thenReturn(Long.valueOf(0L));

    assertThat(rule.validate(validHeader, parentHeader, protocolContext)).isTrue();
  }
}
