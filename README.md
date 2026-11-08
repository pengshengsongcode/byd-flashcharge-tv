# 比亚迪闪充 Android TV

## 下载安装

[下载 Android TV 调试 APK](https://github.com/pengshengsongcode/byd-flashcharge-tv/releases/download/v2.0.0-debug.1/byd-flashcharge-tv-v2.0.0-debug.apk) · [查看发布说明](https://github.com/pengshengsongcode/byd-flashcharge-tv/releases/tag/v2.0.0-debug.1)

支持 Android 6.0（API 23）及以上版本，已在索尼 Android 12 电视上验证。当前为调试签名的测试版。

电视版使用独立原生遥控器界面，支持全国省、市、区县和北京站点浏览；右侧为独立高德地图。方向键移动焦点，确定键进入或刷新站点详情，返回键回到上一级。地图控件支持缩放、复位和遥控器方向键平移。站点列表按 20 条分批加载，可选择“加载更多站点”继续浏览；快速切换城市时地图会合并更新。最近新增仅按官方近期新站清单筛选，不代表该地区所有带新增标签的站点。

应用启动先读取随 APK 附带的站点快照和区县索引，随后通过现有 API 更新，界面会标注数据来源和源数据时间。地图与实时数据需要网络。APK 不需要访问 GitHub Pages 来下载界面；地图组件使用原网站域名加载本地 HTML，以沿用现有高德和 API 的来源限制。高德安全密钥留在服务端，不打包进 APK。站点名称和详情按焦点逐个查询，遵守接口限流，未查询前显示占位信息。

## 构建

需要 JDK 11、Android SDK 31 及 Build Tools 30.0.2：

```sh
./gradlew assembleDebug
```

生成文件为 `app/build/outputs/apk/debug/app-debug.apk`。离线数据位于 `app/src/main/assets/data/`，无需依赖其他源码仓库。调试包由本机 Android 调试密钥签名，仅用于开发和测试；正式发布需单独配置安全保管的发布签名。

## 安装与调试

```sh
adb connect 电视IP:5555
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.pengshengsongcode.bydflashchargetv/.MainActivity
```

电视必须开启开发者选项及 ADB 调试。请仅在可信的家庭网络中临时开启 ADB，调试完成后按设备需要关闭。

## 数据来源与依赖

- 闪充站点数据来自比亚迪公开接口，通过现有 [站点项目](https://github.com/pengshengsongcode/byd-flashcharge-tracker/tree/github-pages) 的 Netlify 后端读取；高德地图通过同一后端配置与代理加载，因此应用运行时仍依赖该服务。
- 区县索引根据原项目的预计算结果构建，相关许可见 [district-license.txt](licenses/district-license.txt)。
- 这是非官方项目，实际站点信息以比亚迪官方 App 为准。
