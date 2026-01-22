/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptance.ethereum;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.math.BigInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthCall;

/**
 * Acceptance test for SLOTNUM opcode (EIP-7843).
 *
 * <p>This test verifies that the SLOTNUM opcode (0x4b) correctly returns the slot number from the
 * block header.
 */
public class SlotNumOpcodeAcceptanceTest extends AcceptanceTestBase {
  private static final String GENESIS_FILE = "/dev/dev_amsterdam.json";

  /**
   * Address of the pre-deployed SLOTNUM test contract. Bytecode: SLOTNUM PUSH0 MSTORE PUSH1 0x20
   * PUSH0 RETURN (0x4b5f5260205ff3)
   */
  public static final String SLOTNUM_CONTRACT_ADDRESS =
      "0x000000000000000000000000000000000000784b";

  private BesuNode besuNode;
  private AmsterdamAcceptanceTestHelper testHelper;

  @BeforeEach
  void setUp() throws IOException {
    besuNode = besu.createExecutionEngineGenesisNode("besuNode", GENESIS_FILE);
    cluster.start(besuNode);
    testHelper = new AmsterdamAcceptanceTestHelper(besuNode, ethTransactions);
  }

  @AfterEach
  void tearDown() {
    besuNode.close();
  }

  /**
   * Test that the SLOTNUM opcode returns the correct slot number from the block header.
   *
   * <p>This test builds multiple blocks with incrementing slot numbers and verifies that the
   * SLOTNUM opcode returns the correct slot number for each block.
   */
  @Test
  public void slotNumReturnsCorrectSlotNumber() throws IOException {
    // Build first block with slot number 1
    testHelper.buildNewBlock();

    // Call the SLOTNUM contract to get the current slot number
    EthCall ethCallResult = besuNode.execute(ethTransactions.call(SLOTNUM_CONTRACT_ADDRESS));
    String resultHex = ethCallResult.getValue();

    // The slot number should be 1 (first block after genesis)
    BigInteger slotNumber = new BigInteger(resultHex.substring(2), 16);
    assertThat(slotNumber.longValue()).isEqualTo(1L);

    // Build second block with slot number 2
    testHelper.buildNewBlock();

    // Call the SLOTNUM contract again
    ethCallResult = besuNode.execute(ethTransactions.call(SLOTNUM_CONTRACT_ADDRESS));
    resultHex = ethCallResult.getValue();

    // The slot number should be 2 (second block after genesis)
    slotNumber = new BigInteger(resultHex.substring(2), 16);
    assertThat(slotNumber.longValue()).isEqualTo(2L);
  }

  /**
   * Test that the SLOTNUM opcode increments correctly across multiple blocks.
   *
   * <p>This test builds several blocks and verifies that the slot number increments with each block
   * as expected.
   */
  @Test
  public void slotNumIncrementsBetweenBlocks() throws IOException {
    // Build multiple blocks and verify slot number increments
    for (int expectedSlot = 1; expectedSlot <= 5; expectedSlot++) {
      testHelper.buildNewBlock();

      EthCall ethCallResult = besuNode.execute(ethTransactions.call(SLOTNUM_CONTRACT_ADDRESS));
      String resultHex = ethCallResult.getValue();
      BigInteger slotNumber = new BigInteger(resultHex.substring(2), 16);

      assertThat(slotNumber.longValue())
          .withFailMessage("Expected slot number %d but got %d", expectedSlot, slotNumber)
          .isEqualTo(expectedSlot);
    }
  }
}
