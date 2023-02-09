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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.BeforeClass;
import org.junit.Test;

public class KZGPointEvalPrecompileContractTest {

  private static KZGPointEvalPrecompiledContract contract;
  private final MessageFrame toRun = mock(MessageFrame.class);

  @BeforeClass
  public static void init() {
    Path testSetupAbsolutePath =
        Path.of(
            KZGPointEvalPrecompileContractTest.class.getResource("trusted_setup_4.txt").getPath());
    contract = new KZGPointEvalPrecompiledContract(Optional.of(testSetupAbsolutePath));
    contract.init();
  }

  @Test
  public void happyPath() {
    Bytes input =
        Bytes.fromHexString(
            "013c03613f6fc558fb7e61e75602241ed9a2f04e36d8670aadd286e71b5ca9cc420000000000000000000000000000000000000000000000000000000000000031e5a2356cbc2ef6a733eae8d54bf48719ae3d990017ca787c419c7d369f8e3c83fac17c3f237fc51f90e2c660eb202a438bc2025baded5cd193c1a018c5885bc9281ba704d5566082e851235c7be763b2a99adff965e0a121ee972ebc472d02944a74f5c6243e14052e105124b70bf65faf85ad3a494325e269fad097842cba");

    Bytes fieldElementsPerBlob =
        Bytes32.wrap(Bytes.ofUnsignedInt(CKZG4844JNI.getFieldElementsPerBlob()).xor(Bytes32.ZERO));
    Bytes blsModulus =
        Bytes32.wrap(Bytes.of(CKZG4844JNI.BLS_MODULUS.toByteArray()).xor(Bytes32.ZERO));
    Bytes expectedOutput = Bytes.concatenate(fieldElementsPerBlob, blsModulus);
    // contract input is encoded as follows: versioned_hash | z | y | commitment | proof |
    PrecompiledContract.PrecompileContractResult result = contract.computePrecompile(input, toRun);
    assertThat(result.getOutput()).isEqualTo(expectedOutput);
    MessageFrame.State endState = result.getState();
    assertThat(endState).isEqualTo(MessageFrame.State.COMPLETED_SUCCESS);
  }

  @Test
  public void sadPaths() {
    try (InputStream failVectors =
        KZGPointEvalPrecompileContractTest.class.getResourceAsStream("fail_pointEvaluation.json")) {

      ObjectMapper jsonMapper = new ObjectMapper();
      JsonNode failJson = jsonMapper.readTree(failVectors);

      for (JsonNode testCase : failJson) {

        Bytes input = Bytes.fromHexString(testCase.get("Input").asText());

        PrecompiledContract.PrecompileContractResult result =
            contract.computePrecompile(input, toRun);
        MessageFrame.State endState = result.getState();
        assertThat(endState).isEqualTo(MessageFrame.State.COMPLETED_FAILED);
        assertThat(result.getHaltReason()).isPresent();
        assertThat(result.getHaltReason()).contains(ExceptionalHaltReason.PRECOMPILE_ERROR);
      }
    } catch (IOException ioe) {
      fail("couldn't load test vectors", ioe);
    }
  }
}
