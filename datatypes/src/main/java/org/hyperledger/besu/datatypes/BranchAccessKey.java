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
package org.hyperledger.besu.datatypes;

import org.apache.tuweni.units.bigints.UInt256;

/**
 * The access event key corresponding to a branch (stem) access in the stateless trie.
 *
 * @param address address of the account being accessed.
 * @param treeIndex stateless trie index of the branch being accessed, also called stem hash.
 */
public record BranchAccessKey(Address address, UInt256 treeIndex) {}
