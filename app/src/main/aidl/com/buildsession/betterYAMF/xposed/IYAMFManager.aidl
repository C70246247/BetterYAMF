// IYAMFManager.aidl
package com.buildsession.betterYAMF.xposed;

import com.buildsession.betterYAMF.xposed.IOpenCountListener;
import com.buildsession.betterYAMF.common.model.AppInfo;
import com.buildsession.betterYAMF.xposed.IAppListCallback;
import com.buildsession.betterYAMF.xposed.IAppIconCallback;
// Declare any non-default types here with import statements

interface IYAMFManager {
    String getVersionName();

    int getVersionCode();

    int getUid();

    void createWindow();

    long getBuildTime();

    String getConfigJson();

    void updateConfig(String newConfig);

    void registerOpenCountListener(IOpenCountListener iOpenCountListener);

    void unregisterOpenCountListener(IOpenCountListener iOpenCountListener);

    void currentToWindow();

    void resetAllWindow();

    List<AppInfo> getAppList();

    void createWindowUserspace(in AppInfo appInfo);

    void getAppListAsync(IAppListCallback callback);

    void getAppIcon(IAppIconCallback callback, in AppInfo appInfo);

    void collapseStatusBarPanel();
}
