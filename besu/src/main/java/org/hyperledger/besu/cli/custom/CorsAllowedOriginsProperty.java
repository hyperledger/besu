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
package org.hyperledger.besu.cli.custom;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.Nonnull;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

public class CorsAllowedOriginsProperty extends AbstractList<String> {

  private final List<String> domains = new ArrayList<>();

  public CorsAllowedOriginsProperty() {}

  @Override
  @Nonnull
  public Iterator<String> iterator() {
    if (domains.size() == 1 && domains.get(0).equals("none")) {
      return Collections.emptyIterator();
    } else {
      return domains.iterator();
    }
  }

  @Override
  public int size() {
    return domains.size();
  }

  @Override
  public boolean add(final String string) {
    return addAll(Collections.singleton(string));
  }

  @Override
  public String get(final int index) {
    return domains.get(index);
  }

  @Override
  public boolean addAll(final Collection<? extends String> collection) {
    final int initialSize = domains.size();
    for (final String string : collection) {
      if (Strings.isNullOrEmpty(string)) {
        throw new IllegalArgumentException("Domain cannot be empty string or null string.");
      }
      for (final String s : Splitter.onPattern("\\s*,+\\s*").split(string)) {
        if ("all".equals(s)) {
          domains.add("*");
        } else {
          domains.add(s);
        }
      }
    }

    if (domains.contains("none")) {
      if (domains.size() > 1) {
        throw new IllegalArgumentException("Value 'none' can't be used with other domains");
      }
    } else if (domains.contains("*")) {
      if (domains.size() > 1) {
        throw new IllegalArgumentException("Values '*' or 'all' can't be used with other domains");
      }
    } else {
      try {
        final StringJoiner stringJoiner = new StringJoiner("|");
        domains.forEach(stringJoiner::add);
        Pattern.compile(stringJoiner.toString());
      } catch (final PatternSyntaxException e) {
        throw new IllegalArgumentException("Domain values result in invalid regex pattern", e);
      }
    }

    return domains.size() != initialSize;
  }
}
