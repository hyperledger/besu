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

import static net.consensys.cava.io.file.Files.copyResource;

import java.nio.file.Path;

import net.consensys.orion.cmd.Orion;
import net.consensys.orion.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OrionTestHarnessFactory {

  private static final Logger LOG = LogManager.getLogger();
  protected static final String HOST = "127.0.0.1";

  public static OrionTestHarness create(
      final Path tempDir,
      final String pubKeyPath,
      final String privKeyPath,
      final String... othernodes)
      throws Exception {

    Path key1pub = copyResource(pubKeyPath, tempDir.resolve(pubKeyPath));
    Path key1key = copyResource(privKeyPath, tempDir.resolve(privKeyPath));

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
            + joinPathsAsTomlListEntry(key1pub)
            + "]\n"
            + "privatekeys = ["
            + joinPathsAsTomlListEntry(key1key)
            + "]\n"
            + "workdir= \""
            + tempDir.toString()
            + "\"\n";

    if (othernodes.length != 0) {
      confString += "othernodes = [" + joinStringsAsTomlListEntry(othernodes) + "]\n";
    }

    // @formatter:on

    Config config = Config.load(confString);

    final Orion orion = new Orion();
    orion.run(System.out, System.err, config);

    LOG.info("Orion node port: {}", orion.nodePort());
    LOG.info("Orion client port: {}", orion.clientPort());

    return new OrionTestHarness(orion, config);
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

  private static String joinStringsAsTomlListEntry(final String... strings) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String string : strings) {
      if (!first) {
        builder.append(",");
      }
      first = false;
      builder.append("\"").append(string).append("\"");
    }
    return builder.toString();
  }
}
