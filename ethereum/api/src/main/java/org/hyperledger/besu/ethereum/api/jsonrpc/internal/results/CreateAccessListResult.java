package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.evm.AccessListEntry;

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
