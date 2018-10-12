package tech.pegasys.errorpronechecks;

import static com.google.errorprone.BugPattern.Category.JDK;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
  name = "DoNotInvokeMessageDigestDirectly",
  summary = "Do not invoke MessageDigest.getInstance directly.",
  category = JDK,
  severity = WARNING
)
public class DoNotInvokeMessageDigestDirectly extends BugChecker
    implements MethodInvocationTreeMatcher {

  @Override
  public Description matchMethodInvocation(
      final MethodInvocationTree tree, final VisitorState state) {
    if (tree.getMethodSelect().toString().equals("MessageDigest.getInstance")) {
      return describeMatch(tree);
    }
    return Description.NO_MATCH;
  }
}
