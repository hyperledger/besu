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
package org.hyperledger.besu.crypto;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.common.io.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

public class KeyPairUtil {
  private static final Logger LOG = LogManager.getLogger();

  public static String loadResourceFile(final String resourcePath) {
    try {
      URL path = KeyPairUtil.class.getClassLoader().getResource(resourcePath);
      return Resources.toString(path, StandardCharsets.UTF_8).trim();
    } catch (Exception e) {
      throw new RuntimeException("Unable to load resource: " + resourcePath, e);
    }
  }

  public static SECP256K1.KeyPair loadKeyPairFromResource(final String resourcePath) {
    final SECP256K1.KeyPair keyPair;
    String keyData = loadResourceFile(resourcePath);
    if (keyData == null || keyData.isEmpty()) {
      throw new IllegalArgumentException("Unable to load resource: " + resourcePath);
    }
    SECP256K1.PrivateKey privateKey = SECP256K1.PrivateKey.create(Bytes32.fromHexString((keyData)));
    keyPair = SECP256K1.KeyPair.create(privateKey);

    LOG.info("Loaded keyPair {} from {}", keyPair.getPublicKey().toString(), resourcePath);
    return keyPair;
  }

  public static SECP256K1.KeyPair loadKeyPair(final File keyFile) {

    final SECP256K1.KeyPair key;
    if (keyFile.exists()) {

      key = load(keyFile);
      LOG.info(
          "Loaded public key {} from {}", key.getPublicKey().toString(), keyFile.getAbsolutePath());
    } else {
      key = SECP256K1.KeyPair.generate();
      try {
        storeKeyPair(key, keyFile);
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot store generated private key.");
      }
      LOG.info(
          "Generated new public key {} and stored it to {}",
          key.getPublicKey().toString(),
          keyFile.getAbsolutePath());
    }
    return key;
  }

  public static SECP256K1.KeyPair loadKeyPair(final Path homeDirectory) {
    return loadKeyPair(getDefaultKeyFile(homeDirectory));
  }

  public static File getDefaultKeyFile(final Path homeDirectory) {
    return homeDirectory.resolve("key").toFile();
  }

  public static SECP256K1.KeyPair load(final File file) {
    return SECP256K1.KeyPair.create(loadPrivateKey(file));
  }

  static SECP256K1.PrivateKey loadPrivateKey(final File file) {
    try {
      final List<String> info = Files.readAllLines(file.toPath());
      if (info.size() != 1) {
        throw new IllegalArgumentException("Supplied file does not contain valid keyPair pair.");
      }
      return SECP256K1.PrivateKey.create(Bytes32.fromHexString((info.get(0))));
    } catch (IOException ex) {
      throw new IllegalArgumentException("Supplied file does not contain valid keyPair pair.");
    }
  }

  static void storeKeyPair(final SECP256K1.KeyPair keyKair, final File file) throws IOException {
    final File privateKeyDir = file.getParentFile();
    privateKeyDir.mkdirs();
    final Path tempPath = Files.createTempFile(privateKeyDir.toPath(), ".tmp", "");
    Files.write(
        tempPath,
        keyKair.getPrivateKey().getEncodedBytes().toString().getBytes(StandardCharsets.UTF_8));
    Files.move(tempPath, file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE);
  }
}
