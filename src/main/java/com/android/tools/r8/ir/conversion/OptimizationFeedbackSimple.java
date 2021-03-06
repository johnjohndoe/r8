// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ParameterUsagesInfo;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import java.util.BitSet;
import java.util.Set;

public class OptimizationFeedbackSimple implements OptimizationFeedback {

  @Override
  public synchronized void markInlinedIntoSingleCallSite(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void methodInitializesClassesOnNormalExit(
      DexEncodedMethod method, Set<DexType> initializedClasses) {
    // Ignored.
  }

  @Override
  public void methodReturnsArgument(DexEncodedMethod method, int argument) {
    // Ignored.
  }

  @Override
  public void methodReturnsConstantNumber(DexEncodedMethod method, long value) {
    // Ignored.
  }

  @Override
  public void methodReturnsConstantString(DexEncodedMethod method, DexString value) {
    // Ignored.
  }

  @Override
  public void methodReturnsObjectOfType(DexEncodedMethod method, TypeLatticeElement type) {
    // Ignored.
  }

  @Override
  public void methodMayNotHaveSideEffects(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void methodNeverReturnsNull(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void methodNeverReturnsNormally(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void markAsPropagated(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {
    // Just as processed, don't provide any inlining constraints.
    method.markProcessed(ConstraintWithTarget.NEVER);
  }

  @Override
  public void markUseIdentifierNameString(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().markUseIdentifierNameString();
  }

  @Override
  public void markCheckNullReceiverBeforeAnySideEffect(DexEncodedMethod method, boolean mark) {
    // Ignored.
  }

  @Override
  public void markTriggerClassInitBeforeAnySideEffect(DexEncodedMethod method, boolean mark) {
    // Ignored.
  }

  @Override
  public void setClassInlinerEligibility(
      DexEncodedMethod method, ClassInlinerEligibility eligibility) {
    // Ignored.
  }

  @Override
  public void setTrivialInitializer(DexEncodedMethod method, TrivialInitializer info) {
    // Ignored.
  }

  @Override
  public void setInitializerEnablingJavaAssertions(DexEncodedMethod method) {
    method.getMutableOptimizationInfo().setInitializerEnablingJavaAssertions();
  }

  @Override
  public void setParameterUsages(DexEncodedMethod method, ParameterUsagesInfo parameterUsagesInfo) {
    // Ignored.
  }

  @Override
  public void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts) {
    // Ignored.
  }

  @Override
  public void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts) {
    // Ignored.
  }

  @Override
  public void classInitializerMayBePostponed(DexEncodedMethod method) {
    // Ignored.
  }
}
