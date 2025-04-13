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
package org.hyperledger.besu.cli.converter

import org.hyperledger.besu.cli.converter.exception.DurationConversionException
import picocli.CommandLine
import java.time.Duration

/** The Duration (milliseconds) Cli type converter.  */
class DurationMillisConverter

/** Default constructor.  */
    : CommandLine.ITypeConverter<Duration>, TypeFormatter<Duration?> {
    @Throws(DurationConversionException::class)
    override fun convert(value: String): Duration {
        try {
            val millis = value.toLong()
            if (millis < 0) {
                throw DurationConversionException(millis)
            }
            return Duration.ofMillis(value.toLong())
        } catch (e: NullPointerException) {
            throw DurationConversionException(value)
        } catch (e: IllegalArgumentException) {
            throw DurationConversionException(value)
        }
    }

    override fun format(value: Duration?): String {
        return value?.toMillis().toString()
    }
}
