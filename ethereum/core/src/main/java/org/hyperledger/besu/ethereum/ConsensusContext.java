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
package org.hyperledger.besu.ethereum;

/**
 * The ConsensusContext interface defines a method for casting the consensus context to a specific
 * class.
 */
@FunctionalInterface
public interface ConsensusContext {

  /**
   * Casts the consensus context to the specified class.
   *
   * @param <C> the type of the class to cast the consensus context to
   * @param klass the class to cast the consensus context to
   * @return the consensus context cast to the specified class
   */
  <C extends ConsensusContext> C as(final Class<C> klass);
}
