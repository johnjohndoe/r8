// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

public class DefaultLambdaWithInvokeInterfaceTest {

  public interface I {
    String run();
  }

  public interface J {
    default String stateless() {
      return "hest";
    }

    default I stateful() {
      return () -> {
        String stateless = stateless();
        return "stateful(" + stateless + ")";
      };
    }
  }

  public static void main(String[] args) {
    J j = new J() {};
    I stateful = j.stateful();
    String run = stateful.run();
    System.out.println(run);
  }
}
