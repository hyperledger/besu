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
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.annotation.Nonnull

/** The Cors allowed origins property used in CLI  */
class CorsAllowedOriginsProperty
/** Instantiates a new Cors allowed origins property.  */
    : AbstractList<String>() {
    private val domains: MutableList<String> = ArrayList()

    @Nonnull
    override fun iterator(): MutableIterator<String> {
        return if (domains.size == 1 && domains[0] == "none") {
            Collections.emptyIterator()
        } else {
            domains.iterator()
        }
    }

    override fun add(string: String): Boolean {
        return addAll(setOf(string))
    }

    override fun get(index: Int): String {
        return domains[index]
    }

    override val size: Int
        get() = domains.size

    override fun addAll(collection: Collection<String>): Boolean {
        val initialSize = domains.size
        for (string in collection) {
            require(!Strings.isNullOrEmpty(string)) { "Domain cannot be empty string or null string." }
            for (s in Splitter.onPattern("\\s*,+\\s*").split(string)) {
                if ("all" == s) {
                    domains.add("*")
                } else {
                    domains.add(s)
                }
            }
        }

        if (domains.contains("none")) {
            require(domains.size <= 1) { "Value 'none' can't be used with other domains" }
        } else if (domains.contains("*")) {
            require(domains.size <= 1) { "Values '*' or 'all' can't be used with other domains" }
        } else {
            try {
                val stringJoiner = StringJoiner("|")
                domains.forEach(Consumer { newElement: String? -> stringJoiner.add(newElement) })
                Pattern.compile(stringJoiner.toString())
            } catch (e: PatternSyntaxException) {
                throw IllegalArgumentException("Domain values result in invalid regex pattern", e)
            }
        }

        return domains.size != initialSize
    }
}
