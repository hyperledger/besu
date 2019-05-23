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

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.consensys.cava.io.file.Files.copyResource;

import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import com.google.common.io.CharSink;
import com.google.common.io.Files;
import net.consensys.orion.cmd.Orion;
import net.consensys.orion.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

public class OrionTestHarnessFactory {

  private static final Logger LOG = LogManager.getLogger();
  protected static final String HOST = "127.0.0.1";

  public static OrionTestHarness create(
      final Path tempDir,
      final Ed25519PublicKeyParameters pubKey,
      final String pubKeyPath,
      final Ed25519PrivateKeyParameters privKey,
      final String privKeyPath,
      final String... othernodes)
      throws IOException {
    return create(
        tempDir, pubKeyPath, privKeyPath, pubKey.getEncoded(), privKey.getEncoded(), othernodes);
  }

  public static OrionTestHarness create(
      final Path tempDir,
      final PublicKey pubKey,
      final String pubKeyPath,
      final PrivateKey privKey,
      final String privKeyPath,
      final String... othernodes)
      throws IOException {
    return create(
        tempDir, pubKeyPath, privKeyPath, pubKey.getEncoded(), privKey.getEncoded(), othernodes);
  }

  private static OrionTestHarness create(
      final Path tempDir,
      final String pubKeyPath,
      final String privKeyPath,
      final byte[] encodedPubKey,
      final byte[] encodedPrivKey,
      final String[] othernodes)
      throws IOException {
    final Path pubKeyFile = tempDir.resolve(pubKeyPath);
    final CharSink pubKeySink = Files.asCharSink(pubKeyFile.toFile(), UTF_8);
    pubKeySink.write(Base64.getEncoder().encodeToString(encodedPubKey));

    final Path privKeyFile = tempDir.resolve(privKeyPath);
    final CharSink privKeySink = Files.asCharSink(privKeyFile.toFile(), UTF_8);
    privKeySink.write(Base64.getEncoder().encodeToString(encodedPrivKey));
    return create(tempDir, pubKeyFile, privKeyFile, othernodes);
  }

  public static OrionTestHarness create(
      final Path tempDir,
      final String pubKeyPath,
      final String privKeyPath,
      final String... othernodes)
      throws IOException {
    Path key1pub = copyResource(pubKeyPath, tempDir.resolve(pubKeyPath));
    Path key1key = copyResource(privKeyPath, tempDir.resolve(privKeyPath));

    return create(tempDir, key1pub, key1key, othernodes);
  }

  public static OrionTestHarness create(
      final Path tempDir, final Path key1pub, final Path key1key, final String... othernodes) {

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
