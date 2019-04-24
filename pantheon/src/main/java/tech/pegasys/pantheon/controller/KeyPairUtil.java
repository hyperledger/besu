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
package tech.pegasys.pantheon.controller;

import tech.pegasys.pantheon.crypto.InvalidSEC256K1PrivateKeyStoreException;
import tech.pegasys.pantheon.crypto.SECP256K1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KeyPairUtil {
  private static final Logger LOG = LogManager.getLogger();

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
