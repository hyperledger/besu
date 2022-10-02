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
package org.hyperledger.enclave.testutil;

import static com.google.common.io.Files.readLines;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import org.assertj.core.util.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

public class TesseraProcessTestHarness implements EnclaveTestHarness {
  private static final Logger LOG = LoggerFactory.getLogger(TesseraProcessTestHarness.class);
  private final Map<String, Process> tesseraProcesses = new HashMap<>();
  private final ExecutorService outputProcessorExecutor = Executors.newCachedThreadPool();
  private final EnclaveConfiguration enclaveConfiguration;
  private final AtomicReference<Process> tesseraProcess = new AtomicReference<>();
  private final ExecutorService executorService = Executors.newCachedThreadPool();
  private URI nodeURI;
  private URI q2TUri;
  private File tempFolder;

  protected TesseraProcessTestHarness(final EnclaveConfiguration enclaveConfiguration) {
    this.enclaveConfiguration = enclaveConfiguration;
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  @Override
  public void start() {
    this.tempFolder = Files.newTemporaryFolder();
    this.tempFolder.deleteOnExit();
    LOG.info("Temporary directory: " + tempFolder.getAbsolutePath());
    try {
      final String configFile = createConfigFile();
      final Optional<String> enclaveStartScript = findTesseraStartScript();
      if (enclaveStartScript.isPresent()) {
        final List<String> commandArgs = createCommandArgs(configFile, enclaveStartScript.get());
        final List<String> jvmArgs = createJvmArgs();
        LOG.info("Starting: {}", String.join(" ", commandArgs));
        LOG.info("JVM Args: {}", String.join(" ", jvmArgs));
        startTessera(commandArgs, jvmArgs);
      } else {
        throw new Exception("Tessera dist not found");
      }

    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() {
    if (tesseraProcess.get().isAlive()) {
      tesseraProcess.get().destroy();
      try {
        FileUtils.forceDelete(tempFolder);
      } catch (final IOException e) {
        LOG.info("Temporary directory not deleted");
      }
    }
  }

  @Override
  public void close() {
    stop();
  }

  @Override
  public List<Path> getPublicKeyPaths() {
    return Arrays.asList(enclaveConfiguration.getPublicKeys());
  }

  @Override
  public String getDefaultPublicKey() {
    return readFile(enclaveConfiguration.getPublicKeys()[0]);
  }

  @Override
  public List<String> getPublicKeys() {
    return Arrays.stream(enclaveConfiguration.getPublicKeys())
        .map(TesseraProcessTestHarness::readFile)
        .collect(Collectors.toList());
  }

  @Override
  public URI clientUrl() {
    return q2TUri;
  }

  @Override
  public URI nodeUrl() {
    return nodeURI;
  }

  private Path getTesseraUrisFileName() {
    final String TESSERA_URI_FILE_NAME = "tessera.uris";
    return Paths.get(tempFolder.getAbsolutePath(), TESSERA_URI_FILE_NAME);
  }

  @Override
  public void addOtherNode(final URI otherNode) {
    enclaveConfiguration.addOtherNode(otherNode.toString());
  }

  @Override
  public EnclaveType getEnclaveType() {
    return EnclaveType.TESSERA;
  }

  private List<String> createCommandArgs(final String pathToConfigFile, final String startScript) {
    final List<String> command = new ArrayList<>();
    command.add(startScript);
    command.add("-configfile");
    command.add(pathToConfigFile);
    command.add("--debug");
    command.add("--XoutputServerURIPath");
    command.add(tempFolder.getAbsolutePath());
    return command;
  }

  private List<String> createJvmArgs() {
    final List<String> command = new ArrayList<>();
    command.add("-Xms128M");
    command.add("-Xmx128M");
    return command;
  }

  private Optional<String> findTesseraStartScript() {
    final String path = System.getProperty("tessera-dist");
    return Optional.ofNullable(path);
  }

  public void startTessera(final List<String> args, final List<String> jvmArgs) throws Exception {

    final ProcessBuilder processBuilder = new ProcessBuilder(args);
    processBuilder.environment().put("JAVA_OPTS", String.join(" ", jvmArgs));
    // processBuilder.redirectOutput(new File(tempFolder, "Proce“ssOutput.txt"));

    try {
      final Process process = processBuilder.redirectErrorStream(true).start();
      tesseraProcess.set(process);
      tesseraProcesses.put(enclaveConfiguration.getName(), process);
      // redirectTesseraOutput();
    } catch (final NullPointerException ex) {
      ex.printStackTrace();
      throw new NullPointerException("Check that application.jar property has been set");
    }

    this.waitForTesseraUris()
        .ifPresentOrElse(this::readTesseraUriFile, () -> LOG.info("Tessera not started"));
  }

  private Optional<Path> waitForTesseraUris() throws InterruptedException {
    final CountDownLatch startUpLatch = new CountDownLatch(1);
    final Path tesseraUris = this.getTesseraUrisFileName();
    executorService.submit(
        () -> {
          while (true) {
            final boolean exists =
                java.nio.file.Files.exists(tesseraUris, LinkOption.NOFOLLOW_LINKS);
            if (exists) {
              startUpLatch.countDown();
              return;
            }
            try {
              LOG.info("Waiting for Tessera...");
              TimeUnit.MILLISECONDS.sleep(3000L);
            } catch (final InterruptedException ex) {
              LOG.error(ex.getMessage());
            }
          }
        });
    return startUpLatch.await(30, TimeUnit.SECONDS) ? Optional.of(tesseraUris) : Optional.empty();
  }

  private void readTesseraUriFile(final Path tesseraUris) {
    try {
      try (final Reader reader =
          java.nio.file.Files.newBufferedReader(tesseraUris, StandardCharsets.UTF_8)) {
        final Properties properties = new Properties();
        properties.load(reader);
        this.q2TUri = URI.create(properties.getProperty("Q2T"));
        LOG.info("Q2T URI: {}", q2TUri);
        final URI thirdPartyUri = URI.create(properties.getProperty("THIRD_PARTY"));
        LOG.info("ThirdParty URI: {}", thirdPartyUri);
        this.nodeURI = URI.create(properties.getProperty("P2P"));
        LOG.info("Node URI: {}", nodeURI);
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("UnusedMethod")
  private void redirectTesseraOutput() {
    executorService.submit(
        () -> {
          try (final BufferedReader reader =
              Stream.of(tesseraProcess.get().getInputStream())
                  .map(InputStreamReader::new)
                  .map(BufferedReader::new)
                  .findAny()
                  .get()) {

            String line;
            while ((line = reader.readLine()) != null) {
              System.out.println(line);
            }

          } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
          }
        });
  }

  public synchronized void shutdown() {
    final Set<String> localMap = new HashSet<>(tesseraProcesses.keySet());
    localMap.forEach(this::killTesseraProcess);
    outputProcessorExecutor.shutdown();
    try {
      if (!outputProcessorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        LOG.error("Output processor executor did not shutdown cleanly.");
      }
    } catch (final InterruptedException e) {
      LOG.error("Interrupted while already shutting down", e);
      Thread.currentThread().interrupt();
    }
  }

  private void killTesseraProcess(final String name) {
    final Process process = tesseraProcesses.remove(name);
    if (process == null) {
      LOG.error("Process {} wasn't in our list", name);
      return;
    }
    if (!process.isAlive()) {
      LOG.info("Process {} already exited, pid {}", name, process.pid());
      return;
    }
    LOG.info("Killing {} process, pid {}", name, process.pid());
    process.destroy();
    try {
      process.waitFor(30, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
      LOG.warn("Wait for death of process {} was interrupted", name, e);
    }

    if (process.isAlive()) {
      LOG.warn("Process {} still alive, destroying forcibly now, pid {}", name, process.pid());
      try {
        process.destroyForcibly().waitFor(30, TimeUnit.SECONDS);
      } catch (final Exception e) {
        // just die already
      }
      LOG.info("Process exited with code {}", process.exitValue());
    }
  }

  private String createConfigFile() {
    // @formatter:off
    String confString =
        "{\n"
            + "    \"mode\": \"orion\","
            + "    \"encryptor\":{\n"
            + "        \"type\":\"NACL\",\n"
            + "        \"properties\":{\n"
            + "\n"
            + "        }\n"
            + "    },\n"
            + "    \"useWhiteList\": false,\n"
            + "    \"jdbc\": {\n"
            + "        \"username\": \"sa\",\n"
            + "        \"password\": \"\",\n"
            + "        \"url\": \"jdbc:h2:"
            + Path.of(tempFolder.getAbsolutePath(), "db")
            + ";MODE=Oracle;TRACE_LEVEL_SYSTEM_OUT=0\",\n"
            + "        \"autoCreateTables\": true\n"
            + "    },\n"
            + "    \"serverConfigs\":[\n"
            + "        {\n"
            + "            \"app\":\"ThirdParty\",\n"
            + "            \"enabled\": true,\n"
            + "            \"serverAddress\":\"http://127.0.0.1:0\",\n"
            + "            \"cors\" : {\n"
            + "                \"allowedMethods\" : [\"GET\", \"OPTIONS\"],\n"
            + "                \"allowedOrigins\" : [\"*\"]\n"
            + "            },\n"
            + "            \"communicationType\" : \"REST\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"app\":\"Q2T\",\n"
            + "            \"enabled\": true,\n"
            + "            \"serverAddress\":\"http://localhost:0\",\n"
            + "            \"communicationType\" : \"REST\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"app\":\"P2P\",\n"
            + "            \"enabled\": true,\n"
            + "            \"serverAddress\":\"http://127.0.0.1:0\",\n"
            + "            \"communicationType\" : \"REST\"\n"
            + "        }\n"
            + "    ],\n"
            + "    \"keys\": {\n"
            + "        \"passwords\": [],\n"
            + "        \"keyData\": [\n"
            + "            {\n"
            + "                \"privateKeyPath\": \""
            + enclaveConfiguration.getPrivateKeys()[0].toString()
            + "\",\n"
            + "                \"publicKeyPath\": \""
            + enclaveConfiguration.getPublicKeys()[0].toString()
            + "\"\n"
            + "            }\n"
            + "        ]\n"
            + "    },\n"
            + "    \"alwaysSendTo\": []";

    if (enclaveConfiguration.getOtherNodes().size() != 0) {
      confString +=
          ",\n"
              + "    \"peer\": [\n"
              + "        {\n"
              + "            \"url\": \""
              + enclaveConfiguration.getOtherNodes().get(0)
              + "\"\n"
              + "        }\n"
              + "    ]";
    } else {
      confString += ",\n" + "    \"peer\": []";
    }

    confString += "\n}";
    // @formatter:on
    LOG.info("Tessera config: \n" + confString);

    final File configFile =
        Files.newFile(Path.of(tempFolder.getAbsolutePath(), "config").toString());
    LOG.info("config file: " + configFile.getAbsolutePath());
    try {
      final FileWriter fw = new FileWriter(configFile, StandardCharsets.UTF_8);
      fw.write(confString);
      fw.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return configFile.getAbsolutePath();
  }

  private static String readFile(final Path path) {
    try {
      return readLines(path.toFile(), Charsets.UTF_8).get(0);
    } catch (final IOException e) {
      LOG.error(e.getMessage());
      return "";
    }
  }
}
