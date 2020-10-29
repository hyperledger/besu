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
package org.hyperledger.besu.ethereum.mainnet.precompiles;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

import com.sun.jna.ptr.IntByReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public abstract class AbstractAltBnPrecompiledContract extends AbstractPrecompiledContract {

  private static final Logger LOG = LogManager.getLogger();
  static boolean useNative = true;

  public static void enableNative() {
    useNative = LibEthPairings.ENABLED;
    LOG.info(
        useNative
            ? "Using LibEthPairings native alt bn128"
            : "Native alt bn128 requested but not available");
  }

  private final byte operationId;

  AbstractAltBnPrecompiledContract(
      final String name, final GasCalculator gasCalculator, final byte operationId) {
    super(name, gasCalculator);
    this.operationId = operationId;
  }

  public Bytes computeNative(final Bytes input, final MessageFrame messageFrame) {
    final byte[] result = new byte[LibEthPairings.EIP196_PREALLOCATE_FOR_RESULT_BYTES];
    final byte[] error = new byte[LibEthPairings.EIP2537_PREALLOCATE_FOR_ERROR_BYTES];

    final IntByReference o_len =
        new IntByReference(LibEthPairings.EIP196_PREALLOCATE_FOR_RESULT_BYTES);
    final IntByReference err_len =
        new IntByReference(LibEthPairings.EIP2537_PREALLOCATE_FOR_ERROR_BYTES);
    final int errorNo =
        LibEthPairings.eip196_perform_operation(
            operationId, input.toArrayUnsafe(), input.size(), result, o_len, error, err_len);
    if (errorNo == 0) {
      return Bytes.wrap(result, 0, o_len.getValue());
    } else {
      final String errorString = new String(error, 0, err_len.getValue(), UTF_8);
      messageFrame.setRevertReason(Bytes.wrap(error, 0, err_len.getValue()));
      LOG.trace("Error executing precompiled contract {}: '{}'", getName(), errorString);
      return null;
    }
  }
}
