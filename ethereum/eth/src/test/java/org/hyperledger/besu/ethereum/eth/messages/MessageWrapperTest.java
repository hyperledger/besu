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
package org.hyperledger.besu.ethereum.eth.messages;

import static java.math.BigInteger.TWO;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_MIN;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE_PLUS_1;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class MessageWrapperTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void GetBlockHeaders() throws IOException {
    final var testJson = parseTestFile("GetBlockHeadersPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final GetBlockHeadersMessage getBlockHeadersMessage =
        GetBlockHeadersMessage.create(
            Hash.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000deadc0de"),
            5,
            5,
            false);
    final Bytes actual = getBlockHeadersMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void GetBlockHeaders1() throws IOException {
    final var testJson = parseTestFile("GetBlockHeadersPacket66-1.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final GetBlockHeadersMessage getBlockHeadersMessage =
        GetBlockHeadersMessage.create(9999, 5, 5, false);
    final Bytes actual = getBlockHeadersMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void BlockHeaders() throws IOException {
    final var testJson = parseTestFile("BlockHeadersPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final BlockHeadersMessage blockHeadersMessage =
        BlockHeadersMessage.create(
            Arrays.asList(
                objectMapper.treeToValue(
                    testJson.get("data").get("BlockHeadersPacket"), TestBlockHeader[].class)));
    final Bytes actual = blockHeadersMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void GetBlockBodies() throws IOException {
    final var testJson = parseTestFile("GetBlockBodiesPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final GetBlockBodiesMessage getBlockBodiesMessage =
        GetBlockBodiesMessage.create(
            Stream.of(
                    "0x00000000000000000000000000000000000000000000000000000000deadc0de",
                    "0x00000000000000000000000000000000000000000000000000000000feedbeef")
                .map(Hash::fromHexString)
                .collect(toUnmodifiableList()));
    final Bytes actual = getBlockBodiesMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void BlockBodies() throws IOException {
    final var testJson = parseTestFile("BlockBodiesPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final BlockBodiesMessage blockBodiesMessage =
        BlockBodiesMessage.create(
            Arrays.asList(
                objectMapper.treeToValue(
                    testJson.get("data").get("BlockBodiesPacket"), TestBlockBody[].class)));
    final Bytes actual = blockBodiesMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void GetNodeData() throws IOException {
    final var testJson = parseTestFile("GetNodeDataPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final GetNodeDataMessage getNodeDataMessage =
        GetNodeDataMessage.create(
            Stream.of(
                    "0x00000000000000000000000000000000000000000000000000000000deadc0de",
                    "0x00000000000000000000000000000000000000000000000000000000feedbeef")
                .map(Hash::fromHexString)
                .collect(toUnmodifiableList()));
    final Bytes actual = getNodeDataMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void NodeData() throws IOException {
    final var testJson = parseTestFile("NodeDataPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final NodeDataMessage nodeDataMessage =
        NodeDataMessage.create(
            Stream.of("0xdeadc0de", "0xfeedbeef")
                .map(Bytes::fromHexString)
                .collect(toUnmodifiableList()));
    final Bytes actual = nodeDataMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void GetReceipts() throws IOException {
    final var testJson = parseTestFile("GetReceiptsPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final GetReceiptsMessage getReceiptsMessage =
        GetReceiptsMessage.create(
            Stream.of(
                    "0x00000000000000000000000000000000000000000000000000000000deadc0de",
                    "0x00000000000000000000000000000000000000000000000000000000feedbeef")
                .map(Hash::fromHexString)
                .collect(toUnmodifiableList()));
    final Bytes actual = getReceiptsMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void Receipts() throws IOException {
    final var testJson = parseTestFile("ReceiptsPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final ReceiptsMessage receiptsMessage =
        ReceiptsMessage.create(
            singletonList(
                singletonList(
                    new TransactionReceipt(
                        TransactionType.FRONTIER,
                        0,
                        1,
                        singletonList(
                            new LogWithMetadata(
                                0,
                                0,
                                Hash.ZERO,
                                Hash.ZERO,
                                0,
                                Address.fromHexString("0x11"),
                                Bytes.fromHexString("0x0100ff"),
                                Stream.of(
                                        "0x000000000000000000000000000000000000000000000000000000000000dead",
                                        "0x000000000000000000000000000000000000000000000000000000000000beef")
                                    .map(LogTopic::fromHexString)
                                    .collect(toUnmodifiableList()),
                                false)),
                        LogsBloomFilter.fromHexString(
                            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"),
                        Optional.empty()))));
    final Bytes actual = receiptsMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void GetPooledTransactions() throws IOException {
    final var testJson = parseTestFile("GetPooledTransactionsPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final GetPooledTransactionsMessage getPooledTransactionsMessage =
        GetPooledTransactionsMessage.create(
            Stream.of(
                    "0x00000000000000000000000000000000000000000000000000000000deadc0de",
                    "0x00000000000000000000000000000000000000000000000000000000feedbeef")
                .map(Hash::fromHexString)
                .collect(toUnmodifiableList()));
    final Bytes actual =
        getPooledTransactionsMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void PooledTransactions() throws IOException {
    final var testJson = parseTestFile("PooledTransactionsPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final PooledTransactionsMessage pooledTransactionsMessage =
        PooledTransactionsMessage.create(
            Arrays.asList(
                objectMapper.treeToValue(
                    testJson.get("data").get("PooledTransactionsPacket"),
                    TestTransaction[].class)));
    final Bytes actual =
        pooledTransactionsMessage.wrapMessageData(BigInteger.valueOf(1111)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  private static class TestTransaction extends Transaction {

    @JsonCreator
    public TestTransaction(
        @JsonProperty("nonce") final String nonce,
        @JsonProperty("gasPrice") final String gasPrice,
        @JsonProperty("gas") final String gasLimit,
        @JsonProperty("to") final String to,
        @JsonProperty("value") final String value,
        @JsonProperty("input") final String data,
        @JsonProperty("v") final String v,
        @JsonProperty("r") final String r,
        @JsonProperty("s") final String s,
        @JsonProperty("hash") final String __) {
      super(
          Long.decode(nonce),
          Wei.fromHexString(gasPrice),
          Long.decode(gasLimit),
          Address.fromHexString(to),
          Wei.fromHexString(value),
          new SECP256K1()
              .createSignature(
                  new BigInteger(r.substring(2), 16),
                  new BigInteger(s.substring(2), 16),
                  recIdAndChainId(Byte.decode(v)).getKey()),
          Bytes.fromHexString(data),
          recIdAndChainId(Byte.decode(v)).getValue(),
          Optional.empty());
    }
  }

  private static Map.Entry<Byte, Optional<BigInteger>> recIdAndChainId(final Byte vByte) {
    final BigInteger v = BigInteger.valueOf(vByte);
    final byte recId;
    Optional<BigInteger> chainId = Optional.empty();
    if (v.equals(REPLAY_UNPROTECTED_V_BASE) || v.equals(REPLAY_UNPROTECTED_V_BASE_PLUS_1)) {
      recId = v.subtract(REPLAY_UNPROTECTED_V_BASE).byteValueExact();
    } else if (v.compareTo(REPLAY_PROTECTED_V_MIN) > 0) {
      chainId = Optional.of(v.subtract(REPLAY_PROTECTED_V_BASE).divide(TWO));
      recId = v.subtract(TWO.multiply(chainId.get()).add(REPLAY_PROTECTED_V_BASE)).byteValueExact();
    } else {
      throw new RuntimeException(
          String.format("An unsupported encoded `v` value of %s was found", v));
    }
    return Map.entry(recId, chainId);
  }

  public static class TestBlockBody extends BlockBody {
    @JsonCreator
    public TestBlockBody(
        @JsonProperty("Transactions") final List<TestTransaction> transactions,
        @JsonProperty("Uncles") final List<TestBlockHeader> uncles) {
      super(
          transactions.stream().collect(toUnmodifiableList()),
          uncles.stream().collect(toUnmodifiableList()));
    }
  }

  public static class TestBlockHeader extends BlockHeader {

    @JsonCreator
    public TestBlockHeader(
        @JsonProperty("parentHash") final String parentHash,
        @JsonProperty("sha3Uncles") final String uncleHash,
        @JsonProperty("miner") final String coinbase,
        @JsonProperty("stateRoot") final String stateRoot,
        @JsonProperty("transactionsRoot") final String transactionsTrie,
        @JsonProperty("receiptsRoot") final String receiptTrie,
        @JsonProperty("logsBloom") final String bloom,
        @JsonProperty("difficulty") final String difficulty,
        @JsonProperty("number") final String number,
        @JsonProperty("gasLimit") final String gasLimit,
        @JsonProperty("gasUsed") final String gasUsed,
        @JsonProperty("timestamp") final String timestamp,
        @JsonProperty("extraData") final String extraData,
        @JsonProperty("mixHash") final String mixHash,
        @JsonProperty("nonce") final String nonce,
        @JsonProperty("hash") final String hash) {
      super(
          Hash.fromHexString(parentHash),
          Hash.fromHexString(uncleHash),
          Address.fromHexString(coinbase),
          Hash.fromHexString(stateRoot),
          Hash.fromHexString(transactionsTrie),
          Hash.fromHexString(receiptTrie),
          LogsBloomFilter.fromHexString(bloom),
          Difficulty.fromHexString(difficulty),
          Long.decode(number),
          Long.decode(gasLimit),
          Long.decode(gasUsed),
          Long.decode(timestamp),
          Bytes.fromHexString(extraData),
          null,
          Hash.fromHexString(mixHash),
          Bytes.fromHexString(nonce).getLong(0),
          new MainnetBlockHeaderFunctions());
    }
  }

  private JsonNode parseTestFile(final String filename) throws IOException {
    return objectMapper.readTree(this.getClass().getResource("/" + filename));
  }
}
