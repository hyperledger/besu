/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.flat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldView;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.verkle.node.StemNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the various pieces of information retrieved when reading the basic data section of an account in Verkle trie.
 * This includes the account's version, balance, nonce, and code size, as well as the storage header.
 * <p>
 * This class serves as a container for these essential pieces of account data, providing a structured way to access
 * each component retrieved from a basic data section in the Verkle trie.
 */
@SuppressWarnings("unused")
public class FlatBasicData {
    private int version;

    private VerkleAccount verkleAccount;

    protected final Map<UInt256, UInt256> headerStorage = new HashMap<>();

    public int getVersion() {
        return version;
    }

    public VerkleAccount getVerkleAccount() {
        return verkleAccount;
    }

    public Map<UInt256, UInt256> getHeaderStorage() {
        return headerStorage;
    }

    public static Bytes getAccountEncodedValue(final Bytes encoded){
        return new StemNode<>(Bytes.EMPTY,Bytes.EMPTY).getEncodedValue();
    }
    public static FlatBasicData fromRLP(
            final DiffBasedWorldView context,
            final Address address,
            final Bytes encoded,
            final boolean mutable)
            throws RLPException {
        final RLPInput in = RLP.input(encoded);
        in.enterList();

        in.leaveList();

        return new FlatBasicData();
    }
}
