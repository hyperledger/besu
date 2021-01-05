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
package org.hyperledger.besu.consensus.qbft.payload;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.Payload;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;

import org.apache.tuweni.bytes.Bytes;

public class MessageFactory {

  private final NodeKey nodeKey;

  public MessageFactory(final NodeKey nodeKey) {
    this.nodeKey = nodeKey;
  }

  public Prepare createPrepare(final ConsensusRoundIdentifier roundIdentifier, final Hash digest) {
    final PreparePayload payload = new PreparePayload(roundIdentifier, digest);
    return new Prepare(createSignedMessage(payload));
  }

  public Commit createCommit(
      final ConsensusRoundIdentifier roundIdentifier,
      final Hash digest,
      final Signature commitSeal) {
    final CommitPayload payload = new CommitPayload(roundIdentifier, digest, commitSeal);
    return new Commit(createSignedMessage(payload));
  }

  private <M extends Payload> SignedData<M> createSignedMessage(final M payload) {
    final Signature signature = nodeKey.sign(hashForSignature(payload));
    return new SignedData<>(payload, Util.publicKeyToAddress(nodeKey.getPublicKey()), signature);
  }

  public static Hash hashForSignature(final Payload unsignedMessageData) {
    return Hash.hash(
        Bytes.concatenate(
            Bytes.of(unsignedMessageData.getMessageType()), unsignedMessageData.encoded()));
  }
}
