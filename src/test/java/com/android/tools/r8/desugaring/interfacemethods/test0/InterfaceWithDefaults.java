// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods.test0;

public interface InterfaceWithDefaults {
  default void foo() {
    System.out.println("InterfaceWithDefaults::foo()");
  }

  default void bar() {
    System.out.println("InterfaceWithDefaults::bar()");
    this.foo();
  }

  void test();
}
