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
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;

/**
 * A pre-compiled contract.
 *
 * <p>It corresponds to one of the function defined in Appendix E of the Yellow Paper (rev.
 * a91c29c).
 */
public interface PrecompiledContract {

  /**
   * Returns the pre-compiled contract name.
   *
   * @return the pre-compiled contract name
   */
  String getName();

  /**
   * Gas requirement for the contract.
   *
   * @param input the input for the pre-compiled contract (on which the gas requirement may or may
   *     not depend).
   * @return the gas requirement (cost) for the pre-compiled contract.
   */
  long gasRequirement(Bytes input);

  /**
   * Executes the pre-compiled contract.
   *
   * @param input the input for the pre-compiled contract.
   * @param messageFrame context for this message
   * @return the output of the pre-compiled contract.
   */
  @SuppressWarnings("deprecation")
  @Nonnull
  default PrecompileContractResult computePrecompile(
      final Bytes input, @Nonnull final MessageFrame messageFrame) {
    final Bytes result = compute(input, messageFrame);
    if (result == null) {
      return PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE));
    } else {
      return PrecompileContractResult.success(result);
    }
  }

  /**
   * Executes the pre-compiled contract.
   *
   * @param input the input for the pre-compiled contract.
   * @param messageFrame context for this message
   * @return the output of the pre-compiled contract.
   * @deprecated Migrate to use {@link #computePrecompile(Bytes, MessageFrame)}.
   */
  @Deprecated(since = "22.1.2")
  default Bytes compute(final Bytes input, final @Nonnull MessageFrame messageFrame) {
    return computePrecompile(input, messageFrame).getOutput();
  }

  /** The Precompile contract result. */
  class PrecompileContractResult {
    private final Bytes output;
    private final boolean refundGas;
    private final MessageFrame.State state;
    private final Optional<ExceptionalHaltReason> haltReason;

    /**
     * Encapsulated result of precompiled contract.
     *
     * @param output output if successful
     * @param refundGas Should we charge the gasRequirement?
     * @param state state of the EVM after execution (for format errors this would be
     *     ExceptionalHalt)
     * @param haltReason the exceptional halt reason
     */
    // TOO JDK17 use a record
    public PrecompileContractResult(
        final Bytes output,
        final boolean refundGas,
        final MessageFrame.State state,
        final Optional<ExceptionalHaltReason> haltReason) {
      this.output = output;
      this.refundGas = refundGas;
      this.state = state;
      this.haltReason = haltReason;
    }

    /**
     * precompile contract result with Success state.
     *
     * @param output the output
     * @return the precompile contract result
     */
    public static PrecompileContractResult success(final Bytes output) {
      return new PrecompileContractResult(
          output, false, MessageFrame.State.COMPLETED_SUCCESS, Optional.empty());
    }

    /**
     * precompile contract result with revert state.
     *
     * @param output the output
     * @return the precompile contract result
     */
    public static PrecompileContractResult revert(final Bytes output) {
      return new PrecompileContractResult(
          output, false, MessageFrame.State.REVERT, Optional.empty());
    }

    /**
     * precompile contract result with Halt state.
     *
     * @param output the output
     * @param haltReason the halt reason
     * @return the precompile contract result
     */
    public static PrecompileContractResult halt(
        final Bytes output, final Optional<ExceptionalHaltReason> haltReason) {
      if (haltReason.isEmpty()) {
        throw new IllegalArgumentException("Halt reason cannot be empty");
      }
      return new PrecompileContractResult(
          output, false, MessageFrame.State.EXCEPTIONAL_HALT, haltReason);
    }

    /**
     * Gets output.
     *
     * @return the output
     */
    public Bytes getOutput() {
      return output;
    }

    /**
     * Is refund gas.
     *
     * @return the boolean
     */
    public boolean isRefundGas() {
      return refundGas;
    }

    /**
     * Gets state.
     *
     * @return the state
     */
    public MessageFrame.State getState() {
      return state;
    }

    /**
     * Gets halt reason.
     *
     * @return the halt reason
     */
    public Optional<ExceptionalHaltReason> getHaltReason() {
      return haltReason;
    }
  }
}
