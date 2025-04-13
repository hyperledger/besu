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

import com.google.common.base.Splitter
import com.google.common.base.Strings
import java.util.*
import javax.annotation.Nonnull

/** The Json Rpc allowlist hosts list for CLI option.  */
class JsonRPCAllowlistHostsProperty
/** Instantiates a new Json rpc allowlist hosts property.  */
    : AbstractList<String>() {
    private val hostnamesAllowlist: MutableList<String> = ArrayList()

    @Nonnull
    override fun iterator(): MutableIterator<String> {
        return if (hostnamesAllowlist.size == 1 && hostnamesAllowlist[0] == "none") {
            Collections.emptyIterator()
        } else {
            hostnamesAllowlist.iterator()
        }
    }

    override fun add(string: String): Boolean {
        return addAll(setOf(string))
    }

    override fun get(index: Int): String {
        return hostnamesAllowlist[index]
    }

    override val size: Int
        get() = hostnamesAllowlist.size

    override fun addAll(collection: Collection<String>): Boolean {
        val initialSize = hostnamesAllowlist.size
        for (string in collection) {
            require(!Strings.isNullOrEmpty(string)) { "Hostname cannot be empty string or null string." }
            for (s in Splitter.onPattern("\\s*,+\\s*").split(string)) {
                if ("all" == s) {
                    hostnamesAllowlist.add("*")
                } else {
                    hostnamesAllowlist.add(s)
                }
            }
        }

        if (hostnamesAllowlist.contains("none")) {
            require(hostnamesAllowlist.size <= 1) { "Value 'none' can't be used with other hostnames" }
        } else if (hostnamesAllowlist.contains("*")) {
            require(hostnamesAllowlist.size <= 1) { "Values '*' or 'all' can't be used with other hostnames" }
        }

        return hostnamesAllowlist.size != initialSize
    }
}
