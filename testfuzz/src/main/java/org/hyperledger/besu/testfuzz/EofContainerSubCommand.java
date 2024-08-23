/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.testfuzz;

import static org.hyperledger.besu.testfuzz.EofContainerSubCommand.COMMAND_NAME;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.referencetests.EOFTestCaseSpec;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.code.CodeV1;
import org.hyperledger.besu.evm.code.EOFLayout;
import org.hyperledger.besu.evm.code.EOFLayout.EOFContainerMode;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.core.util.Separators.Spacing;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.gitlab.javafuzz.core.AbstractFuzzTarget;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/** Fuzzes the parsing and validation of an EOF container. */
@SuppressWarnings({"java:S106", "CallToPrintStackTrace"}) // we use lots the console, on purpose
@CommandLine.Command(
    name = COMMAND_NAME,
    description = "Fuzzes EOF container parsing and validation",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class EofContainerSubCommand extends AbstractFuzzTarget implements Runnable {

  static final String COMMAND_NAME = "eof-container";

  @Option(
      names = {"--corpus-dir"},
      paramLabel = "<directory>",
      description = "Directory to store corpus files")
  private final Path corpusDir = Path.of("corpus");

  @Option(
      names = {"--tests-dir"},
      paramLabel = "<directory>",
      description = "Directory where EOF tests references file tree lives")
  private final Path testsDir = null;

  @Option(
      names = {"--client"},
      paramLabel = "<directory>=<CLI>",
      description = "Add a client for differential fuzzing")
  private final Map<String, String> clients = new LinkedHashMap<>();

  @CommandLine.ParentCommand private final BesuFuzzCommand parentCommand;

  static final ObjectMapper eofTestMapper = createObjectMapper();
  static final JavaType javaType =
      eofTestMapper
          .getTypeFactory()
          .constructParametricType(Map.class, String.class, EOFTestCaseSpec.class);

  List<ExternalClient> externalClients = new ArrayList<>();
  EVM evm = MainnetEVMs.pragueEOF(EvmConfiguration.DEFAULT);
  long validContainers;
  long totalContainers;

  /**
   * Default constructor for the EofContainerSubCommand class. This constructor initializes the
   * parentCommand to null.
   */
  public EofContainerSubCommand() {
    this(null);
  }

  /**
   * Constructs a new EofContainerSubCommand with the specified parent command.
   *
   * @param parentCommand The parent command for this subcommand.
   */
  public EofContainerSubCommand(final BesuFuzzCommand parentCommand) {
    this.parentCommand = parentCommand;
  }

  private static ObjectMapper createObjectMapper() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setDefaultPrettyPrinter(
        (new DefaultPrettyPrinter())
            .withSeparators(
                Separators.createDefaultInstance().withObjectFieldValueSpacing(Spacing.BOTH))
            .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent("  "))
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent("  ")));
    objectMapper.disable(Feature.AUTO_CLOSE_SOURCE);
    SimpleModule serializers = new SimpleModule("Serializers");
    serializers.addSerializer(Address.class, ToStringSerializer.instance);
    serializers.addSerializer(Bytes.class, ToStringSerializer.instance);
    objectMapper.registerModule(serializers);

    return objectMapper;
  }

  @Override
  public void run() {
    // load test dir into corpus dir
    if (testsDir != null) {
      File f = testsDir.toFile();
      if (f.isDirectory()) {
        try (var files = Files.walk(f.toPath(), Integer.MAX_VALUE)) {
          files.forEach(
              ff -> {
                File file = ff.toFile();
                if (file.isFile()) {
                  extractFile(file, corpusDir.toFile());
                }
              });
        } catch (IOException e) {
          parentCommand.out.println("Exception walking " + f + ": " + e.getMessage());
        }
      }
    }

    clients.forEach((k, v) -> externalClients.add(new StreamingClient(k, v.split(" "))));
    System.out.println("Fuzzing client set: " + clients.keySet());

    try {
      new Fuzzer(this, corpusDir.toString(), this::fuzzStats).start();
    } catch (NoSuchAlgorithmException
        | ClassNotFoundException
        | InvocationTargetException
        | IllegalAccessException
        | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private void extractFile(final File f, final File initialCorpus) {
    final Map<String, EOFTestCaseSpec> eofTests;
    try {
      eofTests = eofTestMapper.readValue(f, javaType);
    } catch (IOException e) {
      // presume parse failed because it's a corpus file
      return;
    }
    for (var entry : eofTests.entrySet()) {
      int index = 0;
      for (var vector : entry.getValue().getVector().entrySet()) {
        try (FileOutputStream fos =
            new FileOutputStream(
                new File(
                    initialCorpus,
                    f.toPath().getFileName() + "_" + (index++) + "_" + vector.getKey()))) {
          Bytes codeBytes = Bytes.fromHexString(vector.getValue().code());
          evm.getCodeUncached(codeBytes);
          fos.write(codeBytes.toArrayUnsafe());
        } catch (IOException e) {
          parentCommand.out.println("Invalid file " + f + ": " + e.getMessage());
          e.printStackTrace();
          System.exit(1);
        }
      }
    }
  }

  @Override
  public void fuzz(final byte[] bytes) {
    Bytes eofUnderTest = Bytes.wrap(bytes);
    String eofUnderTestHexString = eofUnderTest.toHexString();
    Code code = evm.getCodeUncached(eofUnderTest);
    Map<String, String> results = new LinkedHashMap<>();
    boolean mismatch = false;
    for (var client : externalClients) {
      String value = client.differentialFuzz(eofUnderTestHexString);
      results.put(client.getName(), value);
      if (value == null || value.startsWith("fail: ")) {
        mismatch = true; // if an external client fails, always report it as an error
      }
    }
    boolean besuValid = false;
    String besuReason;
    if (!code.isValid()) {
      besuReason = ((CodeInvalid) code).getInvalidReason();
    } else if (code.getEofVersion() != 1) {
      EOFLayout layout = EOFLayout.parseEOF(eofUnderTest);
      if (layout.isValid()) {
        besuReason = "Besu Parsing Error";
        parentCommand.out.println(layout.version());
        parentCommand.out.println(layout.invalidReason());
        parentCommand.out.println(code.getEofVersion());
        parentCommand.out.println(code.getClass().getName());
        System.exit(1);
        mismatch = true;
      } else {
        besuReason = layout.invalidReason();
      }
    } else if (EOFContainerMode.INITCODE.equals(
        ((CodeV1) code).getEofLayout().containerMode().get())) {
      besuReason = "Code is initcode, not runtime";
    } else {
      besuReason = "OK";
      besuValid = true;
    }
    for (var entry : results.entrySet()) {
      mismatch =
          mismatch
              || besuValid != entry.getValue().toUpperCase(Locale.getDefault()).startsWith("OK");
    }
    if (mismatch) {
      parentCommand.out.println("besu: " + besuReason);
      for (var entry : results.entrySet()) {
        parentCommand.out.println(entry.getKey() + ": " + entry.getValue());
      }
      parentCommand.out.println("code: " + eofUnderTest.toUnprefixedHexString());
      parentCommand.out.println("size: " + eofUnderTest.size());
      parentCommand.out.println();
    } else {
      if (besuValid) {
        validContainers++;
      }
      totalContainers++;
    }
  }

  String fuzzStats() {
    return " / %5.2f%% valid %d/%d"
        .formatted((100.0 * validContainers) / totalContainers, validContainers, totalContainers);
  }
}
