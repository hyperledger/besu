/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.orion.testutil;

import static com.google.common.io.Files.readLines;
import static net.consensys.cava.io.file.Files.copyResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import net.consensys.orion.cmd.Orion;
import net.consensys.orion.config.Config;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OrionTestHarness {

  private static final Logger LOG = LogManager.getLogger();

  private final Orion orion;
  private final Config config;

  private static final String HOST = "127.0.0.1";

  private OrionTestHarness(final Orion orion, final Config config) {

    this.orion = orion;
    this.config = config;
  }

  public static OrionTestHarness create(final Path tempDir) throws Exception {

    Path key1pub = copyResource("orion_key_0.pub", tempDir.resolve("orion_key_0.pub"));
    Path key1key = copyResource("orion_key_0.key", tempDir.resolve("orion_key_0.key"));

    Path key2pub = copyResource("orion_key_1.pub", tempDir.resolve("orion_key_1.pub"));
    Path key2key = copyResource("orion_key_1.key", tempDir.resolve("orion_key_1.key"));

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
            + "storage = \"leveldb:database/orion_node\"\n"
            + "publickeys = ["
            + joinPathsAsTomlListEntry(key1pub, key2pub)
            + "]\n"
            + "privatekeys = ["
            + joinPathsAsTomlListEntry(key1key, key2key)
            + "]\n"
            + "workdir= \""
            + tempDir.toString()
            + "\"\n";
    // @formatter:on

    Config config = Config.load(confString);

    final Orion orion = new Orion();
    orion.run(System.out, System.err, config);

    LOG.info("Orion node port: {}", orion.nodePort());
    LOG.info("Orion client port: {}", orion.clientPort());

    return new OrionTestHarness(orion, config);
  }

  public Orion getOrion() {
    return orion;
  }

  public Config getConfig() {
    return config;
  }

  public List<String> getPublicKeys() {
    return config.publicKeys().stream()
        .map(OrionTestHarness::readFile)
        .collect(Collectors.toList());
  }

  public List<String> getPrivateKeys() {
    return config.privateKeys().stream()
        .map(OrionTestHarness::readFile)
        .collect(Collectors.toList());
  }

  private static String readFile(final Path path) {
    try {
      return readLines(path.toFile(), Charsets.UTF_8).get(0);
    } catch (IOException e) {
      e.printStackTrace();
      return "";
    }
  }

  private static String joinPathsAsTomlListEntry(final Path... paths) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (Path path : paths) {
      if (!first) {
        builder.append(",");
      }
      first = false;
      builder.append("\"").append(path.toAbsolutePath().toString()).append("\"");
    }
    return builder.toString();
  }

  public String clientUrl() {
    return new HttpUrl.Builder()
        .scheme("http")
        .host(HOST)
        .port(orion.clientPort())
        .build()
        .toString();
  }
}
