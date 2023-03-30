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

import org.apache.tuweni.bytes.Bytes32;

public class NodeKeyUtils {

  public static NodeKey createFrom(final KeyPair keyPair) {
    return new NodeKey(new KeyPairSecurityModule(keyPair));
  }

  public static NodeKey createFrom(final Bytes32 privateKey) {
    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    final KeyPair keyPair =
        signatureAlgorithm.createKeyPair(signatureAlgorithm.createPrivateKey(privateKey));
    return new NodeKey(new KeyPairSecurityModule(keyPair));
  }

  public static NodeKey generate() {
    return new NodeKey(
        new KeyPairSecurityModule(SignatureAlgorithmFactory.getInstance().generateKeyPair()));
  }
}
