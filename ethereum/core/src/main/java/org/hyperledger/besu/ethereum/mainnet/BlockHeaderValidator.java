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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockHeaderValidator {

  private static final Logger LOG = LoggerFactory.getLogger(BlockHeaderValidator.class);

  private final List<Rule> rules;

  private BlockHeaderValidator(final List<Rule> rules) {
    this.rules = rules;
  }

  public boolean validateHeader(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext protocolContext,
      final HeaderValidationMode mode) {
    switch (mode) {
      case NONE:
        return true;
      case LIGHT_DETACHED_ONLY:
        return applyRules(
            header,
            parent,
            protocolContext,
            rule -> rule.includeInLightValidation() && rule.isDetachedSupported());
      case LIGHT_SKIP_DETACHED:
        return applyRules(
            header,
            parent,
            protocolContext,
            rule -> rule.includeInLightValidation() && !rule.isDetachedSupported());
      case LIGHT:
        return applyRules(header, parent, protocolContext, Rule::includeInLightValidation);
      case DETACHED_ONLY:
        return applyRules(header, parent, protocolContext, Rule::isDetachedSupported);
      case SKIP_DETACHED:
        return applyRules(header, parent, protocolContext, rule -> !rule.isDetachedSupported());
      case FULL:
        return applyRules(header, parent, protocolContext, rule -> true);
    }
    throw new IllegalArgumentException("Unknown HeaderValidationMode: " + mode);
  }

  public boolean validateHeader(
      final BlockHeader header,
      final ProtocolContext protocolContext,
      final HeaderValidationMode mode) {
    if (mode == HeaderValidationMode.NONE) {
      return true;
    }
    return getParent(header, protocolContext)
        .map(parentHeader -> validateHeader(header, parentHeader, protocolContext, mode))
        .orElse(false);
  }

  private boolean applyRules(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext protocolContext,
      final Predicate<Rule> filter) {
    return rules.stream()
        .filter(filter)
        .allMatch(
            rule -> {
              boolean worked = rule.validate(header, parent, protocolContext);
              if (!worked) LOG.debug("{} rule failed", rule.innerRuleClass().getCanonicalName());
              return worked;
            });
  }

  private Optional<BlockHeader> getParent(final BlockHeader header, final ProtocolContext context) {
    final Optional<BlockHeader> parent =
        context.getBlockchain().getBlockHeader(header.getParentHash());
    if (parent.isEmpty()) {
      LOG.trace("Invalid block header: cannot determine parent header");
    }
    return parent;
  }

  private static class Rule {
    private final boolean detachedSupported;
    private final AttachedBlockHeaderValidationRule wrappedRule;
    private final boolean includeInLightValidation;

    private Rule(
        final boolean detachedSupported,
        final AttachedBlockHeaderValidationRule toWrap,
        final boolean includeInLightValidation) {
      this.detachedSupported = detachedSupported;
      this.wrappedRule = toWrap;
      this.includeInLightValidation = includeInLightValidation;
    }

    boolean isDetachedSupported() {
      return detachedSupported;
    }

    public boolean validate(
        final BlockHeader header, final BlockHeader parent, final ProtocolContext protocolContext) {
      return this.wrappedRule.validate(header, parent, protocolContext);
    }

    boolean includeInLightValidation() {
      return includeInLightValidation;
    }

    public Class<? extends AttachedBlockHeaderValidationRule> innerRuleClass() {
      return wrappedRule.getClass();
    }
  }

  public static class Builder {
    private final List<Function<DifficultyCalculator, Rule>> rulesBuilder = new ArrayList<>();
    private DifficultyCalculator difficultyCalculator;

    public Builder addRule(
        final Function<DifficultyCalculator, AttachedBlockHeaderValidationRule> ruleBuilder) {
      this.rulesBuilder.add(
          applyMe -> {
            final AttachedBlockHeaderValidationRule rule = ruleBuilder.apply(applyMe);
            return new Rule(false, rule, rule.includeInLightValidation());
          });
      return this;
    }

    public Builder addRule(final AttachedBlockHeaderValidationRule rule) {
      this.rulesBuilder.add(ignored -> new Rule(false, rule, rule.includeInLightValidation()));
      return this;
    }

    public Builder addRule(final DetachedBlockHeaderValidationRule rule) {
      this.rulesBuilder.add(
          ignored ->
              new Rule(
                  true,
                  (header, parent, protocolContext) -> rule.validate(header, parent),
                  rule.includeInLightValidation()));
      return this;
    }

    public Builder difficultyCalculator(final DifficultyCalculator difficultyCalculator) {
      this.difficultyCalculator = difficultyCalculator;
      return this;
    }

    public BlockHeaderValidator build() {
      final List<Rule> rules = new ArrayList<>();
      rulesBuilder.stream()
          .map(ruleBuilder -> ruleBuilder.apply(difficultyCalculator))
          .forEach(rules::add);
      return new BlockHeaderValidator(rules);
    }
  }
}
