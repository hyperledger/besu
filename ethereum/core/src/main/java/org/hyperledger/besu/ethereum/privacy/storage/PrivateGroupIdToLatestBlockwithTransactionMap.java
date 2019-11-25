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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrivateGroupIdToLatestBlockwithTransactionMap {
    private final Map<Bytes32, Hash> map;

    public final static PrivateGroupIdToLatestBlockwithTransactionMap EMPTY = new PrivateGroupIdToLatestBlockwithTransactionMap(new HashMap<>());

    public PrivateGroupIdToLatestBlockwithTransactionMap(final Map<Bytes32, Hash> map) {
        this.map= map;
    }

    public Map<Bytes32, Hash> getMap() {
        return map;
    }

    public void writeTo(final RLPOutput out) {
        out.startList();

        map.entrySet().stream().forEach(e -> {
            out.writeBytesValue(e.getKey());
            out.writeBytesValue(e.getValue());
        });

        out.endList();
    }

    public static PrivateGroupIdToLatestBlockwithTransactionMap readFrom(final RLPInput input) {
        final List<PrivateGroupIdBlockHashMapEntry> entries = input.readList(PrivateGroupIdBlockHashMapEntry::readFrom);

        final HashMap<Bytes32, Hash> map = new HashMap<>();
        entries.stream().forEach(e -> map.put(e.getPrivacyGroup(), e.getBlockHash()));

        return new PrivateGroupIdToLatestBlockwithTransactionMap(map);
    }
}
