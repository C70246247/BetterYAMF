package com.buildsession.betterYAMF.xposed;

import com.buildsession.betterYAMF.common.model.AppInfo;

interface IAppListCallback {
    void onAppListReceived(in List<AppInfo> appList);
    void onAppListFinished();
}
