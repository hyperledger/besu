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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;

/** The KZGPointEval precompile contract. */
public class KZGPointEvalPrecompiledContract implements PrecompiledContract {

  boolean inited;
  Optional<Path> pathToTrustedSetup;

  /** Instantiates a new KZGPointEval precompile contract. */
  public KZGPointEvalPrecompiledContract() {
    this(Optional.empty());
  }

  /**
   * Instantiates a new KZGPointEval precompile contract.
   *
   * @param pathToTrustedSetup the trusted setup path
   */
  public KZGPointEvalPrecompiledContract(final Optional<Path> pathToTrustedSetup) {
    this.pathToTrustedSetup = pathToTrustedSetup;
  }

  public synchronized void init() {
    if (inited) {
      return;
    }
    String absolutePathToSetup;
    CKZG4844JNI.Preset bitLength;
    if (pathToTrustedSetup.isPresent()) {
      Path pathToSetup = pathToTrustedSetup.get();
      absolutePathToSetup = pathToSetup.toAbsolutePath().toString();
    } else {
      InputStream is =
          KZGPointEvalPrecompiledContract.class.getResourceAsStream(
              "mainnet_kzg_trusted_setup_4096.txt");
      try {
        File jniWillLoadFrom = File.createTempFile("kzgTrustedSetup", "txt");
        jniWillLoadFrom.deleteOnExit();
        Files.copy(is, jniWillLoadFrom.toPath(), REPLACE_EXISTING);
        is.close();
        absolutePathToSetup = jniWillLoadFrom.getAbsolutePath();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    try (BufferedReader setupFile =
        Files.newBufferedReader(Paths.get(absolutePathToSetup), Charset.defaultCharset())) {
      String firstLine = setupFile.readLine();
      if ("4".equals(firstLine)) {
        bitLength = CKZG4844JNI.Preset.MINIMAL;
      } else if ("4096".equals(firstLine)) {
        bitLength = CKZG4844JNI.Preset.MAINNET;
      } else {
        throw new IllegalArgumentException("provided file not a setup for either 4 or 4096 bits");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    CKZG4844JNI.loadNativeLibrary(bitLength);
    try {
      CKZG4844JNI.loadTrustedSetup(absolutePathToSetup);
    } catch (RuntimeException mightBeAlreadyLoaded) {
      if (!mightBeAlreadyLoaded.getMessage().contains("Trusted Setup is already loaded")) {
        throw mightBeAlreadyLoaded;
      }
    }
    inited = true;
  }

  /** free up resources. */
  @VisibleForTesting
  public void tearDown() {
    if (inited) {
      CKZG4844JNI.freeTrustedSetup();
    }
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
    init();

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
