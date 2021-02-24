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
package org.hyperledger.besu.ethereum.p2p.rlpx.handshake.ecies;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.junit.Test;

/** Tests for {@link EncryptedMessage}. */
public final class EncryptedMessageTest {

  @Test
  public void eip8RoundTrip() throws InvalidCipherTextException {
    final KeyPair keyPair = SignatureAlgorithmFactory.getInstance().generateKeyPair();
    final byte[] message = new byte[288];
    ThreadLocalRandom.current().nextBytes(message);
    final Bytes initial = Bytes.wrap(message);
    final Bytes encrypted = EncryptedMessage.encryptMsgEip8(initial, keyPair.getPublicKey());
    final Bytes decrypted =
        EncryptedMessage.decryptMsgEIP8(encrypted, NodeKeyUtils.createFrom(keyPair));
    Assertions.assertThat(decrypted.slice(0, 288)).isEqualTo(initial);
  }
}
