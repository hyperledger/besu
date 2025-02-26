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
package org.hyperledger.besu.util.era1;

import java.util.List;

/**
 * Represents a block index in an era1 file
 *
 * @param startingBlockIndex The first blockIndex number indexed by this block index
 * @param indexes The indexes of the blocks indexed by this block index
 */
public record Era1BlockIndex(long startingBlockIndex, List<Long> indexes) {}
