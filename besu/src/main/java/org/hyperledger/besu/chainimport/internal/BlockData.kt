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
package org.hyperledger.besu.chainimport.internal

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.core.Transaction
import org.hyperledger.besu.evm.account.Account
import org.hyperledger.besu.evm.worldstate.WorldState
import java.util.*
import java.util.stream.Stream

/**
 * Represents BlockData used in ChainData. Meant to be constructed by Json serializer/deserializer.
 */
@JsonIgnoreProperties("comment")
class BlockData @JsonCreator constructor(
    @JsonProperty("number") number: Optional<String?>,
    @JsonProperty("parentHash") parentHash: Optional<String?>,
    @JsonProperty("coinbase") coinbase: Optional<String?>,
    @JsonProperty("extraData") extraData: Optional<String?>,
    @param:JsonProperty("transactions") private val transactionData: List<TransactionData>
) {
    /**
     * Gets number.
     *
     * @return the number
     */
    val number: Optional<Long> =
        number.map { str: String? -> UInt256.fromHexString(str) }.map { obj: UInt256 -> obj.toLong() }

    /**
     * Gets parent hash.
     *
     * @return the parent hash
     */
    val parentHash: Optional<Hash> =
        parentHash.map { str: String? ->
            Bytes32.fromHexString(
                str
            )
        }.map { bytes: Bytes32? ->
            Hash.wrap(
                bytes
            )
        }

    /**
     * Gets coinbase.
     *
     * @return the coinbase
     */
    val coinbase: Optional<Address> =
        coinbase.map { str: String? ->
            Address.fromHexString(
                str
            )
        }

    /**
     * Gets extra data.
     *
     * @return the extra data
     */
    val extraData: Optional<Bytes> =
        extraData.map { str: String? ->
            Bytes.fromHexStringLenient(
                str
            )
        }

    /**
     * Stream transactions.
     *
     * @param worldState the world state
     * @return the stream of Transaction
     */
    fun streamTransactions(worldState: WorldState): Stream<Transaction> {
        val nonceProvider = getNonceProvider(worldState)
        return transactionData.stream().map { tx: TransactionData -> tx.getSignedTransaction(nonceProvider) }
    }

    /**
     * Gets nonce provider.
     *
     * @param worldState the world state
     * @return the nonce provider
     */
    fun getNonceProvider(worldState: WorldState): TransactionData.NonceProvider {
        val currentNonceValues = HashMap<Address, Long>()
        return TransactionData.NonceProvider { address: Address ->
            currentNonceValues.compute(
                address
            ) { addr: Address?, currentValue: Long? ->
                if (currentValue == null) {
                    return@compute Optional.ofNullable<Account>(worldState[address])
                        .map<Long> { obj: Account -> obj.nonce }
                        .orElse(0L)
                }
                currentValue + 1
            }!!
        }
    }
}
