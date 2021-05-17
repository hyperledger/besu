package org.hyperledger.besu.ethereum.eth.messages;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.eth.manager.RequestManager.wrapRequestId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.AccessListEntry;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;

import java.io.IOException;
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
import org.junit.Test;

public class RequestIdMessageTest {

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
    final var objectMapper = new ObjectMapper();
    final Bytes expected = Bytes.fromHexString(testJson.get("rlp").asText());
    final Bytes actual =
        wrapRequestId(
                1111,
                BlockHeadersMessage.create(
                    Arrays.asList(
                        objectMapper.treeToValue(
                            testJson.get("data").get("BlockHeadersPacket"),
                            TestBlockHeader[].class))))
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
    final var objectMapper = new ObjectMapper();
    final Bytes actual =
        wrapRequestId(
                1111,
                BlockBodiesMessage.create(
                    Arrays.asList(
                        objectMapper.treeToValue(
                            testJson.get("data").get("BlockBodiesPacket"), TestBlockBody[].class))))
            .getData();
    assertThat(actual).isEqualTo(expected);
  }

  //  private Bytes wrappedData(final MessageData messageData) {
  //    return wrapRequestId(1111, messageData).getData();
  //  }

  private static class TestTransaction extends Transaction {

    @JsonCreator
    TestTransaction(
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
      final SECPSignature secpSignature = SECPSignature.create(r, s, v);
      super(
                Long.decode(nonce),
                Wei.fromHexString(gasPrice), Gas.fromHexString(gasLimit) ,Address.fromHexString(to),Wei.fromHexString(value), secpSignature,Bytes.fromHexString(data),
        )
      this.nonce = Long.decode(nonce);
      this.gasPrice =
      this.to = to.isEmpty() ? null : Address.fromHexString(to);

      SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
      this.keys =
          signatureAlgorithm.createKeyPair(
              signatureAlgorithm.createPrivateKey(Bytes32.fromHexString(secretKey)));

      this.gasLimits = parseArray(gasLimit, Gas::fromHexString);
      this.values = parseArray(value, Wei::fromHexString);
      this.payloads = parseArray(data, Bytes::fromHexString);
      this.maybeAccessLists = Optional.ofNullable(maybeAccessLists);
    }
  }

  public static class TestBlockBody extends BlockBody {
    @JsonCreator
    public TestBlockBody(
        @JsonProperty("Transactions") final List<Transaction> transactions,
        @JsonProperty("Uncles") final List<TestBlockHeader> uncles) {
      super(
          transactions,
          uncles.stream()
              .map(testBlockHeader -> (BlockHeader) testBlockHeader)
              .collect(toUnmodifiableList()));
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
    return new ObjectMapper().readTree(this.getClass().getResource("/" + filename));
  }
}
