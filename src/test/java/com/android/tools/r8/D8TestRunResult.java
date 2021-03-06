// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;

public class D8TestRunResult extends TestRunResult<D8TestRunResult> {

  public D8TestRunResult(AndroidApp app, ProcessResult result) {
    super(app, result);
  }

  @Override
  protected D8TestRunResult self() {
    return this;
  }
}
