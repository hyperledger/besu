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
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;

/** The KZGPointEval precompile contract. */
public class KZGPointEvalPrecompiledContract implements PrecompiledContract {

  @Override
  public String getName() {
    return "KZGPointEval";
  }

  @Override
  public long gasRequirement(final Bytes input) {
    // As defined in EIP-4844
    return 50000;
  }

  @NotNull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes input, @NotNull final MessageFrame messageFrame) {

    if (input.size() != 192) {
      return new PrecompileContractResult(
          Bytes.EMPTY,
          false,
          MessageFrame.State.COMPLETED_FAILED,
          Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }
    Bytes z = input.slice(32, 32);
    Bytes y = input.slice(64, 32);
    Bytes commitment = input.slice(96, 48);
    Bytes proof = input.slice(144, 48);

    Bytes output = Bytes.EMPTY;
    PrecompileContractResult result;
    try {
      boolean proved =
          CKZG4844JNI.verifyKzgProof(
              commitment.toArray(), z.toArray(), y.toArray(), proof.toArray());

      if (proved) {
        Bytes fieldElementsPerBlob =
            Bytes32.wrap(
                Bytes.of(CKZG4844JNI.getFieldElementsPerBlob()).xor(Bytes32.ZERO)); // usually 4096
        Bytes blsModulus =
            Bytes32.wrap(Bytes.of(CKZG4844JNI.BLS_MODULUS.toByteArray()).xor(Bytes32.ZERO));

        output = Bytes.concatenate(fieldElementsPerBlob, blsModulus);

        result =
            new PrecompileContractResult(
                output, false, MessageFrame.State.COMPLETED_SUCCESS, Optional.empty());
      } else {
        result =
            new PrecompileContractResult(
                output,
                false,
                MessageFrame.State.COMPLETED_FAILED,
                Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
      }
      return result;
    } catch (RuntimeException kzgFailed) {
      System.out.println(kzgFailed.getMessage());
      result =
          new PrecompileContractResult(
              output,
              false,
              MessageFrame.State.COMPLETED_FAILED,
              Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }
    return result;
  }
}
