/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.debug;

/**
 * Defines the available tracer types for debug_trace* methods.
 *
 * <p>This enum represents the internal tracers that can be used with Ethereum's debug tracing
 * functionality to analyze transaction execution and state changes.
 */
public enum TracerType {
  /** Default opcode-level tracer that provides detailed execution steps */
  OPCODE_TRACER("", "Default Opcode Tracer"),

  /** Call tracer that tracks contract calls and their hierarchy */
  CALL_TRACER("callTracer", "Call Tracer"),

  /** Flat call tracer that provides a flattened view of all calls */
  FLAT_CALL_TRACER("flatCallTracer", "Flat Call Tracer"),

  /** Prestate tracer that captures account states before transaction execution */
  PRESTATE_TRACER("prestateTracer", "Prestate Tracer");

  private final String value;
  private final String displayName;

  /**
   * Constructs a TracerType with the specified values.
   *
   * @param value the string representation used in debug_trace* method calls
   * @param displayName the human-readable name for this tracer
   */
  TracerType(final String value, final String displayName) {
    this.value = value;
    this.displayName = displayName;
  }

  /**
   * Returns the string value associated with this tracer type.
   *
   * <p>This value is used when making debug_trace* method calls to specify which tracer should be
   * used for transaction analysis.
   *
   * @return the string representation of this tracer type
   */
  public String getValue() {
    return value;
  }

  /**
   * Returns the display name for this tracer type.
   *
   * @return the human-readable name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Converts a string value to the corresponding TracerType enum.
   *
   * <p>This method performs a case-sensitive lookup to find the matching TracerType based on the
   * provided string value.
   *
   * @param value the string value to convert to TracerType
   * @return the corresponding TracerType enum
   * @throws IllegalArgumentException if the value is null or doesn't match any TracerType
   * @see #getValue()
   */
  public static TracerType fromString(final String value) {
    if (value == null) {
      throw new IllegalArgumentException("TracerType value cannot be null");
    }

    for (TracerType type : TracerType.values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }

    throw new IllegalArgumentException(
        "Invalid TracerType: " + value + ". Valid values are: " + getValidValues());
  }

  /**
   * Returns a comma-separated string of all valid tracer type values.
   *
   * <p>This is primarily used for error messages to help users understand what values are
   * acceptable.
   *
   * @return a string containing all valid tracer type values
   */
  private static String getValidValues() {
    StringBuilder sb = new StringBuilder();
    TracerType[] values = TracerType.values();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("'").append(values[i].getValue()).append("'");
    }
    return sb.toString();
  }
}
