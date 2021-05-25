package org.hyperledger.besu.ethereum.eth.messages;

import static java.math.BigInteger.TWO;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_MIN;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE_PLUS_1;
import static org.hyperledger.besu.ethereum.eth.manager.RequestManager.wrapRequestId;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;

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

public class RequestIdMessageTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

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
        @JsonProperty("Uncles") final List<BlockHeader> uncles) {
      super(
          transactions.stream()
              .map(testTransaction -> (Transaction) testTransaction)
              .collect(toUnmodifiableList()),
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
