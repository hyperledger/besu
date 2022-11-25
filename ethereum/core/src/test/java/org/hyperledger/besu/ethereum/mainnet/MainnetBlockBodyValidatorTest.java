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
package org.hyperledger.besu.ethereum.mainnet;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MainnetBlockBodyValidatorTest {

  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  private final BlockDataGenerator.BlockOptions validBlockOptions =
      BlockDataGenerator.BlockOptions.create()
          .setReceiptsRoot(Hash.EMPTY_TRIE_HASH)
          .setGasUsed(0)
          .setLogsBloom(LogsBloomFilter.empty())
          .hasOmmers(false);
  private MainnetBlockBodyValidator validator;

  @Before
  public void setup() {
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    validator = new MainnetBlockBodyValidator(protocolSchedule);
  }

  @Test
  public void validateBodyLight_ReturnsTrue_WhenWithdrawalsProhibited_WithNullWithdrawals() {
    whenWithdrawalsProhibited();

    final boolean result =
        validator.validateBodyLight(null, blockWithNullWithdrawals(), emptyList(), null);

    assertThat(result).isTrue();
  }

  @Test
  public void validateBodyLight_ReturnsFalse_WhenWithdrawalsProhibited_WithNonNullWithdrawals() {
    whenWithdrawalsProhibited();

    final boolean result =
        validator.validateBodyLight(null, blockWithWithdrawals(), emptyList(), null);

    assertThat(result).isFalse();
  }

  @Test
  public void validateBodyLight_ReturnsFalse_WhenWithdrawalsAllowed_WithNullWithdrawals() {
    whenWithdrawalsAllowed();

    final boolean result =
        validator.validateBodyLight(null, blockWithNullWithdrawals(), emptyList(), null);

    assertThat(result).isFalse();
  }

  @Test
  public void validateBodyLight_ReturnsTrue_WhenWithdrawalsAllowed_WithNonNullWithdrawals() {
    whenWithdrawalsAllowed();

    final boolean result =
        validator.validateBodyLight(null, blockWithWithdrawals(), emptyList(), null);

    assertThat(result).isTrue();
  }

  private void whenWithdrawalsProhibited() {
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());
  }

  private void whenWithdrawalsAllowed() {
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());
  }

  private Block blockWithNullWithdrawals() {
    return getBlock(validBlockOptions.setWithdrawals(Optional.empty()));
  }

  private Block blockWithWithdrawals() {
    return getBlock(
        validBlockOptions
            .setWithdrawalsRoot(Hash.EMPTY_TRIE_HASH)
            .setWithdrawals(Optional.of(emptyList())));
  }

  private Block getBlock(final BlockDataGenerator.BlockOptions options) {
    return new BlockDataGenerator().block(options);
  }
}
