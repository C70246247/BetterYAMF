[![Size](https://img.shields.io/github/languages/code-size/JuanArton/reYAMF)](https://github.com/JuanArton/reYAMF)

# BetterYAMF

A fork of reYAMF (forked from [reYAMF](https://github.com/JuanArton/reYAMF), actually), with my changes.

## Preview
![Preview](Preview/preview.gif)

## Requirements
- Android 13+ (>= api 33)
- LSPosed

## Features
- Quick launch use gesture.
- More likely HyperOS's behavior, easyly to use.
- All reMAMF features are supported.

## My Changes
- **Crash Fix**: Resolved `IllegalArgumentException` (View not attached to window manager) caused by accidental triggers of `ITaskStackListener`.
- Use gesture to open float windows like HyperOS.
- Operate float windows like HyperOS.
- **Performance Optimization**: The listener now uses a dynamic registration mechanism, active only when there are open windows to minimize system impact.
- **Stability Enhancement**: Added `isAttachedToWindow` safety checks during the destruction process to ensure reliable view removal.

## Launch by gesture like hyperOS
- Select Launcher3 or pixel launcher in LSP module working area.

## How to install
- Grab the latest APK from the [releases section](https://github.com/JuanArton/YAMFsquared/releases)
- Install it
- Enable module in LSPosed
- Go to Accessibility Settings and enable BetterYAMF accessibility service
- Reboot

## "API" 
- Broadcast `com.buildsession.betterYAMF.action.CURRENT_TO_WINDOW` to float the currently visible app
- Maybe more to come

## Issues
- The system will crash if the module is different from the injected version, its an xposed thing
- Some apps can't seem to launch in small windows
- Some apps scale abnormally at certain sizes
- Some app restart while being resized. (Will fix by adding lock DPI option)

## TODO
- Fix landscape mode performance.
- Fix collapse status app icon show issue.

## Contributors List
<!-- readme: contributors -start -->
<table>
	<tbody>
		<tr>
            <td align="center">
                <a href="https://github.com/C70246247">
                    <img src="https://avatars.githubusercontent.com/u/59191002?v=4" width="100;" alt="C70246247"/>
                    <br />
                    <sub><b>BuildSession</b></sub>
                </a>
            </td>
            <td align="center">
                <a href="https://github.com/JuanArton">
                    <img src="https://avatars.githubusercontent.com/u/69680526?v=4" width="100;" alt="JuanArton"/>
                    <br />
                    <sub><b>Juan Arton</b></sub>
                </a>
            </td>
            <td align="center">
                <a href="https://github.com/duzhaokun123">
                    <img src="https://avatars.githubusercontent.com/u/39830683?v=4" width="100;" alt="duzhaokun123"/>
                    <br />
                    <sub><b>o0kam1</b></sub>
                </a>
            </td>
            <td align="center">
                <a href="https://github.com/No-22-Github">
                    <img src="https://avatars.githubusercontent.com/u/132265925?v=4" width="100;" alt="No-22-Github"/>
                    <br />
                    <sub><b>No.22</b></sub>
                </a>
            </td>
		</tr>
	<tbody>
</table>
<!-- readme: contributors -end -->

## Special Thanks
- MASSIVE thanks to [duzhaokun123](https://github.com/duzhaokun123) and [kaii-lb](https://github.com/kaii-lb/YAMFsquared)

- [reYAMF](https://github.com/JuanArton/reYAMF)
- [AOSP](https://source.android.com/)
- [EzXHelper](https://github.com/KyuubiRan/EzXHelper)
- [FlexboxLayout](https://github.com/google/flexbox-layout)
- [Hide-My-Applist](https://github.com/Dr-TSNG/Hide-My-Applist)
- [LSPosed](https://github.com/LSPosed/LSPosed)
- [Material](https://material.io/)
- [Mi-FreeForm](https://github.com/sunshine0523/Mi-FreeForm)
- [QAuxiliary](https://github.com/cinit/QAuxiliary)
- [ViewBindingUtil](https://github.com/matsudamper/ViewBindingUtil)
- [gson](https://github.com/google/gson)
- [xposed](https://forum.xda-developers.com/xposed)
