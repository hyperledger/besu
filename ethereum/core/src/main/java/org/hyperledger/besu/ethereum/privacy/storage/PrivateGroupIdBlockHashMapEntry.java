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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.privacy.storage;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.Bytes32;

public class PrivateGroupIdBlockHashMapEntry {
    private final Bytes32 privacyGroup;
    private final Hash blockHash;

    public PrivateGroupIdBlockHashMapEntry(final Bytes32 privacyGroup, final Hash blockHash) {
        this.privacyGroup = privacyGroup;
        this.blockHash = blockHash;
    }

    public Hash getBlockHash() {
        return blockHash;
    }

    public Bytes32 getPrivacyGroup() {
        return privacyGroup;
    }

    public void writeTo(final RLPOutput out) {
        out.startList();

        out.writeBytesValue(privacyGroup);
        out.writeBytesValue(blockHash);

        out.endList();
    }

    public static PrivateGroupIdBlockHashMapEntry readFrom(final RLPInput input) {
        input.enterList();

        final PrivateGroupIdBlockHashMapEntry privateTransactionMetadata =
                new PrivateGroupIdBlockHashMapEntry(input.readBytes32(), Hash.wrap(input.readBytes32()));

        input.leaveList();
        return privateTransactionMetadata;
    }
}
