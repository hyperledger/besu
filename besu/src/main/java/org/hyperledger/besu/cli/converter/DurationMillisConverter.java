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
package org.hyperledger.besu.cli.converter;

import org.hyperledger.besu.cli.converter.exception.DurationConversionException;

import java.time.Duration;

import picocli.CommandLine;

/** The Duration (milliseconds) Cli type converter. */
public class DurationMillisConverter
    implements CommandLine.ITypeConverter<Duration>, TypeFormatter<Duration> {

  /** Default constructor. */
  public DurationMillisConverter() {}

  @Override
  public Duration convert(final String value) throws DurationConversionException {
    try {
      final long millis = Long.parseLong(value);
      if (millis < 0) {
        throw new DurationConversionException(millis);
      }
      return Duration.ofMillis(Long.parseLong(value));
    } catch (NullPointerException | IllegalArgumentException e) {
      throw new DurationConversionException(value);
    }
  }

  @Override
  public String format(final Duration value) {
    return Long.toString(value.toMillis());
  }
}
