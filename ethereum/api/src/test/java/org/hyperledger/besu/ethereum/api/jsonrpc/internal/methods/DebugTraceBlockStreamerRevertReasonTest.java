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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DebugTraceBlockStreamerRevertReasonTest {

  private static final String GENESIS_RESOURCE =
      "/org/hyperledger/besu/ethereum/api/jsonrpc/trace/chain-data/genesis-osaka.json";
  private static final KeyPair KEY_PAIR =
      SignatureAlgorithmFactory.getInstance()
          .createKeyPair(
              SECPPrivateKey.create(
                  Bytes32.fromHexString(
                      "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3"),
                  SignatureAlgorithm.ALGORITHM));
  // Contract that copies calldata to memory then REVERTs with it
  private static final Address REVERT_CONTRACT =
      Address.fromHexString("0x0032000000000000000000000000000000000000");

  // ABI encoding of Error("") — 68 bytes; first byte is 0x08 (has a leading-zero high nibble)
  private static final Bytes ABI_ERROR_EMPTY =
      Bytes.fromHexString(
          "0x08c379a0"
              + "0000000000000000000000000000000000000000000000000000000000000020"
              + "0000000000000000000000000000000000000000000000000000000000000000");

  // ABI encoding of Error("revert") — 4+32+32+32 = 100 bytes
  private static final Bytes ABI_ERROR_WITH_MESSAGE =
      Bytes.fromHexString(
          "0x08c379a0"
              + "0000000000000000000000000000000000000000000000000000000000000020"
              + "0000000000000000000000000000000000000000000000000000000000000006"
              + "7265766572740000000000000000000000000000000000000000000000000000");

  private ExecutionContextTestFixture fixture;
  private BlockchainQueries blockchainQueries;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  public void setUp() {
    final GenesisConfig genesisConfig = GenesisConfig.fromResource(GENESIS_RESOURCE);
    fixture =
        ExecutionContextTestFixture.builder(genesisConfig)
            .dataStorageFormat(DataStorageFormat.BONSAI)
            .build();
    blockchainQueries =
        new BlockchainQueries(
            fixture.getProtocolSchedule(),
            fixture.getBlockchain(),
            fixture.getStateArchive(),
            MiningConfiguration.MINING_DISABLED);
  }

  /**
   * A 68-byte revert reason requires 138 chars (2 for "0x" + 136 hex digits). The old hexBuf was
   * only 130 bytes — this test verifies no ArrayIndexOutOfBoundsException is thrown.
   */
  @Test
  public void hexBufDoesNotOverflowFor68ByteRevertReason() {
    final Block block = buildBlockWithCalldata(ABI_ERROR_EMPTY, 0);
    final DebugTraceBlockStreamer streamer =
        new DebugTraceBlockStreamer(
            block, TraceOptions.DEFAULT, fixture.getProtocolSchedule(), blockchainQueries);

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertThatCode(() -> streamer.streamTo(out, mapper)).doesNotThrowAnyException();
  }

  /**
   * The first byte of an ABI Error selector is 0x08. The high nibble (0) must not be stripped, so
   * the reason must start with "0x08" not "0x8".
   */
  @Test
  public void revertReasonPreservesLeadingZeroNibble() throws Exception {
    final Block block = buildBlockWithCalldata(ABI_ERROR_EMPTY, 0);
    final DebugTraceBlockStreamer streamer =
        new DebugTraceBlockStreamer(
            block, TraceOptions.DEFAULT, fixture.getProtocolSchedule(), blockchainQueries);

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    streamer.streamTo(out, mapper);

    final JsonNode structLogs = getStructLogs(out);
    final String reason = findRevertReason(structLogs);
    assertThat(reason).isNotNull();
    assertThat(reason).startsWith("0x08c379a0");
  }

  /**
   * A 100-byte revert reason also exceeds the old buffer and exercises the resize path. Verifies
   * the full hex encoding is correct.
   */
  @Test
  public void revertReasonEncodedCorrectlyFor100BytePayload() throws Exception {
    final Block block = buildBlockWithCalldata(ABI_ERROR_WITH_MESSAGE, 0);
    final DebugTraceBlockStreamer streamer =
        new DebugTraceBlockStreamer(
            block, TraceOptions.DEFAULT, fixture.getProtocolSchedule(), blockchainQueries);

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    streamer.streamTo(out, mapper);

    final JsonNode structLogs = getStructLogs(out);
    final String reason = findRevertReason(structLogs);
    assertThat(reason).isNotNull();
    assertThat(reason).startsWith("0x08c379a0");
    // full encoding must be exactly 2 + 100*2 = 202 chars
    assertThat(reason).hasSize(202);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private Block buildBlockWithCalldata(final Bytes calldata, final int nonce) {
    final Transaction tx =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .nonce(nonce)
            .maxPriorityFeePerGas(Wei.of(5))
            .maxFeePerGas(Wei.of(7))
            .gasLimit(200_000L)
            .to(REVERT_CONTRACT)
            .value(Wei.ZERO)
            .payload(calldata)
            .chainId(BigInteger.valueOf(42))
            .signAndBuild(KEY_PAIR);

    final BlockHeader genesis = fixture.getBlockchain().getChainHeadHeader();
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .number(genesis.getNumber() + 1L)
            .parentHash(genesis.getHash())
            .gasLimit(30_000_000L)
            .baseFeePerGas(Wei.of(7))
            .buildHeader();
    return new Block(header, new BlockBody(List.of(tx), Collections.emptyList(), Optional.empty()));
  }

  private JsonNode getStructLogs(final ByteArrayOutputStream out) throws Exception {
    final JsonNode root = mapper.readTree(out.toByteArray());
    return root.get(0).get("result").get("structLogs");
  }

  /** Returns the "reason" field from the first struct log that has one, or null. */
  private String findRevertReason(final JsonNode structLogs) {
    for (final JsonNode log : structLogs) {
      if (log.has("reason")) {
        return log.get("reason").asText();
      }
    }
    return null;
  }
}
