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
 *
 */
package org.hyperledger.besu.evm.precompile;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

import java.util.Optional;
import javax.annotation.Nonnull;

import com.sun.jna.ptr.IntByReference;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAltBnPrecompiledContract extends AbstractPrecompiledContract {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractAltBnPrecompiledContract.class);

  // use the native library implementation, if it is available
  static boolean useNative = LibEthPairings.ENABLED;

  public static void disableNative() {
    useNative = false;
  }

  public static boolean isNative() {
    return useNative;
  }

  private final byte operationId;
  private final int inputLen;

  AbstractAltBnPrecompiledContract(
      final String name,
      final GasCalculator gasCalculator,
      final byte operationId,
      final int inputLen) {
    super(name, gasCalculator);
    this.operationId = operationId;
    this.inputLen = inputLen + 1;

    if (!LibEthPairings.ENABLED) {
      LOG.info("Native alt bn128 not available");
    }
  }

  @Nonnull
  public PrecompileContractResult computeNative(
      final @Nonnull Bytes input, final MessageFrame messageFrame) {
    final byte[] result = new byte[LibEthPairings.EIP196_PREALLOCATE_FOR_RESULT_BYTES];
    final byte[] error = new byte[LibEthPairings.EIP2537_PREALLOCATE_FOR_ERROR_BYTES];

    final IntByReference o_len =
        new IntByReference(LibEthPairings.EIP196_PREALLOCATE_FOR_RESULT_BYTES);
    final IntByReference err_len =
        new IntByReference(LibEthPairings.EIP2537_PREALLOCATE_FOR_ERROR_BYTES);
    final int inputSize = Math.min(inputLen, input.size());
    final int errorNo =
        LibEthPairings.eip196_perform_operation(
            operationId,
            input.slice(0, inputSize).toArrayUnsafe(),
            inputSize,
            result,
            o_len,
            error,
            err_len);
    if (errorNo == 0) {
      return PrecompileContractResult.success(Bytes.wrap(result, 0, o_len.getValue()));
    } else {
      final String errorString = new String(error, 0, err_len.getValue(), UTF_8);
      messageFrame.setRevertReason(Bytes.wrap(error, 0, err_len.getValue()));
      LOG.trace("Error executing precompiled contract {}: '{}'", getName(), errorString);
      return PrecompileContractResult.halt(
          null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }
  }
}
