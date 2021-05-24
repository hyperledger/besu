package org.hyperledger.besu.ethereum.eth.messages;

import static io.vertx.core.json.Json.mapper;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.eth.manager.RequestManager.wrapRequestId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.AccessListEntry;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.manager.EthMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.referencetests.StateTestAccessListDeserializer;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.junit.Test;

public class RequestIdMessageTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(BlockBody.class, TestBlockBodyFactory);
    objectMapper.registerModule(module);
  }

  @Test
  public void GetBlockHeaders() throws IOException {
    final var testJson = parseTestFile("GetBlockHeadersPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final Bytes actual =
        wrapRequestId(
                1111,
                GetBlockHeadersMessage.create(
                    Hash.fromHexString(
                        "0x00000000000000000000000000000000000000000000000000000000deadc0de"),
                    5,
                    5,
                    false))
            .getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void GetBlockHeaders1() throws IOException {
    final var testJson = parseTestFile("GetBlockHeadersPacket66-1.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final Bytes actual =
        wrapRequestId(1111, GetBlockHeadersMessage.create(9999, 5, 5, false)).getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void BlockHeaders() throws IOException {
    final var testJson = parseTestFile("BlockHeadersPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final Bytes actual =
        wrapRequestId(
                1111,
                BlockHeadersMessage.create(
                    Arrays.asList(
                        objectMapper.treeToValue(
                            testJson.get("data").get("BlockHeadersPacket"), BlockHeader[].class))))
            .getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void GetBlockBodies() throws IOException {
    final var testJson = parseTestFile("GetBlockBodiesPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final Bytes actual =
        wrapRequestId(
                1111,
                GetBlockBodiesMessage.create(
                    Stream.of(
                            "0x00000000000000000000000000000000000000000000000000000000deadc0de",
                            "0x00000000000000000000000000000000000000000000000000000000feedbeef")
                        .map(Hash::fromHexString)
                        .collect(toUnmodifiableList())))
            .getData();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void BlockBodies() throws IOException {
    final var testJson = parseTestFile("BlockBodiesPacket66.json");
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final Bytes actual =
        wrapRequestId(
                1111,
                BlockBodiesMessage.create(
                    Arrays.asList(
                        objectMapper.treeToValue(
                            testJson.get("data").get("BlockBodiesPacket"), BlockBody[].class))))
            .getData();
    assertThat(actual).isEqualTo(expected);
  }

  //  private Bytes wrappedData(final MessageData messageData) {
  //    return wrapRequestId(1111, messageData).getData();
  //  }

  private static class TestTransactionFactory {

    @JsonCreator
    public static Transaction createTransaction(
        @JsonProperty("nonce") final String nonce,
        @JsonProperty("gasPrice") final String gasPrice,
        @JsonProperty("gas") final String gasLimit,
        @JsonProperty("to") final String to,
        @JsonProperty("value") final String value,
        @JsonProperty("input") final String data,
        @JsonProperty("v") final String v,
        @JsonProperty("r") final String r,
        @JsonProperty("s") final String s,
        @JsonProperty("hash") final String hash) {
      final SECPSignature secpSignature =
          new SECP256K1()
              .createSignature(new BigInteger(r, 16), new BigInteger(s, 16), Byte.decode(v));
      return Transaction.builder()
          .nonce(Long.decode(nonce))
          .gasPrice(Wei.fromHexString(gasPrice))
          .to(Address.fromHexString(to))
          .value(Wei.fromHexString(value))
          .payload(Bytes.fromHexString(data))
          .signature(secpSignature)
          .build();
    }
  }

  public static class TestBlockBodyFactory {
    @JsonCreator
    public static BlockBody create(
        @JsonProperty("Transactions") final List<Transaction> transactions,
        @JsonProperty("Uncles") final List<BlockHeader> uncles) {
      return new BlockBody(transactions, uncles.stream().collect(toUnmodifiableList()));
    }
  }

  public static class TestBlockHeaderFactory {

    @JsonCreator
    public static BlockHeader create(
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
      return new BlockHeader(
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
          new BlockHeaderFunctions() {
            @Override
            public Hash hash(final BlockHeader header) {
              return Hash.fromHexString(hash);
            }

            @Override
            public ParsedExtraData parseExtraData(final BlockHeader header) {
              return null;
            }
          });
    }
  }

  private JsonNode parseTestFile(final String filename) throws IOException {
    return objectMapper.readTree(this.getClass().getResource("/" + filename));
  }
}
