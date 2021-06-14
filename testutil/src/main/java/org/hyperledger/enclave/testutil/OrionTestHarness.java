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
package org.hyperledger.enclave.testutil;

import static com.google.common.io.Files.readLines;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import net.consensys.orion.cmd.Orion;
import net.consensys.orion.config.Config;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OrionTestHarness implements EnclaveTestHarness {
  private static final Logger LOG = LogManager.getLogger();

  private final Orion orion;
  private final EnclaveConfiguration enclaveConfiguration;

  private Config config;

  private boolean isRunning;

  protected static final String HOST = "127.0.0.1";

  protected OrionTestHarness(final EnclaveConfiguration enclaveConfiguration) {
    this.enclaveConfiguration = enclaveConfiguration;
    this.orion = new Orion();
  }

  @Override
  public void start() {
    if (!isRunning) {
      config = buildConfig();
      orion.run(config, enclaveConfiguration.isClearKnownNodes());
      isRunning = true;
      LOG.info("Orion node port: {}", orion.nodePort());
      LOG.info("Orion client port: {}", orion.clientPort());
    }
  }

  @Override
  public void stop() {
    if (isRunning) {
      orion.stop();
      isRunning = false;
    }
  }

  @Override
  public void close() {
    stop();
    try {
      MoreFiles.deleteRecursively(config.workDir(), RecursiveDeleteOption.ALLOW_INSECURE);
    } catch (final IOException e) {
      LOG.info("Failed to clean up temporary file: {}", config.workDir(), e);
    }
  }

  @Override
  public List<Path> getPublicKeyPaths() {
    return config.publicKeys();
  }

  public Config getConfig() {
    return config;
  }

  @Override
  public String getDefaultPublicKey() {
    return config.publicKeys().stream().map(OrionTestHarness::readFile).findFirst().orElseThrow();
  }

  @Override
  public List<String> getPublicKeys() {
    return config.publicKeys().stream()
        .map(OrionTestHarness::readFile)
        .collect(Collectors.toList());
  }

  private static String readFile(final Path path) {
    try {
      return readLines(path.toFile(), Charsets.UTF_8).get(0);
    } catch (final IOException e) {
      e.printStackTrace();
      return "";
    }
  }

  @Override
  public URI clientUrl() {
    final HttpUrl httpUrl =
        new HttpUrl.Builder().scheme("http").host(HOST).port(orion.clientPort()).build();

    return URI.create(httpUrl.toString());
  }

  @Override
  public URI nodeUrl() {
    final HttpUrl httpUrl =
        new HttpUrl.Builder().scheme("http").host(HOST).port(orion.nodePort()).build();

    return URI.create(httpUrl.toString());
  }

  @Override
  public void addOtherNode(final URI otherNode) {
    enclaveConfiguration.addOtherNode(otherNode.toString());
  }

  @Override
  public EnclaveType getEnclaveType() {
    return EnclaveType.ORION;
  }

  private Config buildConfig() {
    // @formatter:off
    String confString =
        "tls=\"off\"\n"
            + "tlsservertrust=\"tofu\"\n"
            + "tlsclienttrust=\"tofu\"\n"
            + "nodeport=0\n"
            + "nodenetworkinterface = \""
            + HOST
            + "\"\n"
            + "clientport=0\n"
            + "clientnetworkinterface = \""
            + HOST
            + "\"\n"
            + "storage = \""
            + enclaveConfiguration.getStorage()
            + "\"\n"
            + "publickeys = ["
            + joinPathsAsTomlListEntry(enclaveConfiguration.getPublicKeys())
            + "]\n"
            + "privatekeys = ["
            + joinPathsAsTomlListEntry(enclaveConfiguration.getPrivateKeys())
            + "]\n"
            + "workdir= \""
            + enclaveConfiguration.getTempDir().toString()
            + "\"\n";

    if (enclaveConfiguration.getOtherNodes().size() != 0) {
      confString +=
          "othernodes = ["
              + joinStringsAsTomlListEntry(enclaveConfiguration.getOtherNodes())
              + "]\n";
    }

    // @formatter:on
    return Config.load(confString);
  }

  private static String joinPathsAsTomlListEntry(final Path... paths) {
    return joinStringsAsTomlListEntry(
        Arrays.stream(paths).map(p -> p.toAbsolutePath().toString()).collect(Collectors.toList()));
  }

  private static String joinStringsAsTomlListEntry(final List<String> strings) {
    final StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (final String string : strings) {
      if (!first) {
        builder.append(",");
      }
      first = false;
      builder.append("\"").append(string).append("\"");
    }
    return builder.toString();
  }
}
