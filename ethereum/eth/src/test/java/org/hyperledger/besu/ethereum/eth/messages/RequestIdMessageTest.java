package org.hyperledger.besu.ethereum.eth.messages;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.eth.manager.RequestManager.wrapRequestId;

import org.hyperledger.besu.ethereum.core.Hash;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class RequestIdMessageTest {

  @Test
  public void GetBlockHeaders1() throws IOException {
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

  private JsonNode parseTestFile(final String filename) throws IOException {
    return new ObjectMapper().readTree(this.getClass().getResource("/" + filename));
  }
}
