/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.controller;

import org.hyperledger.besu.crypto.InvalidSEC256K1PrivateKeyStoreException;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.util.bytes.Bytes32;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.google.common.io.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    try {
      SECP256K1.PrivateKey privateKey =
          SECP256K1.PrivateKey.create(Bytes32.fromHexString((keyData)));
      keyPair = SECP256K1.KeyPair.create(privateKey);

      LOG.info("Loaded keyPair {} from {}", keyPair.getPublicKey().toString(), resourcePath);
      return keyPair;
    } catch (InvalidSEC256K1PrivateKeyStoreException e) {
      throw new IllegalArgumentException("Supplied file does not contain valid keyPair pair.");
    }
  }

  public static SECP256K1.KeyPair loadKeyPair(final File keyFile)
      throws IOException, IllegalArgumentException {
    try {
      final SECP256K1.KeyPair key;
      if (keyFile.exists()) {
        key = SECP256K1.KeyPair.load(keyFile);
        LOG.info("Loaded key {} from {}", key.getPublicKey().toString(), keyFile.getAbsolutePath());
      } else {
        key = SECP256K1.KeyPair.generate();
        key.getPrivateKey().store(keyFile);
        LOG.info(
            "Generated new key {} and stored it to {}",
            key.getPublicKey().toString(),
            keyFile.getAbsolutePath());
      }
      return key;
    } catch (InvalidSEC256K1PrivateKeyStoreException e) {
      throw new IllegalArgumentException("Supplied file does not contain valid key pair.");
    }
  }

  public static SECP256K1.KeyPair loadKeyPair(final Path homeDirectory) throws IOException {
    return loadKeyPair(getDefaultKeyFile(homeDirectory));
  }

  public static File getDefaultKeyFile(final Path homeDirectory) {
    return homeDirectory.resolve("key").toFile();
  }
}
