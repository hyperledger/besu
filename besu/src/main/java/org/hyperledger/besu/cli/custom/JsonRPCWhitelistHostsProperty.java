/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.cli.custom;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

public class JsonRPCWhitelistHostsProperty extends AbstractList<String> {

  private final List<String> hostnamesWhitelist = new ArrayList<>();

  public JsonRPCWhitelistHostsProperty() {}

  @Override
  @Nonnull
  public Iterator<String> iterator() {
    if (hostnamesWhitelist.size() == 1 && hostnamesWhitelist.get(0).equals("none")) {
      return Collections.emptyIterator();
    } else {
      return hostnamesWhitelist.iterator();
    }
  }

  @Override
  public int size() {
    return hostnamesWhitelist.size();
  }

  @Override
  public boolean add(final String string) {
    return addAll(Collections.singleton(string));
  }

  @Override
  public String get(final int index) {
    return hostnamesWhitelist.get(index);
  }

  @Override
  public boolean addAll(final Collection<? extends String> collection) {
    final int initialSize = hostnamesWhitelist.size();
    for (final String string : collection) {
      if (Strings.isNullOrEmpty(string)) {
        throw new IllegalArgumentException("Hostname cannot be empty string or null string.");
      }
      for (final String s : Splitter.onPattern("\\s*,+\\s*").split(string)) {
        if ("all".equals(s)) {
          hostnamesWhitelist.add("*");
        } else {
          hostnamesWhitelist.add(s);
        }
      }
    }

    if (hostnamesWhitelist.contains("none")) {
      if (hostnamesWhitelist.size() > 1) {
        throw new IllegalArgumentException("Value 'none' can't be used with other hostnames");
      }
    } else if (hostnamesWhitelist.contains("*")) {
      if (hostnamesWhitelist.size() > 1) {
        throw new IllegalArgumentException(
            "Values '*' or 'all' can't be used with other hostnames");
      }
    }

    return hostnamesWhitelist.size() != initialSize;
  }
}
