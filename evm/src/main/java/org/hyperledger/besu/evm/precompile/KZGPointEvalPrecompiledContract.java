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

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.Words;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;

/** The KZGPointEval precompile contract. */
public class KZGPointEvalPrecompiledContract implements PrecompiledContract {
  private static final AtomicBoolean loaded = new AtomicBoolean(false);

  private static Bytes successResult;

  private static void init() {
    CKZG4844JNI.loadNativeLibrary(CKZG4844JNI.Preset.MAINNET);
    Bytes fieldElementsPerBlob =
        Bytes32.wrap(Words.intBytes(CKZG4844JNI.getFieldElementsPerBlob()).xor(Bytes32.ZERO));
    Bytes blsModulus =
        Bytes32.wrap(Bytes.of(CKZG4844JNI.BLS_MODULUS.toByteArray()).xor(Bytes32.ZERO));

    successResult = Bytes.concatenate(fieldElementsPerBlob, blsModulus);
  }

  /**
   * Init the C-KZG native lib using a file as trusted setup
   *
   * @param trustedSetupFile the file with the trusted setup
   * @throws IllegalStateException is the trusted setup was already loaded
   */
  public static void init(final Path trustedSetupFile) {
    if (loaded.compareAndSet(false, true)) {
      init();
      CKZG4844JNI.loadTrustedSetup(trustedSetupFile.toAbsolutePath().toString());
    } else {
      throw new IllegalStateException("KZG trusted setup was already loaded");
    }
  }

  /**
   * Init the C-KZG native lib using a resource identified by the passed network name as trusted
   * setup
   *
   * @param networkName used to select the resource that contains the trusted setup
   * @throws IllegalStateException is the trusted setup was already loaded
   */
  public static void init(final String networkName) {
    if (loaded.compareAndSet(false, true)) {
      init();
      final String trustedSetupResourceName =
          "/kzg-trusted-setups/" + networkName.toLowerCase() + ".txt";
      CKZG4844JNI.loadTrustedSetupFromResource(
          trustedSetupResourceName, KZGPointEvalPrecompiledContract.class);
    } else {
      throw new IllegalStateException("KZG trusted setup was already loaded");
    }
  }

  /** free up resources. */
  @VisibleForTesting
  void tearDown() {
    CKZG4844JNI.freeTrustedSetup();
    loaded.set(false);
  }

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
      return PrecompileContractResult.halt(
          null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }
    Bytes32 versionedHash = Bytes32.wrap(input.slice(0, 32));
    Bytes z = input.slice(32, 32);
    Bytes y = input.slice(64, 32);
    Bytes commitment = input.slice(96, 48);
    Bytes proof = input.slice(144, 48);
    if (versionedHash.get(0) != 0x01) { // unsupported hash version
      return PrecompileContractResult.halt(
          null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    } else {
      byte[] hash = Hash.sha256(commitment).toArray();
      hash[0] = 0x01;
      if (!versionedHash.equals(Bytes32.wrap(hash))) {
        return PrecompileContractResult.halt(
            null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
      }
    }
    try {
      boolean proved =
          CKZG4844JNI.verifyKzgProof(
              commitment.toArray(), z.toArray(), y.toArray(), proof.toArray());

      if (proved) {
        return PrecompileContractResult.success(successResult);
      } else {
        return PrecompileContractResult.halt(
            null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
      }
    } catch (RuntimeException kzgFailed) {
      System.out.println(kzgFailed.getMessage());

      return PrecompileContractResult.halt(
          null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }
  }
}
