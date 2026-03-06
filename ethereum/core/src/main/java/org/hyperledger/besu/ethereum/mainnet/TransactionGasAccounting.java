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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates the gas accounting logic for transaction processing, including EIP-8037
 * multidimensional gas.
 *
 * <p>This extracts the complex gas computation from {@link MainnetTransactionProcessor} into a
 * testable, stateless helper. Uses a builder to prevent parameter ordering mistakes — the many long
 * fields are easily confused without named setters.
 *
 * <p>Usage: {@code TransactionGasAccounting.builder().txGasLimit(...).remainingGas(...)...
 * .build().calculate()}
 */
public class TransactionGasAccounting {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionGasAccounting.class);

  /** Result of the gas accounting calculation. */
  public record GasResult(long effectiveStateGas, long gasUsedByTransaction, long usedGas) {}

  private final long txGasLimit;
  private final long remainingGas;
  private final long stateGasReservoir;
  private final long stateGasUsed;
  private final long initialFrameStateGasSpill;
  private final long stateGasSpillBurned;
  private final long regularGasCollisionBurned;
  private final long refundedGas;
  private final long floorCost;
  private final boolean regularGasLimitExceeded;

  private TransactionGasAccounting(
      final long txGasLimit,
      final long remainingGas,
      final long stateGasReservoir,
      final long stateGasUsed,
      final long initialFrameStateGasSpill,
      final long stateGasSpillBurned,
      final long regularGasCollisionBurned,
      final long refundedGas,
      final long floorCost,
      final boolean regularGasLimitExceeded) {
    this.txGasLimit = txGasLimit;
    this.remainingGas = remainingGas;
    this.stateGasReservoir = stateGasReservoir;
    this.stateGasUsed = stateGasUsed;
    this.initialFrameStateGasSpill = initialFrameStateGasSpill;
    this.stateGasSpillBurned = stateGasSpillBurned;
    this.regularGasCollisionBurned = regularGasCollisionBurned;
    this.refundedGas = refundedGas;
    this.floorCost = floorCost;
    this.regularGasLimitExceeded = regularGasLimitExceeded;
  }

  /**
   * Calculate gas accounting for a completed transaction.
   *
   * <p>Two paths:
   *
   * <ul>
   *   <li><b>regularGasLimitExceeded=true:</b> All gas consumed. effectiveStateGas = stateGasUsed +
   *       initialFrameStateGasSpill.
   *   <li><b>regularGasLimitExceeded=false:</b> Computes executionGas, stateGas, regularGas with
   *       double-counting avoidance. Floor cost applies to regularGas only.
   * </ul>
   *
   * @return the gas result containing effectiveStateGas, gasUsedByTransaction, and usedGas
   */
  public GasResult calculate() {
    if (regularGasLimitExceeded) {
      final long effectiveStateGas = stateGasUsed + initialFrameStateGasSpill;
      return new GasResult(effectiveStateGas, txGasLimit, txGasLimit);
    }

    // EIP-8037: Include leftover reservoir in remaining gas for execution gas calculation
    final long executionGas = txGasLimit - remainingGas - stateGasReservoir;
    // EIP-8037: Floor applies to regular gas only, not total gas.
    // Pre-Amsterdam: stateGasUsed=0, spillBurned=0, collisionBurned=0 — identical.
    // stateGasSpillBurned: state gas that spilled into gasRemaining from reverted frames.
    // For child frame reverts: not metered as regular or state gas (invisible to block).
    // For initial frame revert/halt: counts as state gas for block accounting, because the
    // transaction's state gas consumption is final regardless of execution outcome.
    // regularGasCollisionBurned: gas burned by CREATE children that halted before executing
    // code (address collision); excluded from block regular gas but still charged as fees.
    final long stateGas = stateGasUsed + initialFrameStateGasSpill;
    // initialFrameStateGasSpill is already included in spillBurned AND stateGas,
    // so subtract it from spillBurned to avoid double-counting.
    final long regularGas =
        executionGas
            - stateGas
            - (stateGasSpillBurned - initialFrameStateGasSpill)
            - regularGasCollisionBurned;
    if (regularGas < 0) {
      LOG.warn(
          "Negative regularGas={} (executionGas={}, stateGas={}, spillBurned={}, initialSpill={}, collisionBurned={})",
          regularGas,
          executionGas,
          stateGas,
          stateGasSpillBurned,
          initialFrameStateGasSpill,
          regularGasCollisionBurned);
    }
    final long gasUsedByTransaction = Math.max(regularGas, floorCost) + stateGas;
    final long usedGas = txGasLimit - refundedGas;
    return new GasResult(stateGas, gasUsedByTransaction, usedGas);
  }

  /** Creates a new builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link TransactionGasAccounting}. */
  public static class Builder {
    private Long txGasLimit;
    private Long remainingGas;
    private Long stateGasReservoir;
    private Long stateGasUsed;
    private Long initialFrameStateGasSpill;
    private Long stateGasSpillBurned;
    private Long regularGasCollisionBurned;
    private Long refundedGas;
    private Long floorCost;
    private Boolean regularGasLimitExceeded;

    private Builder() {}

    /** The transaction gas limit. */
    public Builder txGasLimit(final long txGasLimit) {
      this.txGasLimit = txGasLimit;
      return this;
    }

    /** Gas remaining in the initial frame after execution. */
    public Builder remainingGas(final long remainingGas) {
      this.remainingGas = remainingGas;
      return this;
    }

    /** Leftover state gas reservoir in the initial frame. */
    public Builder stateGasReservoir(final long stateGasReservoir) {
      this.stateGasReservoir = stateGasReservoir;
      return this;
    }

    /** State gas consumed by the initial frame. */
    public Builder stateGasUsed(final long stateGasUsed) {
      this.stateGasUsed = stateGasUsed;
      return this;
    }

    /** State gas spilled from the initial frame's own revert/halt. */
    public Builder initialFrameStateGasSpill(final long initialFrameStateGasSpill) {
      this.initialFrameStateGasSpill = initialFrameStateGasSpill;
      return this;
    }

    /** Total state gas spilled into gasRemaining from reverted frames. */
    public Builder stateGasSpillBurned(final long stateGasSpillBurned) {
      this.stateGasSpillBurned = stateGasSpillBurned;
      return this;
    }

    /** Gas burned by CREATE children that halted before executing code (address collision). */
    public Builder regularGasCollisionBurned(final long regularGasCollisionBurned) {
      this.regularGasCollisionBurned = regularGasCollisionBurned;
      return this;
    }

    /** Gas refunded to the sender. */
    public Builder refundedGas(final long refundedGas) {
      this.refundedGas = refundedGas;
      return this;
    }

    /** Transaction floor cost (EIP-7623), 0 for pre-Prague. */
    public Builder floorCost(final long floorCost) {
      this.floorCost = floorCost;
      return this;
    }

    /** Whether the regular gas limit was exceeded (EIP-8037). */
    public Builder regularGasLimitExceeded(final boolean regularGasLimitExceeded) {
      this.regularGasLimitExceeded = regularGasLimitExceeded;
      return this;
    }

    /** Builds an immutable {@link TransactionGasAccounting} instance. */
    public TransactionGasAccounting build() {
      requireNonNull(txGasLimit, "txGasLimit");
      requireNonNull(remainingGas, "remainingGas");
      requireNonNull(stateGasReservoir, "stateGasReservoir");
      requireNonNull(stateGasUsed, "stateGasUsed");
      requireNonNull(initialFrameStateGasSpill, "initialFrameStateGasSpill");
      requireNonNull(stateGasSpillBurned, "stateGasSpillBurned");
      requireNonNull(regularGasCollisionBurned, "regularGasCollisionBurned");
      requireNonNull(refundedGas, "refundedGas");
      requireNonNull(floorCost, "floorCost");
      requireNonNull(regularGasLimitExceeded, "regularGasLimitExceeded");
      return new TransactionGasAccounting(
          txGasLimit,
          remainingGas,
          stateGasReservoir,
          stateGasUsed,
          initialFrameStateGasSpill,
          stateGasSpillBurned,
          regularGasCollisionBurned,
          refundedGas,
          floorCost,
          regularGasLimitExceeded);
    }

    private static void requireNonNull(final Object value, final String name) {
      if (value == null) {
        throw new IllegalStateException(name + " must be set");
      }
    }
  }
}
