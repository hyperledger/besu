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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWHasher;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ValidationTestUtils;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProofOfWorkValidationRuleTest {

  private final BlockHeader blockHeader;
  private final BlockHeader parentHeader;
  private final ProofOfWorkValidationRule validationRule;

  public ProofOfWorkValidationRuleTest(final long parentBlockNum, final long blockNum)
      throws IOException {
    blockHeader = ValidationTestUtils.readHeader(parentBlockNum);
    parentHeader = ValidationTestUtils.readHeader(blockNum);
    validationRule =
        new ProofOfWorkValidationRule(
            new EpochCalculator.DefaultEpochCalculator(), PoWHasher.ETHASH_LIGHT);
  }

  @Parameters(name = "block {1}")
  public static Collection<Object[]> data() {

    return Arrays.asList(
        new Object[][] {
          {300005, 300006},
          {1200000, 1200001},
          {4400000, 4400001},
          {4400001, 4400002}
        });
  }

  @Test
  public void validatesValidBlocks() {
    assertThat(validationRule.validate(blockHeader, parentHeader)).isTrue();
  }

  @Test
  public void failsBlockWithZeroValuedDifficulty() {
    final BlockHeader header =
        BlockHeaderBuilder.fromHeader(blockHeader)
            .difficulty(Difficulty.ZERO)
            .blockHeaderFunctions(mainnetBlockHashFunction())
            .buildBlockHeader();
    assertThat(validationRule.validate(header, parentHeader)).isFalse();
  }

  @Test
  public void passesBlockWithOneValuedDifficulty() {
    final BlockHeaderBuilder headerBuilder =
        BlockHeaderBuilder.fromHeader(blockHeader)
            .difficulty(Difficulty.ONE)
            .blockHeaderFunctions(mainnetBlockHashFunction())
            .timestamp(1);
    final BlockHeader preHeader = headerBuilder.buildBlockHeader();
    final Hash headerHash = validationRule.hashHeader(preHeader);

    PoWSolution solution =
        PoWHasher.ETHASH_LIGHT.hash(
            preHeader.getNonce(),
            preHeader.getNumber(),
            new EpochCalculator.DefaultEpochCalculator(),
            headerHash);

    final BlockHeader header = headerBuilder.mixHash(solution.getMixHash()).buildBlockHeader();

    assertThat(validationRule.validate(header, parentHeader)).isTrue();
  }

  @Test
  public void failsWithVeryLargeDifficulty() {
    final Difficulty largeDifficulty = Difficulty.of(BigInteger.valueOf(2).pow(255));
    final BlockHeader header =
        BlockHeaderBuilder.fromHeader(blockHeader)
            .difficulty(largeDifficulty)
            .blockHeaderFunctions(mainnetBlockHashFunction())
            .buildBlockHeader();
    assertThat(validationRule.validate(header, parentHeader)).isFalse();
  }

  @Test
  public void failsWithMisMatchedMixHash() {
    final Hash updateMixHash = Hash.wrap(UInt256.fromBytes(blockHeader.getMixHash()).subtract(1L));
    final BlockHeader header =
        BlockHeaderBuilder.fromHeader(blockHeader)
            .mixHash(updateMixHash)
            .blockHeaderFunctions(mainnetBlockHashFunction())
            .buildBlockHeader();
    assertThat(validationRule.validate(header, parentHeader)).isFalse();
  }

  @Test
  public void failsWithMisMatchedNonce() {
    final long updatedNonce = blockHeader.getNonce() + 1;
    final BlockHeader header =
        BlockHeaderBuilder.fromHeader(blockHeader)
            .nonce(updatedNonce)
            .blockHeaderFunctions(mainnetBlockHashFunction())
            .buildBlockHeader();
    assertThat(validationRule.validate(header, parentHeader)).isFalse();
  }

  @Test
  public void failsWithNonEip1559BlockAfterFork() {
    final ProofOfWorkValidationRule proofOfWorkValidationRule =
        new ProofOfWorkValidationRule(
            new EpochCalculator.DefaultEpochCalculator(),
            PoWHasher.ETHASH_LIGHT,
            Optional.of(FeeMarket.london(0L)));

    final BlockHeaderBuilder headerBuilder =
        BlockHeaderBuilder.fromHeader(blockHeader)
            .difficulty(Difficulty.ONE)
            .blockHeaderFunctions(mainnetBlockHashFunction())
            .timestamp(1);
    final BlockHeader preHeader = headerBuilder.buildBlockHeader();
    final Hash headerHash = validationRule.hashHeader(preHeader);

    PoWSolution solution =
        PoWHasher.ETHASH_LIGHT.hash(
            preHeader.getNonce(),
            preHeader.getNumber(),
            new EpochCalculator.DefaultEpochCalculator(),
            headerHash);

    final BlockHeader header = headerBuilder.mixHash(solution.getMixHash()).buildBlockHeader();

    assertThat(proofOfWorkValidationRule.validate(header, parentHeader)).isFalse();
  }

  @Test
  public void failsWithEip1559BlockBeforeFork() {
    final ProofOfWorkValidationRule proofOfWorkValidationRule =
        new ProofOfWorkValidationRule(
            new EpochCalculator.DefaultEpochCalculator(), PoWHasher.ETHASH_LIGHT);

    final BlockHeaderBuilder headerBuilder =
        BlockHeaderBuilder.fromHeader(blockHeader)
            .difficulty(Difficulty.ONE)
            .baseFee(Wei.of(10L))
            .blockHeaderFunctions(mainnetBlockHashFunction())
            .timestamp(1);
    final BlockHeader preHeader = headerBuilder.buildBlockHeader();
    final Hash headerHash = validationRule.hashHeader(preHeader);

    PoWSolution solution =
        PoWHasher.ETHASH_LIGHT.hash(
            preHeader.getNonce(),
            preHeader.getNumber(),
            new EpochCalculator.DefaultEpochCalculator(),
            headerHash);

    final BlockHeader header = headerBuilder.mixHash(solution.getMixHash()).buildBlockHeader();

    assertThat(proofOfWorkValidationRule.validate(header, parentHeader)).isFalse();
  }

  private BlockHeaderFunctions mainnetBlockHashFunction() {
    final ProtocolSchedule protocolSchedule = ProtocolScheduleFixture.MAINNET;
    return ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
  }
}
