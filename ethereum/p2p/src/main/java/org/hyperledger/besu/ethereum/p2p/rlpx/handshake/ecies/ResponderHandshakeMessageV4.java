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

import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class ResponderHandshakeMessageV4 implements ResponderHandshakeMessage {

  private final SECPPublicKey ephPublicKey;

  private final Bytes32 nonce;

  public static ResponderHandshakeMessageV4 create(
      final SECPPublicKey ephPublicKey, final Bytes32 nonce) {
    return new ResponderHandshakeMessageV4(ephPublicKey, nonce);
  }

  public static ResponderHandshakeMessageV4 decode(final Bytes raw) {
    final RLPInput input = new BytesValueRLPInput(raw, true);
    input.enterList();
    return new ResponderHandshakeMessageV4(
        SignatureAlgorithmFactory.getInstance().createPublicKey(input.readBytes()),
        input.readBytes32());
  }

  private ResponderHandshakeMessageV4(final SECPPublicKey ephPublicKey, final Bytes32 nonce) {
    this.ephPublicKey = ephPublicKey;
    this.nonce = nonce;
  }

  @Override
  public SECPPublicKey getEphPublicKey() {
    return ephPublicKey;
  }

  @Override
  public Bytes32 getNonce() {
    return nonce;
  }

  @Override
  public Bytes encode() {
    final BytesValueRLPOutput temp = new BytesValueRLPOutput();
    temp.startList();
    temp.writeBytes(ephPublicKey.getEncodedBytes());
    temp.writeBytes(nonce);
    temp.writeIntScalar(InitiatorHandshakeMessageV4.VERSION);
    temp.endList();
    return temp.encoded();
  }
}
