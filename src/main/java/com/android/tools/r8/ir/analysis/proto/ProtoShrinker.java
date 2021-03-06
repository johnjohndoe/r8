// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ProtoShrinker {

  public final GeneratedExtensionRegistryShrinker generatedExtensionRegistryShrinker;
  public final ProtoReferences references;

  public ProtoShrinker(AppView<AppInfoWithLiveness> appView) {
    ProtoReferences references = new ProtoReferences(appView.dexItemFactory());
    this.generatedExtensionRegistryShrinker =
        appView.options().enableGeneratedExtensionRegistryShrinking
            ? new GeneratedExtensionRegistryShrinker(appView, references)
            : null;
    this.references = references;
  }
}
