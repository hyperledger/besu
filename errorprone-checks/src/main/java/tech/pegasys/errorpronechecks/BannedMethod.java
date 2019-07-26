/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.errorpronechecks;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;

import java.util.Map;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
    name = "BannedMethod",
    summary = "Some methods should not be used, make sure that doesn't happen.",
    severity = WARNING)
public class BannedMethod extends BugChecker implements MethodInvocationTreeMatcher {

  private static final ImmutableMap<Matcher<ExpressionTree>, String> BANNED_METHOD_LIST =
      ImmutableMap.of(
          allOf(staticMethod().onClass("com.google.common.base.Objects").withAnyName()),
          "Do not use com.google.common.base.Objects methods, use java.util.Objects methods instead.");

  @Override
  public Description matchMethodInvocation(
      final MethodInvocationTree tree, final VisitorState state) {
    for (final Map.Entry<Matcher<ExpressionTree>, String> entry : BANNED_METHOD_LIST.entrySet()) {
      if (entry.getKey().matches(tree, state)) {
        return buildDescriptionFromChecker(tree, this).setMessage(entry.getValue()).build();
      }
    }
    return NO_MATCH;
  }
}
