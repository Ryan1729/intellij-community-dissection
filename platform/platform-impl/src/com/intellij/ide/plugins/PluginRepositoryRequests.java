// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PluginRepositoryRequests {
  @NotNull
  public static Url createSearchUrl(@NotNull String query, int count) {
    ApplicationInfoEx instance = ApplicationInfoImpl.getShadowInstance();
    return Urls.newFromEncoded(instance.getPluginManagerUrl() + "/api/search?" + query +
                               "&build=" + URLUtil.encodeURIComponent(instance.getApiVersion()) +
                               "&max=" + count);
  }

  @NotNull
  public static List<PluginId> requestToPluginRepository() {
    return new ArrayList<>();
  }

  public static boolean loadPlugins(@NotNull List<? super IdeaPluginDescriptor> descriptors,
                                    @NotNull Map<PluginId, IdeaPluginDescriptor> allDescriptors,
                                    @NotNull String query) throws IOException {
    Url baseUrl = createSearchUrl(query, PluginManagerConfigurable.ITEMS_PER_GROUP);
    Url offsetUrl = baseUrl;
    Map<String, String> offsetParameters = new HashMap<>();
    int offset = 0;

    while (true) {
      List<PluginId> pluginIds = requestToPluginRepository();
      if (pluginIds.isEmpty()) {
        return false;
      }

      for (PluginId pluginId : pluginIds) {
        IdeaPluginDescriptor descriptor = allDescriptors.get(pluginId);
        if (descriptor != null) {
          descriptors.add(descriptor);
          if (descriptors.size() == PluginManagerConfigurable.ITEMS_PER_GROUP) {
            return true;
          }
        }
      }

      offset += pluginIds.size();
      offsetParameters.put("offset", Integer.toString(offset));
      offsetUrl = baseUrl.addParameters(offsetParameters);
    }
  }

  @Nullable
  public static Object getPluginPricesJsonObject() throws IOException {
    ApplicationInfoEx instance = ApplicationInfoImpl.getShadowInstance();
    Url url = Urls.newFromEncoded(instance.getPluginManagerUrl() + "/geo/files/prices");

    return HttpRequests.request(url).throwStatusCodeException(false).productNameAsUserAgent().connect(request -> null);
  }
}
