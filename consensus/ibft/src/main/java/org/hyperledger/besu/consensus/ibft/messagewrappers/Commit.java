/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft.messagewrappers;

import org.hyperledger.besu.consensus.ibft.payload.CommitPayload;
import org.hyperledger.besu.consensus.ibft.payload.SignedData;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.util.bytes.BytesValue;

public class Commit extends IbftMessage<CommitPayload> {

  public Commit(final SignedData<CommitPayload> payload) {
    super(payload);
  }

  public Signature getCommitSeal() {
    return getPayload().getCommitSeal();
  }

  public Hash getDigest() {
    return getPayload().getDigest();
  }

  public static Commit decode(final BytesValue data) {
    return new Commit(SignedData.readSignedCommitPayloadFrom(RLP.input(data)));
  }
}
