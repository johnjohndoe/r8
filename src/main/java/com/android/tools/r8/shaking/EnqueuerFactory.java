// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.Enqueuer.Mode;

public class EnqueuerFactory {

  public static Enqueuer createForInitialTreeShaking(
      AppView<? extends AppInfoWithSubtyping> appView) {
    return createForInitialTreeShaking(appView, null);
  }

  public static Enqueuer createForInitialTreeShaking(
      AppView<? extends AppInfoWithSubtyping> appView,
      ProguardConfiguration.Builder compatibility) {
    return new Enqueuer(appView, null, compatibility, Mode.INITIAL_TREE_SHAKING);
  }

  public static Enqueuer createForPostTreeShaking(
      AppView<? extends AppInfoWithSubtyping> appView, GraphConsumer keptGraphConsumer) {
    return new Enqueuer(appView, keptGraphConsumer, null, Mode.POST_TREE_SHAKING);
  }

  public static Enqueuer createForMainDexTracing(AppView<? extends AppInfoWithSubtyping> appView) {
    return createForMainDexTracing(appView, null);
  }

  public static Enqueuer createForMainDexTracing(
      AppView<? extends AppInfoWithSubtyping> appView, GraphConsumer keptGraphConsumer) {
    return new Enqueuer(appView, keptGraphConsumer, null, Mode.MAIN_DEX_TRACING);
  }

  public static Enqueuer createForWhyAreYouKeeping(
      AppView<? extends AppInfoWithSubtyping> appView, GraphConsumer keptGraphConsumer) {
    return new Enqueuer(appView, keptGraphConsumer, null, Mode.WHY_ARE_YOU_KEEPING);
  }
}
