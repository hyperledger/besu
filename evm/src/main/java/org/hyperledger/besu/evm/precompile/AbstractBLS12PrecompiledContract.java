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

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.nativelib.gnark.LibGnarkEIP2537;

import java.util.Optional;
import javax.annotation.Nonnull;

import com.sun.jna.ptr.IntByReference;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Abstract BLS12 precompiled contract. */
public abstract class AbstractBLS12PrecompiledContract implements PrecompiledContract {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractBLS12PrecompiledContract.class);

  static {
    // set parallel 1 for testing.  Remove this for prod code (or set a rational limit)
    // LibGnarkEIP2537.setDegreeOfMSMParallelism(1);
  }

  /** The Discount table. */
  static final int[] G1_DISCOUNT_TABLE =
      new int[] {
        1800, 888, 955, 802, 743, 821, 750, 680, 657, 635, 612, 591, 664, 637,
        611, 585, 578, 571, 564, 557, 628, 620, 612, 604, 596, 588, 578, 570,
        562, 693, 683, 673, 670, 665, 663, 658, 655, 650, 648, 643, 640, 635,
        633, 628, 625, 620, 618, 613, 610, 605, 603, 598, 595, 590, 588, 583,
        580, 578, 573, 570, 565, 563, 558, 555, 553, 550, 548, 548, 545, 543,
        540, 540, 538, 535, 533, 533, 530, 528, 528, 525, 523, 520, 520, 518,
        515, 513, 513, 510, 508, 505, 505, 503, 500, 498, 498, 495, 493, 490,
        490, 488, 485, 483, 483, 480, 478, 478, 475, 473, 470, 470, 468, 465,
        463, 463, 460, 458, 455, 455, 453, 450, 448, 448, 445, 443, 440, 440,
        438, 435
      };

  static final int[] G2_DISCOUNT_TABLE =
      new int[] {
        1800, 1776, 1528, 1282, 1188, 1368, 1250, 1133, 1095, 1269, 1224, 1182, 1137, 1274, 1222,
        1169, 1155, 1141, 1127, 1113, 1256, 1240, 1224, 1208, 1192, 1176, 1156, 1140, 1124, 1108,
        1092, 1076, 1072, 1064, 1060, 1052, 1048, 1040, 1036, 1028, 1024, 1016, 1012, 1004, 1000,
        992, 988, 980, 976, 968, 964, 956, 952, 944, 940, 932, 928, 924, 916, 912, 904, 900, 892,
        888, 884, 880, 876, 876, 872, 868, 864, 864, 860, 856, 852, 852, 848, 844, 844, 840, 836,
        832, 832, 828, 824, 820, 820, 816, 812, 808, 808, 804, 800, 796, 796, 792, 788, 784, 784,
        780, 776, 772, 772, 768, 764, 764, 760, 756, 752, 752, 748, 744, 740, 740, 736, 732, 728,
        728, 724, 720, 716, 716, 712, 708, 704, 704, 700, 696
      };

  /** The Max discount. */
  static final int G1_MAX_DISCOUNT = 435;

  static final int G2_MAX_DISCOUNT = 696;

  private final String name;
  private final byte operationId;
  private final int inputLimit;

  /**
   * Instantiates a new Abstract BLS12 precompiled contract.
   *
   * @param name the name
   * @param operationId the operation id
   * @param inputLen the input len
   */
  AbstractBLS12PrecompiledContract(final String name, final byte operationId, final int inputLen) {
    this.name = name;
    this.operationId = operationId;
    this.inputLimit = inputLen + 1;
  }

  /**
   * Is bls12 supported on this platform
   *
   * @return true if the native library was loaded.
   */
  public static boolean isAvailable() {
    try {
      return LibGnarkEIP2537.ENABLED;
    } catch (UnsatisfiedLinkError | NoClassDefFoundError ule) {
      LOG.info("bls12-381 native precompile not available: {}", ule.getMessage());
    }
    return false;
  }

  @Override
  public String getName() {
    return name;
  }

  @Nonnull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes input, @Nonnull final MessageFrame messageFrame) {
    final byte[] result = new byte[LibGnarkEIP2537.EIP2537_PREALLOCATE_FOR_RESULT_BYTES];
    final byte[] error = new byte[LibGnarkEIP2537.EIP2537_PREALLOCATE_FOR_ERROR_BYTES];

    final IntByReference o_len =
        new IntByReference(LibGnarkEIP2537.EIP2537_PREALLOCATE_FOR_RESULT_BYTES);
    final IntByReference err_len =
        new IntByReference(LibGnarkEIP2537.EIP2537_PREALLOCATE_FOR_ERROR_BYTES);

    final int inputSize = Math.min(inputLimit, input.size());
    final int errorNo =
        LibGnarkEIP2537.eip2537_perform_operation(
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
      final String errorMessage = new String(error, 0, err_len.getValue(), UTF_8);
      messageFrame.setRevertReason(Bytes.wrap(error, 0, err_len.getValue()));
      LOG.trace("Error executing precompiled contract {}: '{}'", name, errorMessage);
      return PrecompileContractResult.halt(
          null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }
  }

  /**
   * Gets G1 MSM discount.
   *
   * @param k the k
   * @return the discount
   */
  protected int getG1Discount(final int k) {
    // `k * multiplication_cost * discount / multiplier` where `multiplier = 1000`
    // multiplication_cost and multiplier are folded into one constant as a long and placed first to
    // prevent int32 overflow
    // there was a table prepared for discount in case of k <= 128 points in the multiexponentiation
    // with a discount cup max_discount for k > 128.

    if (k >= G1_DISCOUNT_TABLE.length) {
      return G1_MAX_DISCOUNT;
    }
    return G1_DISCOUNT_TABLE[k];
  }

  /**
   * Gets G2 MSM discount.
   *
   * @param k the k
   * @return the discount
   */
  protected int getG2Discount(final int k) {
    // `k * multiplication_cost * discount / multiplier` where `multiplier = 1000`
    // multiplication_cost and multiplier are folded into one constant as a long and placed first to
    // prevent int32 overflow
    // there was a table prepared for discount in case of k <= 128 points in the multiexponentiation
    // with a discount cup max_discount for k > 128.

    if (k >= G2_DISCOUNT_TABLE.length) {
      return G2_MAX_DISCOUNT;
    }
    return G2_DISCOUNT_TABLE[k];
  }
}
