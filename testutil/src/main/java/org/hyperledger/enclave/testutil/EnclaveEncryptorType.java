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

/** The enum Enclave encryptor type. */
public enum EnclaveEncryptorType {
  /** Nacl enclave encryptor type. */
  NACL,
  /** Ec enclave encryptor type. */
  EC,
  /** Noop enclave encryptor type. */
  NOOP;

  /**
   * To tessera encryptor config json string.
   *
   * @return the string
   */
  public String toTesseraEncryptorConfigJSON() {
    switch (this) {
      case NACL:
        return "    \"encryptor\":{\n"
            + "        \"type\":\"NACL\",\n"
            + "        \"properties\":{\n"
            + "        }\n"
            + "    },\n";
      case EC:
        return "    \"encryptor\":{\n"
            + "        \"type\":\"EC\",\n"
            + "        \"properties\":{\n"
            + "            \"symmetricCipher\": \"AES/GCM/NoPadding\",\n"
            + "            \"ellipticCurve\": \"secp256r1\",\n"
            + "            \"nonceLength\": \"24\",\n"
            + "            \"sharedKeyLength\": \"32\"\n"
            + "        }\n"
            + "    },\n";
      default:
        return "";
    }
  }
}
