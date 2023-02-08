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
 *
 */

package org.hyperledger.besu.evmtool;

import static org.hyperledger.besu.evmtool.B11rSubCommand.COMMAND_ALIAS;
import static org.hyperledger.besu.evmtool.B11rSubCommand.COMMAND_NAME;

import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.referencetests.BlockchainReferenceTestCaseSpec.ReferenceTestBlockHeader;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = COMMAND_NAME,
    aliases = {COMMAND_ALIAS},
    description = "Execute an Ethereum State Test.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class B11rSubCommand implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(B11rSubCommand.class);

  static final String COMMAND_NAME = "block-builder";
  static final String COMMAND_ALIAS = "b11r";
  private static final Path stdoutPath = Path.of("stdout");
  private static final Path stdinPath = Path.of("stdin");
  private final InputStream input;
  private final PrintStream output;

  @Option(
      names = {"--input.header"},
      paramLabel = "full path",
      description = "The block header for the block")
  private final Path header = stdinPath;

  @Option(
      names = {"--input.txs"},
      paramLabel = "full path",
      description = "The transactions to block")
  private final Path txs = stdinPath;

  @Option(
      names = {"--input.ommers"},
      paramLabel = "full path",
      description = "The ommers for the block")
  private final Path ommers = stdinPath;

  @Option(
      names = {"--seal.clique"},
      paramLabel = "full path",
      description = "The clique seal/signature for the block")
  private final Path sealClique = stdinPath;

  @Option(
      names = {"--seal.ethash"},
      description = "Use Proof of Work to seal the block")
  private final Boolean sealEthash = false;

  @Option(
      names = {"--seal.ethash.mode"},
      paramLabel = "full path",
      description = "The ethash mode for the block")
  private String sealEthashMode = "noproof";

  @Option(
      names = {"--output.basedir"},
      paramLabel = "full path",
      description = "The output ")
  private final Path outDir = Path.of(".");

  @Option(
      names = {"--output.block"},
      paramLabel = "file name",
      description = "The account state after the transition")
  private final Path outBlock = Path.of("block.json");

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @SuppressWarnings("unused")
  public B11rSubCommand() {
    // PicoCLI requires this
    this(System.in, System.out);
  }

  @SuppressWarnings("unused")
  public B11rSubCommand(final EvmToolCommand parentCommand) {
    // PicoCLI requires this too
    this(System.in, System.out);
  }

  B11rSubCommand(final InputStream input, final PrintStream output) {
    this.input = input;
    this.output = output;
  }

  @Override
  public void run() {
    objectMapper.setDefaultPrettyPrinter(
        (new DefaultPrettyPrinter())
            .withSpacesInObjectEntries()
            .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent(" "))
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent(" ")));
    final ObjectReader t8nReader = objectMapper.reader();
    objectMapper.disable(Feature.AUTO_CLOSE_SOURCE);

    ObjectNode config;
    try {
      if (header.equals(stdinPath)
          || txs.equals(stdinPath)
          || ommers.equals(stdinPath)
          || sealClique.equals(stdinPath)) {
        config =
            (ObjectNode) t8nReader.readTree(new InputStreamReader(input, StandardCharsets.UTF_8));
      } else {
        config = objectMapper.createObjectNode();
      }

      if (!header.equals(stdinPath)) {
        config.set(
            "header", t8nReader.readTree(new FileReader(header.toFile(), StandardCharsets.UTF_8)));
      }
      if (!txs.equals(stdinPath)) {
        config.set("txs", t8nReader.readTree(new FileReader(txs.toFile(), StandardCharsets.UTF_8)));
      }
      if (!ommers.equals(stdinPath)) {
        config.set(
            "ommers", t8nReader.readTree(new FileReader(ommers.toFile(), StandardCharsets.UTF_8)));
      }
      if (!sealClique.equals(stdinPath)) {
        config.set(
            "clique",
            t8nReader.readTree(new FileReader(sealClique.toFile(), StandardCharsets.UTF_8)));
      }
    } catch (final JsonProcessingException jpe) {
      output.println("File content error: " + jpe);
      jpe.printStackTrace();
      return;
    } catch (final IOException e) {
      LOG.error("Unable to read state file", e);
      return;
    }

    var testHeader = this.readHeader(config.get("header"));
    Bytes txsBytes = null;

    if (config.has("txs")) {
      String txsString = config.get("txs").textValue();
      if (txsString.length() > 0) {
        txsBytes = Bytes.fromHexString(txsString);
      }
    }

    var newHeader =
        BlockHeaderBuilder.fromHeader(testHeader)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader();
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    rlpOut.startList();
    newHeader.writeTo(rlpOut);
    if (txsBytes != null) {
      rlpOut.writeRaw(txsBytes);
    } else {
      rlpOut.startList();
      rlpOut.endList();
    }
    // ommers
    rlpOut.startList();
    rlpOut.endList();

    // withdrawals
    // TODO

    rlpOut.endList();

    final ObjectNode resultObject = objectMapper.createObjectNode();
    resultObject.put("rlp", rlpOut.encoded().toHexString());
    resultObject.put("hash", newHeader.getHash().toHexString());
    var writer = objectMapper.writerWithDefaultPrettyPrinter();
    try {
      var resultString = writer.writeValueAsString(resultObject);
      if (outBlock.equals((stdoutPath))) {
        output.println(resultString);
      } else {
        try (var fileOut =
            new PrintStream(new FileOutputStream(outDir.resolve(outBlock).toFile()))) {
          fileOut.println(resultString);
        }
      }
    } catch (FileNotFoundException | JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  void maybeMoveField(final ObjectNode jsonObject, final String oldField, final String newField) {
    if (jsonObject.has(oldField)) {
      jsonObject.set(newField, jsonObject.remove(oldField));
    }
  }

  private ReferenceTestBlockHeader readHeader(final JsonNode jsonObject) {
    ObjectNode objectNode = (ObjectNode) jsonObject;
    maybeMoveField(objectNode, "logsBloom", "bloom");
    maybeMoveField(objectNode, "sha3Uncles", "uncleHash");
    maybeMoveField(objectNode, "miner", "coinbase");
    return objectMapper.convertValue(jsonObject, ReferenceTestBlockHeader.class);
  }
}
