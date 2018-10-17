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
package tech.pegasys.pantheon.ethereum.p2p.rlpx.handshake.ecies;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class ResponderHandshakeMessageV4 implements ResponderHandshakeMessage {

  private final SECP256K1.PublicKey ephPublicKey;

  private final Bytes32 nonce;

  public static ResponderHandshakeMessageV4 create(
      final SECP256K1.PublicKey ephPublicKey, final Bytes32 nonce) {
    return new ResponderHandshakeMessageV4(ephPublicKey, nonce);
  }

  public static ResponderHandshakeMessageV4 decode(final BytesValue raw) {
    final RLPInput input = new BytesValueRLPInput(raw, true);
    input.enterList();
    return new ResponderHandshakeMessageV4(
        SECP256K1.PublicKey.create(input.readBytesValue()), input.readBytes32());
  }

  private ResponderHandshakeMessageV4(final SECP256K1.PublicKey ephPublicKey, final Bytes32 nonce) {
    this.ephPublicKey = ephPublicKey;
    this.nonce = nonce;
  }

  @Override
  public SECP256K1.PublicKey getEphPublicKey() {
    return ephPublicKey;
  }

  @Override
  public Bytes32 getNonce() {
    return nonce;
  }

  @Override
  public BytesValue encode() {
    final BytesValueRLPOutput temp = new BytesValueRLPOutput();
    temp.startList();
    temp.writeBytesValue(ephPublicKey.getEncodedBytes());
    temp.writeBytesValue(nonce);
    temp.writeIntScalar(InitiatorHandshakeMessageV4.VERSION);
    temp.endList();
    return temp.encoded();
  }
}
