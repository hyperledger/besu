/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.plugin.data;

import tech.pegasys.pantheon.plugin.Unstable;

/**
 * An interface to mark the {@link BinaryData} that also represents a disceete quantity, such as an
 * unsigned integer value.
 */
@Unstable
public interface Quantity extends BinaryData {

  /**
   * Returns the numeric value of the quantity.
   *
   * <p>The specific class returned may be the boxed Java primitives, however plugin authors should
   * not rely on the underlying number always being castable to that primitive in all cases and
   * should instead rely on APIs such as {@link Number#longValue()} to cast to primitive values.
   * Similarly the underlying object based values may evolve over time.
   *
   * @return The boxed or object based value of the quantity.
   */
  Number getValue();
}
