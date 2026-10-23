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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class FrameTransactionGasTest {

  private static final Address SENDER =
      Address.fromHexString("0x1111111111111111111111111111111111111111");
  private static final Address OTHER =
      Address.fromHexString("0x2222222222222222222222222222222222222222");

  /** rlp([0]) ++ rlp(0) = 0xc180 ++ 0x80: three non-zero bytes. */
  private static final Bytes LEGACY_NONCE_CALLDATA = Bytes.fromHexString("0xc18080");

  /** Three non-zero bytes: 3 * 4 tokens at 4 gas each. */
  private static final long LEGACY_NONCE_CALLDATA_COST = 48L;

  /** Three bytes: 3 * 4 floor tokens. */
  private static final long LEGACY_NONCE_FLOOR_TOKENS = 12L;

  private static Frame frame(
      final Optional<Address> target,
      final long executionGas,
      final long stateGas,
      final Wei value,
      final Bytes data) {
    return new Frame(Frame.MODE_SENDER, 0, target, executionGas, stateGas, value, data);
  }

  private static Frame emptyFrame() {
    return frame(Optional.empty(), 0L, 0L, Wei.ZERO, Bytes.EMPTY);
  }

  @Test
  void signatureGasPerScheme() {
    assertThat(FrameTransactionGas.signatureGas(FrameSignature.SCHEME_ARBITRARY)).isEqualTo(100L);
    assertThat(FrameTransactionGas.signatureGas(FrameSignature.SCHEME_SECP256K1)).isEqualTo(2_800L);
    assertThat(FrameTransactionGas.signatureGas(FrameSignature.SCHEME_P256)).isEqualTo(6_700L);
    assertThat(FrameTransactionGas.signatureGas(3)).isEqualTo(-1L);
    assertThat(FrameTransactionGas.signatureGas(-1)).isEqualTo(-1L);
  }

  @Test
  void nonceCalldataEncodesKeysThenSequence() {
    assertThat(FrameTransactionGas.nonceCalldata(List.of(UInt256.ZERO), 0L))
        .isEqualTo(LEGACY_NONCE_CALLDATA);
    assertThat(FrameTransactionGas.nonceCalldata(List.of(UInt256.ONE, UInt256.valueOf(2)), 5L))
        .isEqualTo(Bytes.fromHexString("0xc2010205"));
  }

  @Test
  void nonceCalldataOfATransactionUsesItsKeysAndSequence() {
    final Transaction transaction =
        Transaction.builder()
            .type(TransactionType.FRAME)
            .chainId(BigInteger.ONE)
            .nonce(5)
            .sender(SENDER)
            .nonceKeys(List.of(UInt256.ONE, UInt256.valueOf(2)))
            .frames(List.of(emptyFrame()))
            .maxPriorityFeePerGas(Wei.ONE)
            .maxFeePerGas(Wei.ONE)
            .build();
    assertThat(FrameTransactionGas.nonceCalldata(transaction))
        .isEqualTo(Bytes.fromHexString("0xc2010205"));
  }

  @Test
  void intrinsicGasOfASingleEmptyFrame() {
    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(emptyFrame()), List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(12_000L + LEGACY_NONCE_CALLDATA_COST + 475L);
  }

  @Test
  void intrinsicGasChargesFrameDataAsTokens() {
    // One zero byte (1 token) and one non-zero byte (4 tokens), at 4 gas per token.
    final Frame withData = frame(Optional.empty(), 0L, 0L, Wei.ZERO, Bytes.fromHexString("0x00ff"));
    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(withData), List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(12_000L + LEGACY_NONCE_CALLDATA_COST + 475L + 20L);
  }

  @Test
  void valueCostAppliesOnlyToAnotherTarget() {
    final long base = 12_000L + LEGACY_NONCE_CALLDATA_COST + 475L;
    final Frame toOther = frame(Optional.of(OTHER), 0L, 0L, Wei.ONE, Bytes.EMPTY);
    final Frame toSender = frame(Optional.of(SENDER), 0L, 0L, Wei.ONE, Bytes.EMPTY);
    final Frame noTarget = frame(Optional.empty(), 0L, 0L, Wei.ONE, Bytes.EMPTY);
    final Frame zeroValue = frame(Optional.of(OTHER), 0L, 0L, Wei.ZERO, Bytes.EMPTY);

    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(toOther), List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(base + 6_000L);
    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(toSender), List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(base);
    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(noTarget), List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(base);
    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(zeroValue), List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(base);
  }

  @Test
  void intrinsicGasChargesEachFrame() {
    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(emptyFrame(), emptyFrame(), emptyFrame()),
                List.of(),
                SENDER,
                LEGACY_NONCE_CALLDATA))
        .isEqualTo(12_000L + LEGACY_NONCE_CALLDATA_COST + 3 * 475L);
  }

  @Test
  void intrinsicGasChargesSignatureBytesAndVerification() {
    // 65 non-zero signature bytes at 16 gas each, plus the secp256k1 verification cost.
    final FrameSignature secp256k1 =
        new FrameSignature(
            FrameSignature.SCHEME_SECP256K1, Bytes.EMPTY, Bytes.EMPTY, Bytes.repeat((byte) 7, 65));
    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(emptyFrame()), List.of(secp256k1), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(12_000L + LEGACY_NONCE_CALLDATA_COST + 475L + 65 * 16L + 2_800L);

    // The signer and msg fields are charged as calldata too.
    final FrameSignature arbitrary =
        new FrameSignature(
            FrameSignature.SCHEME_ARBITRARY,
            OTHER.getBytes(),
            Bytes.repeat((byte) 0, 32),
            Bytes.of(1));
    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(emptyFrame()), List.of(arbitrary), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(12_000L + LEGACY_NONCE_CALLDATA_COST + 475L + 20 * 16L + 32 * 4L + 16L + 100L);
  }

  @Test
  void anUnknownSignatureSchemeSaturatesTheIntrinsicGas() {
    final FrameSignature unknown = new FrameSignature(7, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY);
    assertThat(
            FrameTransactionGas.intrinsicGas(
                List.of(emptyFrame()), List.of(unknown), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(Long.MAX_VALUE);
    assertThat(
            FrameTransactionGas.calldataFloorGas(
                List.of(emptyFrame()), List.of(unknown), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(Long.MAX_VALUE);
    assertThat(
            FrameTransactionGas.maxGas(
                List.of(emptyFrame()), List.of(unknown), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void calldataFloorPricesEveryByteAtTheFloorRate() {
    // Floor: 4 tokens per byte, zero or not, at 16 gas per token, on top of the fixed costs.
    final Frame withData = frame(Optional.empty(), 0L, 0L, Wei.ZERO, Bytes.fromHexString("0x00ff"));
    assertThat(
            FrameTransactionGas.calldataFloorGas(
                List.of(withData), List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(12_000L + 475L + 16L * (LEGACY_NONCE_FLOOR_TOKENS + 2 * 4L));

    final FrameSignature secp256k1 =
        new FrameSignature(
            FrameSignature.SCHEME_SECP256K1, Bytes.EMPTY, Bytes.EMPTY, Bytes.repeat((byte) 7, 65));
    final Frame toOther = frame(Optional.of(OTHER), 0L, 0L, Wei.ONE, Bytes.EMPTY);
    assertThat(
            FrameTransactionGas.calldataFloorGas(
                List.of(toOther), List.of(secp256k1), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(12_000L + 475L + 6_000L + 2_800L + 16L * (LEGACY_NONCE_FLOOR_TOKENS + 65 * 4L));
  }

  @Test
  void totalGasLimitsSumTheFrames() {
    final List<Frame> frames =
        List.of(
            frame(Optional.empty(), 100L, 7L, Wei.ZERO, Bytes.EMPTY),
            frame(Optional.empty(), 250L, 11L, Wei.ZERO, Bytes.EMPTY));
    assertThat(FrameTransactionGas.totalExecutionGasLimit(frames)).isEqualTo(350L);
    assertThat(FrameTransactionGas.totalStateGasLimit(frames)).isEqualTo(18L);
    assertThat(FrameTransactionGas.totalExecutionGasLimit(List.of())).isZero();
  }

  @Test
  void totalGasLimitsSaturate() {
    final List<Frame> frames =
        List.of(
            frame(Optional.empty(), Long.MAX_VALUE, Long.MAX_VALUE, Wei.ZERO, Bytes.EMPTY),
            frame(Optional.empty(), 1L, 1L, Wei.ZERO, Bytes.EMPTY));
    assertThat(FrameTransactionGas.totalExecutionGasLimit(frames)).isEqualTo(Long.MAX_VALUE);
    assertThat(FrameTransactionGas.totalStateGasLimit(frames)).isEqualTo(Long.MAX_VALUE);
    assertThat(FrameTransactionGas.maxGas(frames, List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void maxGasIsTheStandardLimitWhenExecutionDominates() {
    final List<Frame> frames =
        List.of(frame(Optional.empty(), 50_000L, 10_000L, Wei.ZERO, Bytes.EMPTY));
    final long intrinsic =
        FrameTransactionGas.intrinsicGas(frames, List.of(), SENDER, LEGACY_NONCE_CALLDATA);
    assertThat(FrameTransactionGas.maxGas(frames, List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(intrinsic + 50_000L + 10_000L);
  }

  @Test
  void maxGasIsTheFloorPlusStateGasWhenCalldataDominates() {
    // 1,000 zero bytes cost 4,000 gas of intrinsic calldata but 64,000 on the floor.
    final List<Frame> frames =
        List.of(frame(Optional.empty(), 0L, 10_000L, Wei.ZERO, Bytes.repeat((byte) 0, 1_000)));
    final long floor =
        FrameTransactionGas.calldataFloorGas(frames, List.of(), SENDER, LEGACY_NONCE_CALLDATA);
    assertThat(floor)
        .isGreaterThan(
            FrameTransactionGas.intrinsicGas(frames, List.of(), SENDER, LEGACY_NONCE_CALLDATA));
    assertThat(FrameTransactionGas.maxGas(frames, List.of(), SENDER, LEGACY_NONCE_CALLDATA))
        .isEqualTo(floor + 10_000L);
  }
}
