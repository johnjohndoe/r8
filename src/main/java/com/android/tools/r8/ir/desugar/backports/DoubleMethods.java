// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;

public final class DoubleMethods extends TemplateMethodCode {
  public DoubleMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static int hashCode(double d) {
    long l = Double.doubleToLongBits(d);
    return (int) (l ^ (l >>> 32));
  }

  public static double max(double a, double b) {
    return Math.max(a, b);
  }

  public static double min(double a, double b) {
    return Math.min(a, b);
  }

  public static double sum(double a, double b) {
    return a + b;
  }

  public static boolean isFinite(double d) {
    return !Double.isInfinite(d) && !Double.isNaN(d);
  }
}
