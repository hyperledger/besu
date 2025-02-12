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
package org.hyperledger.besu.util.log4j.plugin;

import java.util.Arrays;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/** Matches a text in the stack trace */
@Plugin(
    name = "StackTraceMatchFilter",
    category = "Core",
    elementType = "filter",
    printObject = true)
public class StackTraceMatchFilter extends AbstractFilter {
  private final String text;

  private StackTraceMatchFilter(final String text, final Result onMatch, final Result onMismatch) {
    super(onMatch, onMismatch);
    this.text = text;
  }

  @Override
  public Result filter(
      final Logger logger,
      final Level level,
      final Marker marker,
      final Object msg,
      final Throwable t) {
    return filter(t);
  }

  @Override
  public Result filter(
      final Logger logger,
      final Level level,
      final Marker marker,
      final Message msg,
      final Throwable t) {
    return filter(t);
  }

  @Override
  public Result filter(final LogEvent event) {
    return filter(event.getThrown());
  }

  private Result filter(final Throwable t) {
    if (t != null) {
      return Arrays.stream(t.getStackTrace())
              .map(StackTraceElement::getClassName)
              .anyMatch(cn -> cn.contains(text))
          ? onMatch
          : onMismatch;
    }
    return Result.NEUTRAL;
  }

  @Override
  public String toString() {
    return text;
  }

  /**
   * Create a new builder
   *
   * @return a new builder
   */
  @PluginBuilderFactory
  public static StackTraceMatchFilter.Builder newBuilder() {
    return new StackTraceMatchFilter.Builder();
  }

  /** Builder for StackTraceMatchFilter */
  public static class Builder extends AbstractFilterBuilder<StackTraceMatchFilter.Builder>
      implements org.apache.logging.log4j.core.util.Builder<StackTraceMatchFilter> {
    @PluginBuilderAttribute private String text = "";

    /** Default constructor */
    public Builder() {
      // here to make javadoc happy
    }

    /**
     * Set the string to match in the stack trace
     *
     * @param text the match string
     * @return this builder
     */
    public StackTraceMatchFilter.Builder setMatchString(final String text) {
      this.text = text;
      return this;
    }

    @Override
    public StackTraceMatchFilter build() {
      return new StackTraceMatchFilter(this.text, this.getOnMatch(), this.getOnMismatch());
    }
  }
}
