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

import org.hyperledger.besu.ethereum.mainnet.PrecompiledContract;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

import com.sun.jna.ptr.IntByReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public abstract class AbstractBLS12PrecompiledContract implements PrecompiledContract {

  private static final Logger LOG = LogManager.getLogger();

  static final int[] DISCOUNT_TABLE =
      new int[] {
        -1, 1_200, 888, 764, 641, 594, 547, 500, 453, 438, 423, 408, 394, 379, 364, 349,
        334, 330, 326, 322, 318, 314, 310, 306, 302, 298, 294, 289, 285, 281, 277, 273,
        269, 268, 266, 265, 263, 262, 260, 259, 257, 256, 254, 253, 251, 250, 248, 247,
        245, 244, 242, 241, 239, 238, 236, 235, 233, 232, 231, 229, 228, 226, 225, 223,
        222, 221, 220, 219, 219, 218, 217, 216, 216, 215, 214, 213, 213, 212, 211, 211,
        210, 209, 208, 208, 207, 206, 205, 205, 204, 203, 202, 202, 201, 200, 199, 199,
        198, 197, 196, 196, 195, 194, 193, 193, 192, 191, 191, 190, 189, 188, 188, 187,
        186, 185, 185, 184, 183, 182, 182, 181, 180, 179, 179, 178, 177, 176, 176, 175,
        174
      };

  static final int MAX_DISCOUNT = 174;

  private final String name;
  private final byte operationId;

  AbstractBLS12PrecompiledContract(final String name, final byte operationId) {
    this.name = name;
    this.operationId = operationId;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Bytes compute(final Bytes input, final MessageFrame messageFrame) {
    final byte[] result = new byte[LibEthPairings.EIP2537_PREALLOCATE_FOR_RESULT_BYTES];
    final byte[] error = new byte[LibEthPairings.EIP2537_PREALLOCATE_FOR_ERROR_BYTES];

    final IntByReference o_len =
        new IntByReference(LibEthPairings.EIP2537_PREALLOCATE_FOR_RESULT_BYTES);
    final IntByReference err_len =
        new IntByReference(LibEthPairings.EIP2537_PREALLOCATE_FOR_ERROR_BYTES);
    final int errorNo =
        LibEthPairings.eip2537_perform_operation(
            operationId, input.toArrayUnsafe(), input.size(), result, o_len, error, err_len);
    if (errorNo == 0) {
      return Bytes.wrap(result, 0, o_len.getValue());
    } else {
      messageFrame.setRevertReason(Bytes.wrap(error, 0, err_len.getValue()));
      LOG.trace(
          "Error executing precompiled contract {}: '{}'",
          name,
          new String(error, 0, err_len.getValue(), UTF_8));
      return null;
    }
  }

  protected int getDiscount(final int k) {
    // `k * multiplication_cost * discount / multiplier` where `multiplier = 1000`
    // multiplication_cost and multiplier are folded into one constant as a long and placed first to
    // prevent int32 overflow
    // there was a table prepared for discount in case of k <= 128 points in the multiexponentiation
    // with a discount cup max_discount for k > 128.

    if (k >= DISCOUNT_TABLE.length) {
      return MAX_DISCOUNT;
    }
    return DISCOUNT_TABLE[k];
  }
}
