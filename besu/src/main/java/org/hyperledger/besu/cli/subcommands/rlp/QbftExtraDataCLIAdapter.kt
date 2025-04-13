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
package org.hyperledger.besu.cli.subcommands.rlp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.consensus.common.bft.BftExtraData
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec
import org.hyperledger.besu.datatypes.Address
import java.io.IOException
import java.util.stream.Collectors

/** Adapter to convert a typed JSON of addresses to a QBFT RLP extra data encoding  */
class QbftExtraDataCLIAdapter
/** Default Constructor.  */
    : JSONToRLP {
    @Throws(IOException::class)
    override fun encode(json: String?): Bytes? {
        return fromJsonAddresses(json)
    }

    @Throws(IOException::class)
    private fun fromJsonAddresses(jsonAddresses: String?): Bytes {
        val validatorAddresses = MAPPER.readValue(jsonAddresses, TYPE_REF)
        return QbftExtraDataCodec.encodeFromAddresses(
            validatorAddresses!!.stream().map { str: String? -> Address.fromHexString(str) }.collect(Collectors.toList())
        )
    }

    @Throws(IOException::class)
    override fun decode(rlpInput: String?): BftExtraData? {
        return fromRLPInput(rlpInput!!)
    }

    @Throws(IOException::class)
    private fun fromRLPInput(rlpInput: String): BftExtraData {
        return QbftExtraDataCodec().decodeRaw(Bytes.fromHexString(rlpInput))
    }

    companion object {
        private val MAPPER = ObjectMapper()
        private val TYPE_REF: TypeReference<Collection<String?>?> = object : TypeReference<Collection<String?>?>() {}
    }
}
