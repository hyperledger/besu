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
package org.hyperledger.besu.util

import java.util.function.Function

/** some useful tools to display strings in command line help or error messages  */
object StringUtils {
    /**
     * Joins a list into string elements with a delimiter but having a last different delimiter
     * Example: "this thing, that thing and this other thing"
     *
     * @param delimiter delimiter for all the items except before the last one
     * @param lastDelimiter delimiter before the last item
     * @return a delimited string representation of the list
     */
    @JvmStatic
    fun joiningWithLastDelimiter(
        delimiter: String, lastDelimiter: String
    ): Function<List<String?>, String> {
        return Function { list: List<String?> ->
            val last = list.size - 1
            if (last < 1) return@Function java.lang.String.join(delimiter, list)
            java.lang.String.join(
                lastDelimiter, java.lang.String.join(delimiter, list.subList(0, last)), list[last]
            )
        }
    }
}
