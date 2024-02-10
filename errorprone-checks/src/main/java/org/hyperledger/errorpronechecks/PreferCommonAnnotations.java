/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 * Copyright Hyperledger Besu contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Derived from https://github.com/palantir/gradle-baseline/blob/6fe385a80291473e7fc1441f176454bec4184d6b/baseline-error-prone/src/main/java/com/palantir/baseline/errorprone/PreferCommonAnnotations.java */

package org.hyperledger.errorpronechecks;

import java.util.Map;
import java.util.Objects;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ImportTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ImportTree;
import com.sun.tools.javac.code.Type;

/**
 * Checker that recommends using the common version of an annotation.
 *
 * <p>Examples: - Guava's version of {@code @VisibleForTesting} over other copies.
 */
@AutoService(BugChecker.class)
@BugPattern(
    summary = "Prefer the common version of annotations over other copies.",
    severity = SeverityLevel.WARNING)
public final class PreferCommonAnnotations extends BugChecker implements ImportTreeMatcher {

  /** ClassName -> preferred import. */
  private static final Map<String, String> PREFERRED_IMPORTS =
      Map.of("org.jetbrains.annotations.NotNull", "javax.annotation.Nonnull");

  @Override
  public Description matchImport(ImportTree tree, VisitorState state) {
    Type importType = ASTHelpers.getType(tree.getQualifiedIdentifier());
    if (importType == null) {
      return Description.NO_MATCH;
    }
    String importName = importType.toString();
    for (Map.Entry<String, String> entry : PREFERRED_IMPORTS.entrySet()) {
      String affectedClassName = entry.getKey();
      String preferredType = entry.getValue();
      if (importName.endsWith(affectedClassName) && !Objects.equals(importName, preferredType)) {
        SuggestedFix fix =
            SuggestedFix.builder().removeImport(importName).addImport(preferredType).build();
        return this.buildDescription(tree)
            .setMessage("Do not use " + importName + " use " + preferredType + " instead.")
            .addFix(fix)
            .build();
      }
    }
    return Description.NO_MATCH;
  }
}
