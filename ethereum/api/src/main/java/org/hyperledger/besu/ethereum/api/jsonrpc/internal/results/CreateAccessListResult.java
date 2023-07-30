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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.AccessListEntry;

import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;

public class CreateAccessListResult {
  List<AccessListEntry> accessListList;
  String gasUsed;

  public CreateAccessListResult(final List<AccessListEntry> accessListEntries, final long gasUsed) {
    this.accessListList = accessListEntries;
    this.gasUsed = Quantity.create(gasUsed);
  }

  @JsonGetter(value = "accessList")
  public Collection<AccessListEntry> getAccessList() {
    return accessListList;
  }

  @JsonGetter(value = "gasUsed")
  public String getGasUsed() {
    return gasUsed;
  }
}
