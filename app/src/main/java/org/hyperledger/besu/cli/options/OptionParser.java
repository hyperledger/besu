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
package org.hyperledger.besu.cli.options;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Wei;

import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

import com.google.common.base.Splitter;
import com.google.common.collect.Range;
import org.apache.tuweni.units.bigints.UInt256;

/** The Option parser. */
public class OptionParser {
  /** Default Constructor. */
  OptionParser() {}

  /**
   * Parse long range range.
   *
   * @param arg the arg
   * @return the range
   */
  public static Range<Long> parseLongRange(final String arg) {
    checkArgument(arg.matches("-?\\d+\\.\\.-?\\d+"));
    final Iterator<String> ends = Splitter.on("..").split(arg).iterator();
    return Range.closed(parseLong(ends.next()), parseLong(ends.next()));
  }

  /**
   * Parse long from String.
   *
   * @param arg long value to parse from String
   * @return the long
   */
  public static long parseLong(final String arg) {
    return Long.parseLong(arg, 10);
  }

  /**
   * Format Long values range.
   *
   * @param range the range
   * @return the string
   */
  public static String format(final Range<Long> range) {
    return format(range.lowerEndpoint()) + ".." + format(range.upperEndpoint());
  }

  /**
   * Format int to String.
   *
   * @param value the value
   * @return the string
   */
  public static String format(final int value) {
    return Integer.toString(value, 10);
  }

  /**
   * Format long to String.
   *
   * @param value the value
   * @return the string
   */
  public static String format(final long value) {
    return Long.toString(value, 10);
  }

  /**
   * Format float to string.
   *
   * @param value the value
   * @return the string
   */
  public static String format(final float value) {
    return Float.toString(value);
  }

  /**
   * Format UInt256 to string.
   *
   * @param value the value
   * @return the string
   */
  public static String format(final UInt256 value) {
    return value.toBigInteger().toString(10);
  }

  /**
   * Format Wei to string.
   *
   * @param value the value
   * @return the string
   */
  public static String format(final Wei value) {
    return format(value.toUInt256());
  }

  /**
   * Format any object to string. This implementation tries to find an existing format method, in
   * this class, that matches the type of the passed object, and if not found just invoke, to string
   * on the passed object
   *
   * @param value the object
   * @return the string
   */
  public static String format(final Object value) {
    Method formatMethod;
    try {
      formatMethod = OptionParser.class.getMethod("format", value.getClass());
    } catch (NoSuchMethodException e) {
      try {
        // maybe a primitive version of the method exists
        formatMethod =
            OptionParser.class.getMethod(
                "format", MethodType.methodType(value.getClass()).unwrap().returnType());
      } catch (NoSuchMethodException ex) {
        return value.toString();
      }
    }

    try {
      return (String) formatMethod.invoke(null, value);
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
