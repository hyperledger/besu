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

package org.hyperledger.besu.ethereum.core.encoding;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.AccessList;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.deserializer.HexStringDeserializer;
import org.hyperledger.besu.ethereum.core.deserializer.QuantityToByteDeserializer;
import org.hyperledger.besu.ethereum.core.deserializer.QuantityToLongDeserializer;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.Streams;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DecodeBlockWithAccessListTransactionsTest {

  private final RLPInput rlpInput;
  private final Block expectedBlock;
  private final List<TransactionReceipt> transactionReceipts;

  public DecodeBlockWithAccessListTransactionsTest(
      final RLPInput rlpInput,
      final Block expectedBlock,
      final List<TransactionReceipt> transactionReceipts) {
    this.rlpInput = rlpInput;
    this.expectedBlock = expectedBlock;
    this.transactionReceipts = transactionReceipts;
  }

  @Parameterized.Parameters(name = "acl_block_{index}.json")
  public static Iterable<Object[]> data() {
    final ObjectMapper objectMapper = new ObjectMapper();
    return IntStream.rangeClosed(0, 9)
        .mapToObj(index -> String.format("acl_block_%d.json", index))
        .map(
            filename -> {
              try {
                return objectMapper.readTree(
                    DecodeBlockWithAccessListTransactionsTest.class.getResource(filename));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .map(
            rootNode -> {
              try {
                final JsonNode jsonNode = rootNode.get("json");

                final BytesValueRLPInput rlpInput =
                    new BytesValueRLPInput(
                        Bytes.fromHexString("0x" + rootNode.get("rlp").textValue()), false);

                final Block block =
                    new Block(
                        objectMapper.treeToValue(
                            jsonNode.get("header"), CustomHeaderForThisTest.class),
                        new BlockBody(
                            Arrays.asList(
                                Optional.ofNullable(
                                        objectMapper.treeToValue(
                                            jsonNode.get("transactions"),
                                            CustomTransactionForThisTest[].class))
                                    .orElse(new CustomTransactionForThisTest[] {})),
                            Arrays.asList(
                                Optional.ofNullable(
                                        objectMapper.treeToValue(
                                            jsonNode.get("uncles"),
                                            CustomHeaderForThisTest[].class))
                                    .orElse(new CustomHeaderForThisTest[] {}))));

                final List<CustomTransactionReceiptForThisTest> receipts =
                    Arrays.asList(
                        objectMapper.treeToValue(
                            jsonNode.get("receipts"), CustomTransactionReceiptForThisTest[].class));

                return new Object[] {rlpInput, block, receipts};
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(toUnmodifiableList());
  }

  @Test
  public void decodesBlockWithAccessListTransactionsCorrectly() {
    assertThat(Block.readFrom(rlpInput, new MainnetBlockHeaderFunctions()))
        .isEqualTo(expectedBlock);
    assertThat(transactionReceipts).isNotNull();
  }

  /**
   * Test files provided by Martin from EF so the json format is a bit different from what we would
   * generate. Hence the custom classes below.
   */
  private static class CustomHeaderForThisTest extends BlockHeader {
    @JsonCreator
    public CustomHeaderForThisTest(
        @JsonProperty("parentHash") final Hash parentHash,
        @JsonProperty("sha3Uncles") final Hash ommersHash,
        @JsonProperty("miner") final Address coinbase,
        @JsonProperty("stateRoot") final Hash stateRoot,
        @JsonProperty("transactionsRoot") final Hash transactionsRoot,
        @JsonProperty("receiptsRoot") final Hash receiptsRoot,
        @JsonProperty("logsBloom") final LogsBloomFilter logsBloom,
        @JsonProperty("difficulty") final Difficulty difficulty,
        @JsonProperty("number") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final long number,
        @JsonProperty("gasLimit") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final long gasLimit,
        @JsonProperty("gasUsed") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final long gasUsed,
        @JsonProperty("timestamp") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final long timestamp,
        @JsonProperty("extraData") @JsonDeserialize(using = HexStringDeserializer.class)
            final Bytes extraData,
        @JsonProperty("baseFee") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final Long baseFee,
        @JsonProperty("mixHash") final Hash mixHash,
        @JsonProperty("nonce") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final long nonce) {
      super(
          parentHash,
          ommersHash,
          coinbase,
          stateRoot,
          transactionsRoot,
          receiptsRoot,
          logsBloom,
          difficulty,
          number,
          gasLimit,
          gasUsed,
          timestamp,
          extraData,
          baseFee,
          mixHash,
          nonce,
          new MainnetBlockHeaderFunctions());
    }
  }

  private static class CustomTransactionForThisTest extends Transaction {
    private static TransactionType fromString(final String transactionTypeString) {
      return transactionTypeString.equals("0x0")
          ? TransactionType.FRONTIER
          : TransactionType.of(Bytes.fromHexStringLenient(transactionTypeString).get(0));
    }

    @JsonCreator
    public CustomTransactionForThisTest(
        @JsonProperty("type") final String transactionTypeString,
        @JsonProperty("chainId") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final Long chainId,
        @JsonProperty("nonce") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final long nonce,
        @JsonProperty("gasPrice") final Wei gasPrice,
        @JsonProperty("gas") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final long gasLimit,
        @JsonProperty("to") final Address to,
        @JsonProperty("value") final Wei value,
        @JsonProperty("input") @JsonDeserialize(using = HexStringDeserializer.class)
            final Bytes payload,
        @JsonProperty("accessList") @JsonDeserialize(using = AccessListDeserializer.class)
            final AccessList accessListNullable,
        @JsonProperty("v") @JsonDeserialize(using = QuantityToByteDeserializer.class)
            final byte recIdorV,
        @JsonProperty("r") @JsonDeserialize(using = HexStringDeserializer.class) final Bytes r,
        @JsonProperty("s") @JsonDeserialize(using = HexStringDeserializer.class) final Bytes s) {
      super(
          fromString(transactionTypeString),
          nonce,
          gasPrice,
          null,
          null,
          gasLimit,
          Optional.ofNullable(to),
          value,
          SECP256K1.Signature.create(
              UInt256.fromBytes(r).toBigInteger(),
              UInt256.fromBytes(s).toBigInteger(),
              fromString(transactionTypeString).equals(TransactionType.ACCESS_LIST)
                  ? recIdorV
                  : (byte) (1 - recIdorV % 2)),
          payload,
          Optional.ofNullable(accessListNullable).orElse(AccessList.EMPTY),
          null,
          Optional.of(BigInteger.ONE),
          fromString(transactionTypeString).equals(TransactionType.FRONTIER)
              ? Optional.empty()
              : Optional.of(BigInteger.valueOf(recIdorV)));
    }

    private static class AccessListDeserializer extends StdDeserializer<AccessList> {

      private AccessListDeserializer() {
        this(null);
      }

      private AccessListDeserializer(final Class<?> vc) {
        super(vc);
      }

      @Override
      public AccessList deserialize(final JsonParser jsonParser, final DeserializationContext ctxt)
          throws IOException {
        final JsonNode accessListNode = jsonParser.getCodec().readTree(jsonParser);
        return new AccessList(
            Streams.stream(accessListNode.elements())
                .map(
                    accessListEntryNode ->
                        Map.entry(
                            Address.fromHexString(accessListEntryNode.get("address").textValue()),
                            Streams.stream(accessListEntryNode.get("storageKeys").elements())
                                .map(
                                    storageKeyNode ->
                                        Bytes32.fromHexString(storageKeyNode.textValue()))
                                .collect(toUnmodifiableList())))
                .collect(toUnmodifiableList()));
      }
    }
  }

  @JsonIgnoreProperties(
      value = {
        "logs",
        "root",
        "logsBloom",
        "transactionHash",
        "contractAddress",
        "gasUsed",
        "blockHash",
        "transactionIndex"
      }) // these fields are not used in testing or the values in the test file are nonsense
  private static class CustomTransactionReceiptForThisTest extends TransactionReceipt {

    @JsonCreator
    public CustomTransactionReceiptForThisTest(
        @JsonProperty("type") final TransactionType transactionType,
        @JsonProperty("status") @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final long status,
        @JsonProperty("cumulativeGasUsed")
            @JsonDeserialize(using = QuantityToLongDeserializer.class)
            final long cumulativeGasUsed) {
      super(
          fromNullable(transactionType),
          (int) status,
          cumulativeGasUsed,
          Collections.emptyList() /* tests have no logs */,
          Optional.empty() /* tests have no revert reasons */);
    }

    private static TransactionType fromNullable(final TransactionType transactionType) {
      return transactionType == null ? TransactionType.FRONTIER : transactionType;
    }
  }
}
