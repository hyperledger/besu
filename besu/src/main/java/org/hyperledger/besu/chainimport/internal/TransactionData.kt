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
import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.crypto.SECPPrivateKey
import org.hyperledger.besu.crypto.SignatureAlgorithm
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.ethereum.core.Transaction
import java.util.*

/** The Transaction data.  */
@JsonIgnoreProperties("comment")
class TransactionData @JsonCreator constructor(
    @JsonProperty("gasLimit") gasLimit: String,
    @JsonProperty("gasPrice") gasPrice: String?,
    @JsonProperty("data") data: Optional<String?>,
    @JsonProperty("value") value: Optional<String?>,
    @JsonProperty("to") to: Optional<String?>,
    @JsonProperty("secretKey") secretKey: String
) {
    private val gasLimit = UInt256.fromHexString(gasLimit).toLong()
    private val gasPrice: Wei = Wei.fromHexString(gasPrice)
    private val data: Bytes = data.map { str: String? ->
        Bytes.fromHexString(
            str
        )
    }.orElse(Bytes.EMPTY)
    private val value: Wei = value.map { str: String? ->
        Wei.fromHexString(
            str
        )
    }.orElse(Wei.ZERO)
    private val to: Optional<Address?> =
        to.map { str: String? ->
            Address.fromHexString(
                str
            )
        }
    private val privateKey: SECPPrivateKey

    /**
     * Instantiates a new Transaction data.
     *
     * @param gasLimit the gas limit
     * @param gasPrice the gas price
     * @param data the data
     * @param value the value
     * @param to the to
     * @param secretKey the secret key
     */
    init {
        this.privateKey = SIGNATURE_ALGORITHM.get().createPrivateKey(Bytes32.fromHexString(secretKey))
    }

    /**
     * Gets signed transaction.
     *
     * @param nonceProvider the nonce provider
     * @return the signed transaction
     */
    fun getSignedTransaction(nonceProvider: NonceProvider): Transaction {
        val keyPair = SIGNATURE_ALGORITHM.get().createKeyPair(privateKey)

        val fromAddress = Address.extract(keyPair.publicKey)
        val nonce = nonceProvider.get(fromAddress)
        return Transaction.builder()
            .gasLimit(gasLimit)
            .gasPrice(gasPrice)
            .nonce(nonce)
            .payload(data)
            .value(value)
            .to(to.orElse(null))
            .guessType()
            .signAndBuild(keyPair)
    }

    /** The interface Nonce provider.  */
    fun interface NonceProvider {
        /**
         * Get Nonce.
         *
         * @param address the address
         * @return the Nonce
         */
        fun get(address: Address): Long
    }

    companion object {
        private val SIGNATURE_ALGORITHM: Supplier<SignatureAlgorithm> =
            Suppliers.memoize { SignatureAlgorithmFactory.getInstance() }
    }
}
