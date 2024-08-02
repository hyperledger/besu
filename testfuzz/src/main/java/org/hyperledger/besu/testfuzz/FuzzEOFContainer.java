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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
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

/** Fuzzes the parsing and validation of an EOF container. */
@SuppressWarnings({"java:S106", "CallToPrintStackTrace"}) // we use lots the console, on purpose
public class FuzzEOFContainer implements Runnable {

  static final ObjectMapper eofTestMapper = createObjectMapper();

  static final JavaType javaType =
      eofTestMapper
          .getTypeFactory()
          .constructParametricType(Map.class, String.class, EOFTestCaseSpec.class);

  String corpusFiles;

  /**
   * Creates a fuzzer with a set of corpus dirs and files. The first directory is where new corpus
   * is dropped.
   *
   * @param corpusDirs list of dirs with corpus data.
   */
  public FuzzEOFContainer(final String[] corpusDirs) {
    corpusFiles = createCorpus(corpusDirs);
  }

  /**
   * Main class. Calls FuzzEOFContainer with all args
   *
   * @param args list of dirs with corpus data.
   */
  public static void main(final String[] args) {
    new FuzzEOFContainer(args).run();
  }

  private static ObjectMapper createObjectMapper() {
    final ObjectMapper objectMapper = new ObjectMapper();

    // Attempting to get byte-perfect to go's standard json output
    objectMapper.setDefaultPrettyPrinter(
        (new DefaultPrettyPrinter())
            .withSeparators(
                Separators.createDefaultInstance().withObjectFieldValueSpacing(Spacing.BOTH))
            .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent("  "))
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent("  ")));

    // When we stream stdin we cannot close the stream
    objectMapper.disable(Feature.AUTO_CLOSE_SOURCE);

    // GraalVM has a hard time reflecting these classes for serialization
    SimpleModule serializers = new SimpleModule("Serializers");
    serializers.addSerializer(Address.class, ToStringSerializer.instance);
    serializers.addSerializer(Bytes.class, ToStringSerializer.instance);
    objectMapper.registerModule(serializers);

    return objectMapper;
  }

  String createCorpus(final String[] filenames) {
    StringBuilder corpus = new StringBuilder();
    File firstDir = null;

    for (String arg : filenames) {
      File f = new File(arg);
      if (f.isDirectory()) {
        corpus.append(",").append(arg);
        if (firstDir == null) {
          firstDir = f;
        } else {
          File finalFirstDir = firstDir;
          try (var files = Files.walk(f.toPath(), Integer.MAX_VALUE)) {
            files.forEach(
                ff -> {
                  File file = ff.toFile();
                  if (file.isFile()) {
                    extractFile(file, finalFirstDir);
                  }
                });
          } catch (IOException e) {
            System.out.println("Exception walking " + f + ": " + e.getMessage());
          }
        }
      } else {
        if (!extractFile(f, firstDir)) {
          corpus.append(",").append(arg);
        }
      }
    }

    return corpus.substring(1);
  }

  @Override
  public void run() {
    try {
      new Fuzzer(new EOFFuzz(), corpusFiles).start();
    } catch (IOException
        | NoSuchAlgorithmException
        | ClassNotFoundException
        | InvocationTargetException
        | IllegalAccessException
        | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean extractFile(final File f, final File initialCorpus) {
    final Map<String, EOFTestCaseSpec> eofTests;
    try {
      eofTests = eofTestMapper.readValue(f, javaType);
    } catch (IOException e) {
      // presume parse failed because it's a corpus file
      return false;
    }
    for (var entry : eofTests.entrySet()) {
      int index = 0;
      for (var vector : entry.getValue().getVector().entrySet()) {
        try (FileOutputStream fos =
            new FileOutputStream(
                new File(
                    initialCorpus,
                    f.toPath().getFileName() + "_" + (index++) + "_" + vector.getKey()))) {
          fos.write(Bytes.fromHexString(vector.getValue().code()).toArrayUnsafe());
        } catch (IOException e) {
          System.out.println("Invalid file " + f + ": " + e.getMessage());
          e.printStackTrace();
          System.exit(1);
        }
      }
    }
    return true;
  }

  static class EOFFuzz extends AbstractFuzzTarget {

    List<ExternalClient> externalClients = new ArrayList<>();
    EVM evm = MainnetEVMs.pragueEOF(EvmConfiguration.DEFAULT);

    public EOFFuzz() throws IOException {
      // evm1 build bin must be in the path
      externalClients.add(new StreamingClient("evm1", "evmone-eofparse"));
      // geth build bin must be in the path
      externalClients.add(new StreamingClient("geth", "eofdump", "eofparser"));
      // revm target/release must be in the path
      externalClients.add(new StreamingClient("revm", "revme", "bytecode"));
      // externalClients.add(
      //    new SingleQueryClient(
      //        "revm",
      //        "Validation:\\s+Ok\\(\\s*\\((\\w*)",
      //        1,
      //        "(Validation:\\s*Err\\(\\s*\\w+\\(\\s*|Decoding Error: )(\\w*)",
      //        2,
      //        "revme",
      //        "bytecode"));
    }

    @Override
    public void fuzz(final byte[] bytes) {
      Bytes eofUnderTest = Bytes.wrap(bytes);
      String eofUnderTestHexString = eofUnderTest.toHexString();
      Code code = evm.getCodeUncached(eofUnderTest);
      Map<String, String> results = new HashMap<>();
      boolean mismatch = false;
      for (var client : externalClients) {
        String value = client.differentialFuzz(eofUnderTestHexString);
        results.put(client.getName(), value);
        if (value.startsWith("fail: ")) {
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
          System.out.println(layout.version());
          System.out.println(layout.invalidReason());
          System.out.println(code.getEofVersion());
          System.out.println(code.getClass().getName());
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
        mismatch |= besuValid != entry.getValue().toUpperCase(Locale.getDefault()).startsWith("OK");
      }
      if (mismatch) {
        System.out.println("besu: " + besuReason);
        for (var entry : results.entrySet()) {
          System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println("code: " + eofUnderTest.toUnprefixedHexString());
        System.out.println("size: " + eofUnderTest.size());
        System.out.println();
      }
    }
  }
}
