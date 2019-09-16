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
package org.hyperledger.besu.consensus.ibft.payload;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

public interface Payload extends RoundSpecific {

  void writeTo(final RLPOutput rlpOutput);

  default BytesValue encoded() {
    BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    writeTo(rlpOutput);

    return rlpOutput.encoded();
  }

  int getMessageType();

  static Hash readDigest(final RLPInput ibftMessageData) {
    return Hash.wrap(ibftMessageData.readBytes32());
  }
}
