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
package org.hyperledger.besu.cli.custom

import com.google.common.annotations.VisibleForTesting
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl
import picocli.CommandLine
import java.net.URI
import java.util.function.Function

/** The Enode to uri property converter.  */
class EnodeToURIPropertyConverter : CommandLine.ITypeConverter<URI> {
    private val converter: Function<String, URI>

    /** Instantiates a new Enode to uri property converter.  */
    internal constructor() {
        this.converter =
            Function { s: String? -> EnodeURLImpl.fromString(s).toURI() }
    }

    /**
     * Instantiates a new Enode to uri property converter.
     *
     * @param converter the converter
     */
    @VisibleForTesting
    internal constructor(converter: Function<String, URI>) {
        this.converter = converter
    }

    override fun convert(value: String): URI {
        return converter.apply(value)
    }
}
