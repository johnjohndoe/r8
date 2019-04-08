// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;

public class Regress129901036TestRunner extends TestBase {

  @Test
  public void test() throws Exception {
    testForD8()
        .addProgramClasses(Regress129901036Test.class)
        .setMode(CompilationMode.RELEASE)
        .setMinApi(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.L_MR1))
        .run(Regress129901036Test.class)
        .assertSuccessWithOutput("aok");
  }
}
