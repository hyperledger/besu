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

import org.hyperledger.besu.evm.frame.MessageFrame;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

public class KZGPointEvalPrecompiledContract implements PrecompiledContract {

  public KZGPointEvalPrecompiledContract(final Optional<String> pathToTrustedSetup) {

    String pathToSetup;
    CKZG4844JNI.Preset bitLength;
    if (pathToTrustedSetup.isPresent()) {
      pathToSetup = pathToTrustedSetup.get();
    } else {
      InputStream is =
          KZGPointEvalPrecompiledContract.class.getResourceAsStream(
              "mainnet_kzg_trusted_setup_4096.txt");
      try {
        File jniWillLoadFrom = File.createTempFile("kzgTrustedSetup", "txt");
        Files.copy(is, jniWillLoadFrom.toPath(), REPLACE_EXISTING);
        is.close();
        pathToSetup = jniWillLoadFrom.getAbsolutePath();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    try {
      BufferedReader setupFile =
          Files.newBufferedReader(Paths.get(pathToSetup), Charset.defaultCharset());
      String firstLine = setupFile.readLine();
      if ("4".equals(firstLine)) {
        bitLength = CKZG4844JNI.Preset.MINIMAL;
      } else if ("4096".equals(firstLine)) {
        bitLength = CKZG4844JNI.Preset.MAINNET;
      } else {
        throw new IllegalArgumentException("provided file not a setup for either 4 or 4096 bits");
      }
      CKZG4844JNI.loadNativeLibrary(bitLength);
      try {
        CKZG4844JNI.loadTrustedSetup(pathToSetup);
      } catch (RuntimeException alreadyLoaded) {
        if (alreadyLoaded.getMessage().contains("Trusted Setup is already loaded")) {
        } else {
          throw alreadyLoaded;
        }
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @VisibleForTesting
  public void tearDown() {
    CKZG4844JNI.freeTrustedSetup();
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
      return new PrecompileContractResult(
          Bytes.EMPTY, false, MessageFrame.State.COMPLETED_FAILED, Optional.empty());
    }
    // Bytes versionedHash = input.slice(0, 32);
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

      // # Return FIELD_ELEMENTS_PER_BLOB and BLS_MODULUS as padded 32 byte big endian values
      //    return Bytes(U256(FIELD_ELEMENTS_PER_BLOB).to_be_bytes32() +
      // U256(BLS_MODULUS).to_be_bytes32())

      result =
          new PrecompileContractResult(
              output,
              false,
              proved ? MessageFrame.State.COMPLETED_SUCCESS : MessageFrame.State.COMPLETED_FAILED,
              Optional.empty());
      return result;
    } catch (RuntimeException kzgFailed) {
      System.out.println(kzgFailed.getMessage());
      result =
          new PrecompileContractResult(
              output, false, MessageFrame.State.COMPLETED_FAILED, Optional.empty());
    }
    return result;
  }
}
