package org.hyperledger.besu.consensus.qbt;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.qbt.RlpTestCaseSpec.RlpTestInput;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class RlpTest {

  private static final Logger LOG = LogManager.getLogger();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void messagesCanBeRLPEncodedAndDecoded() throws IOException, URISyntaxException {
    final Path rlpPath = Path.of(RlpTest.class.getResource("/rlp").toURI());
    Files.newDirectoryStream(rlpPath)
        .forEach(
            rlpTestFile -> {
              try {
                List<RlpTestCaseSpec> testCases =
                    objectMapper.readValue(rlpTestFile.toFile(), new TypeReference<>() {});
                for (RlpTestCaseSpec testCase : testCases) {
                  // message -> RLP
                  assertThat(Bytes.fromHexStringLenient(testCase.getOutput()))
                      .isEqualTo(testCase.getInput().toRlp());

                  // RLP -> message
                  final RlpTestInput rlpTestInput =
                      testCase.getInput().fromRlp(Bytes.fromHexString(testCase.getOutput()));
                  assertThat(testCase.getInput())
                      .usingRecursiveComparison()
                      .isEqualTo(rlpTestInput);
                }
              } catch (IOException e) {
                LOG.error("Unable to read rlp test file", e);
                throw new IllegalStateException(e);
              }
            });
  }

  private Bytes rlpEncode(final JsonNode message) {
    return null;
  }
}
