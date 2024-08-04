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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Enclave key utils. */
public class EnclaveKeyUtils {
  private static final Logger LOG = LoggerFactory.getLogger(EnclaveKeyUtils.class);

  /** Default constructor */
  private EnclaveKeyUtils() {}

  /**
   * Utility method to load the enclave public key. Possible input values are the names of the *.pub
   * files in the resources folder.
   *
   * @param keyFileName the name of the file containing the enclave public key
   * @return the enclave public key stored in that file
   * @throws IOException throws if key not found
   */
  public static String loadKey(final String keyFileName) throws IOException {
    InputStream is = EnclaveKeyUtils.class.getResourceAsStream("/" + keyFileName);
    InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
    try (BufferedReader reader = new BufferedReader(streamReader)) {
      return reader.readLine();
    }
  }

  /**
   * Generate key pair.
   *
   * @return the key pair
   * @throws NoSuchAlgorithmException the no such algorithm exception
   */
  public static KeyPair generateKeys() throws NoSuchAlgorithmException {
    final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    final PublicKey pubKey = keyPair.getPublic();
    final PrivateKey privKey = keyPair.getPrivate();

    LOG.debug("pubkey      : " + pubKey);
    LOG.debug("pubkey bytes: " + Bytes.wrap(pubKey.getEncoded()).toHexString());
    LOG.debug("pubkey b64  : " + Base64.getEncoder().encodeToString(pubKey.getEncoded()));

    LOG.debug("privkey      : " + privKey);
    LOG.debug("privkey bytes: " + Bytes.wrap(privKey.getEncoded()).toHexString());
    LOG.debug("privkey b64  : " + Base64.getEncoder().encodeToString(privKey.getEncoded()));

    return keyPair;
  }
}
