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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.config.BlobSchedule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;

import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CancunTargetingGasLimitCalculatorTest {
  private static final long TARGET_BLOB_GAS_PER_BLOCK_CANCUN = 0x60000;
  private final CancunTargetingGasLimitCalculator cancunTargetingGasLimitCalculator =
      new CancunTargetingGasLimitCalculator(
          0L, FeeMarket.cancunDefault(0L, Optional.empty()), new CancunGasCalculator());

  @ParameterizedTest(name = "{index} - parent gas {0}, used gas {1}, new excess {2}")
  @MethodSource("cancunBlobGasses")
  public void shouldCalculateCancunExcessBlobGasCorrectly(
      final long parentExcess, final long used, final long expected) {
    final long usedBlobGas = new CancunGasCalculator().blobGasCost(used);
    assertThat(cancunTargetingGasLimitCalculator.computeExcessBlobGas(parentExcess, usedBlobGas))
        .isEqualTo(expected);
  }

  Iterable<Arguments> cancunBlobGasses() {
    long targetGasPerBlock = TARGET_BLOB_GAS_PER_BLOCK_CANCUN;
    return List.of(
        Arguments.of(0L, 0L, 0L),
        Arguments.of(targetGasPerBlock, 0L, 0L),
        Arguments.of(0L, 3, 0L),
        Arguments.of(1, 3, 1),
        Arguments.of(targetGasPerBlock, 1, cancunTargetingGasLimitCalculator.getBlobGasPerBlob()),
        Arguments.of(targetGasPerBlock, 3, targetGasPerBlock));
  }

  @Test
  void currentBlobGasLimitIs6Blobs() {
    var cancunTargetingGasLimitCalculator =
        new org.hyperledger.besu.ethereum.mainnet.CancunTargetingGasLimitCalculator(
            0L, FeeMarket.cancunDefault(0L, Optional.empty()), new CancunGasCalculator());
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(0xC0000);
  }

  @Test
  void shouldUseCancunCalculatorBlobGasPerBlob() {
    // should use CancunGasCalculator's blob gas per blob to calculate the gas limit
    final long blobGasPerBlob = new CancunGasCalculator().getBlobGasPerBlob();
    assertThat(blobGasPerBlob).isEqualTo(131072);
    int maxBlobs = 10;
    int targetBlobs = 3;
    var cancunTargetingGasLimitCalculator =
        new CancunTargetingGasLimitCalculator(
            0L,
            FeeMarket.cancunDefault(0L, Optional.empty()),
            new CancunGasCalculator(),
            maxBlobs,
            targetBlobs);
    // if maxBlobs = 10, then the gas limit would be 131072 * 10 = 1310720
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit())
        .isEqualTo(blobGasPerBlob * maxBlobs);
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(1310720);
  }

  @Test
  void shouldUseFutureForkCalculatorBlobGasPerBlob() {
    // if a future fork changes the blob gas per blob
    // even if we still use the CancunTargetingGasLimitCalculator
    // it should use TestFutureForkCalculator's blob gas per blob to calculate the blob gas limit
    final long blobGasPerBlob = new TestFutureGasCalculator().getBlobGasPerBlob();
    assertThat(blobGasPerBlob).isEqualTo(262144);
    int maxBlobs = 10;
    int targetBlobs = 3;
    var cancunTargetingGasLimitCalculator =
        new CancunTargetingGasLimitCalculator(
            0L,
            FeeMarket.cancunDefault(0L, Optional.empty()),
            new TestFutureGasCalculator(),
            maxBlobs,
            targetBlobs);
    // if maxBlobs = 10, then the gas limit would be 262144 * 10 = 2621440
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit())
        .isEqualTo(blobGasPerBlob * maxBlobs);
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(2621440);
  }

  private final PragueGasCalculator pragueGasCalculator = new PragueGasCalculator();
  // CancunTargetingGasLimitCalculator with Prague numbers
  private final CancunTargetingGasLimitCalculator pragueGasLimitCalculator =
      new CancunTargetingGasLimitCalculator(
          0L,
          FeeMarket.cancunDefault(0L, Optional.empty()),
          pragueGasCalculator,
          BlobSchedule.PRAGUE_DEFAULT.getMax(),
          BlobSchedule.PRAGUE_DEFAULT.getTarget());

  private static final long TARGET_BLOB_GAS_PER_BLOCK_PRAGUE = 0xC0000;

  @ParameterizedTest(name = "{index} - parent gas {0}, used gas {1}, blob target {2}")
  @MethodSource("pragueBlobGasses")
  public void shouldCalculatePragueExcessBlobGasCorrectly(
      final long parentExcess, final long used, final long expected) {
    final long usedBlobGas = pragueGasCalculator.blobGasCost(used);
    assertThat(pragueGasLimitCalculator.computeExcessBlobGas(parentExcess, usedBlobGas))
        .isEqualTo(expected);
  }

  Iterable<Arguments> pragueBlobGasses() {
    long sixBlobTargetGas = TARGET_BLOB_GAS_PER_BLOCK_PRAGUE;
    long newTargetCount = 6;

    return List.of(
        // New target count
        Arguments.of(0L, 0L, 0L),
        Arguments.of(sixBlobTargetGas, 0L, 0L),
        Arguments.of(newTargetCount, 0L, 0L),
        Arguments.of(0L, newTargetCount, 0L),
        Arguments.of(1L, newTargetCount, 1L),
        Arguments.of(
            pragueGasCalculator.blobGasCost(newTargetCount),
            1L,
            pragueGasLimitCalculator.getBlobGasPerBlob()),
        Arguments.of(sixBlobTargetGas, newTargetCount, sixBlobTargetGas));
  }

  @Test
  void shouldCalculateCorrectlyPragueBlobGasPerBlob() {
    // should use PragueGasCalculator's blob gas per blob to calculate the gas limit
    final long blobGasPerBlob = new PragueGasCalculator().getBlobGasPerBlob();
    assertThat(blobGasPerBlob).isEqualTo(131072); // same as Cancun
    int maxBlobs = 10;
    int targetBlobs = 3;
    var pragueTargetingGasLimitCalculator =
        new CancunTargetingGasLimitCalculator(
            0L,
            FeeMarket.cancun(0L, Optional.empty(), BlobSchedule.PRAGUE_DEFAULT),
            new PragueGasCalculator(),
            maxBlobs,
            targetBlobs);
    // if maxBlobs = 10, then the gas limit would be 131072 * 10 = 1310720
    assertThat(pragueTargetingGasLimitCalculator.currentBlobGasLimit())
        .isEqualTo(blobGasPerBlob * maxBlobs);
    assertThat(pragueTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(1310720);
  }

  long nineBlobTargetGas = TARGET_BLOB_GAS_PER_BLOCK_OSAKA;
  int newTargetCount = 9;
  public static final OsakaGasCalculator osakaGasCalculator = new OsakaGasCalculator();
  // CancunTargetingGasLimitCalculator with Osaka numbers
  private final CancunTargetingGasLimitCalculator osakaGasLimitCalculator =
      new CancunTargetingGasLimitCalculator(
          0L,
          FeeMarket.cancunDefault(0L, Optional.empty()),
          osakaGasCalculator,
          BlobSchedule.PRAGUE_DEFAULT.getMax(),
          newTargetCount);

  private static final long TARGET_BLOB_GAS_PER_BLOCK_OSAKA = 0x120000;

  @ParameterizedTest(name = "{index} - parent gas {0}, used gas {1}, blob target {2}")
  @MethodSource("osakaBlobGasses")
  public void shouldCalculateOsakaExcessBlobGasCorrectly(
      final long parentExcess, final long used, final long expected) {
    final long usedBlobGas = osakaGasCalculator.blobGasCost(used);
    assertThat(osakaGasLimitCalculator.computeExcessBlobGas(parentExcess, usedBlobGas))
        .isEqualTo(expected);
  }

  Iterable<Arguments> osakaBlobGasses() {

    return List.of(
        // New target count
        Arguments.of(0L, 0L, 0L),
        Arguments.of(nineBlobTargetGas, 0L, 0L),
        Arguments.of(newTargetCount, 0L, 0L),
        Arguments.of(0L, newTargetCount, 0L),
        Arguments.of(1L, newTargetCount, 1L),
        Arguments.of(
            osakaGasCalculator.blobGasCost(newTargetCount),
            1L,
            osakaGasLimitCalculator.getBlobGasPerBlob()),
        Arguments.of(nineBlobTargetGas, newTargetCount, nineBlobTargetGas));
  }

  /**
   * This class is used to test the CancunTargetingGasLimitCalculator with a hypothetical future
   * blob gas
   */
  private static class TestFutureGasCalculator extends CancunGasCalculator {
    private static final long TEST_BLOB_GAS_PER_BLOB_FUTURE = 262144;

    public TestFutureGasCalculator() {
      super(0);
    }

    @Override
    public long getBlobGasPerBlob() {
      return TEST_BLOB_GAS_PER_BLOB_FUTURE;
    }
  }

  @Test
  void dryRunDetector() {
    Assertions.assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
