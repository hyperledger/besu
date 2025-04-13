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
package org.hyperledger.besu.cli.options

import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.collect.Range
import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.datatypes.Wei
import java.lang.invoke.MethodType
import java.lang.reflect.InvocationTargetException

/** The Option parser.  */
object OptionParser {
    /**
     * Parse long range range.
     *
     * @param arg the arg
     * @return the range
     */
    @JvmStatic
    fun parseLongRange(arg: String): Range<Long> {
        Preconditions.checkArgument(arg.matches("-?\\d+\\.\\.-?\\d+".toRegex()))
        val ends: Iterator<String> = Splitter.on("..").split(arg).iterator()
        return Range.closed(parseLong(ends.next()), parseLong(ends.next()))
    }

    /**
     * Parse long from String.
     *
     * @param arg long value to parse from String
     * @return the long
     */
    fun parseLong(arg: String): Long {
        return arg.toLong(10)
    }

    /**
     * Format Long values range.
     *
     * @param range the range
     * @return the string
     */
    fun format(range: Range<Long>): String {
        return format(range.lowerEndpoint()) + ".." + format(
            range.upperEndpoint()
        )
    }

    /**
     * Format int to String.
     *
     * @param value the value
     * @return the string
     */
    @JvmStatic
    fun format(value: Int): String {
        return value.toString(10)
    }

    /**
     * Format long to String.
     *
     * @param value the value
     * @return the string
     */
    @JvmStatic
    fun format(value: Long): String {
        return value.toString(10)
    }

    /**
     * Format float to string.
     *
     * @param value the value
     * @return the string
     */
    @JvmStatic
    fun format(value: Float): String {
        return value.toString()
    }

    /**
     * Format UInt256 to string.
     *
     * @param value the value
     * @return the string
     */
    @JvmStatic
    fun format(value: UInt256): String {
        return value.toBigInteger().toString(10)
    }

    /**
     * Format Wei to string.
     *
     * @param value the value
     * @return the string
     */
    @JvmStatic
    fun format(value: Wei): String {
        return format(value.toUInt256())
    }

    /**
     * Format any object to string. This implementation tries to find an existing format method, in
     * this class, that matches the type of the passed object, and if not found just invoke, to string
     * on the passed object
     *
     * @param value the object
     * @return the string
     */
    @JvmStatic
    fun format(value: Any): String {
        var formatMethod = try {
            OptionParser::class.java.getMethod("format", value.javaClass)
        } catch (e: NoSuchMethodException) {
            try {
                // maybe a primitive version of the method exists
                OptionParser::class.java.getMethod(
                    "format", MethodType.methodType(value.javaClass).unwrap().returnType()
                )
            } catch (ex: NoSuchMethodException) {
                return value.toString()
            }
        }

        try {
            return formatMethod.invoke(null, value) as String
        } catch (e: InvocationTargetException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }
}
