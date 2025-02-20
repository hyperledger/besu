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
package org.hyperledger.besu.util.e2;

import java.util.List;

/**
 * Represents a slot index in an E2 file
 *
 * @param startingSlot The first slot number indexed by this slot index
 * @param indexes The indexes of the slots indexed by this slot index
 */
public record E2SlotIndex(long startingSlot, List<Long> indexes) {}
