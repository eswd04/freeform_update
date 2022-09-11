## 现已构建出能够在Android 8 以及 Android 10 上稳定运行的版本

(现在已上传能够在Android10上的稳定运行的版本：Ver13+)

现已发布支持Android 8+ 的版本。（ AFreeform 1.0 ）android 8+可能还存在一定的问题，不建议使用。

android 10以下的版本用Freeform 1.0 必须使用lsposed（或其他xposed框架）进行激活，并重启系统，否则无法正常使用！





**Ver15版本及以前的版本基于米窗官方版本二改**，如需了解原版，请访问：

官方版本链接：>> [米窗官版GitHub](https://github.com/sunshine0523/Mi-FreeForm) <<





### 源码发布说明：因为一部分原因，代码是重新修改了。最近也在忙着其他的事情，不太方便更新，现在只是上传了是包含这个项目的核心代码。拖了那么久，真是很抱歉。不过后面会把项目的代码逐步补全。而且官方版本近期也会恢复更新了。因此你们后面应该可以迁移回官方版本了。



<br>

**安装步骤请往下划**

**安装步骤请往下划**

**安装步骤请往底部划**

建议先没看过说明的先看翻阅说明

<br>


若要稳定运行，需要配合Xposed框架使用.（具体看安装过程安装步骤）**（为什么需要激活Xposed？请滚动到下方查看 #Q&A ）**

**用不了Xposed框架的用户建议不要安装使用该项目内的任何版本**,因为官方有更为好用的版本！！！

> ~~支持Android 8的版本暂未上传，仅上传了支持Android 10 的版本。支持Android 10 的版本近期上传，如需加速上传，请进telegrem群组内开启加速包来加速上传~~

Now Avaliable a version can run stably on Android 8 ,But not upload.
> The version that supports Android 8 has not been uploaded yet.

**Users who can't use the Xposed Framework are adviced not to use this version**

ver13+ support Android 10 stably！



# 捐赠 (Donate ：Only support wechat and alipay)

为了能让我做出更好用的产品让你们使用，你可以向我捐赠。向我捐赠的就是我的金主爸爸！捐赠多少都是对我的一点鼓励

~~（支持Android 8+版本的暂未放出。如果你需要，可以考虑找我进行py交易。）~~




If you are would like to support my work,You can donate to me that I can make a better app for you。

(If you want a version to support android 8+ for test,you can find me in telegrem group ,and find user： @eswd)


<img src="https://github.com/eswd04/freeform_update/blob/main/eswd_alipay.jpg?raw=true" alt="支付宝" style="max-width: 30%; zoom: 33%;" width="200px"/><img src="https://github.com/eswd04/freeform_update/blob/main/eswd_mm.png?raw=true" alt="微信" style="max-width: 30%; zoom: 33%;" width="200px"/>

<a href="https://qr.alipay.com/fkx16389aa8c5ayxrqbetbd">手机跳转支付宝</a>


# 安装步骤

安装好应用的后续步骤：

1. 装Sui 或Shizuku
2. 装Lsposed 或其他Xposed框架
3. 进Lsposed激活米窗（勾选 系统框架[Framework] 和 系统界面[SystemUI]）
4. 激活后重启设备

#Installation Guide

After install apk：
1. Install Sui or Shizuku
2. Install Lsposed or other Xposed Framework
3. Goto Lsposed and active Mi Freeform
4. reboot device


# 说明（Note）

软件需要使用xposed框架来保证应用的稳定运行,如果你直接安装，你会得到一个和官方版本有着几乎同样体验的的应用.

因此，我的建议是：如果你没有使用Xposed框架(如Lsposed这些框架)，建议你还是去使用官方版本，因为官方版本来说更为好用。

Ver15版本及以前的版本基于官方2.0.5 的开放源代码进行构建，除了修复了所有应用不显示的或者启动不为目标应用的bug。还添加了一部分的特性功能。

如果你有什么问题可以提issue,但我也不一定会去看。想要内容更新或者应用的功能后续维护请找该应用的原开发者。本人并不打算继续更新下去,(没有动力,你懂的,所以我能做的就是能用就行了，不然我也会继续完善下去)，该版本仅为个人在兴趣之余，为提供在 Android 12上（Android 11也行）的稳定使用而进行构建。 

官方版本链接：[GitHub](https://github.com/sunshine0523/Mi-FreeForm)

修改版下载：<a href="https://github.com/eswd04/freeform_update/releases"> 跳转下载（download）</a>
国内用户请访问：<a href="https://eswd.lanzouj.com/b0rijuti">国内地址下载</a>

if you want to run stably , you need to use Xposed Framework.
or you get the same experience as the official version.

So my suggestion is , if you don't have Xposed ( or Lsposed etc ),you best to use officail package.
This version is build for Xposed users to use. 


# 截图/Screenshot
<img src="https://github.com/eswd04/freeform_update/blob/main/Screenshot_20220619_151608.png?raw=true" alt="android 8.1"  />



# Q&A

**为什么需要Xposed框架？不用可以吗？**

> 注意，安装Xposed并不意味着你就不需要使用Sui 或者Shizuku了

应用可以不用Xposed激活，使用Xposed激活米窗只是为了保证能够在一部分的系统版本上能够正常运行小窗。比如在Android 10 或者Android 12L上，一部分应用会在界面切换时，应用会切换回全屏运行，而不是以小窗的方式运行。这种情况就需要激活Xposed辅助。在Xposed中激活米窗并重启后，应该就不会出现应用以小窗运行时依旧会跳转回全屏运行。

因此，Xposed作为辅助，可以用也可以不用。用了Xposed可以在某些系统上更好运行。当然没有Xposed不建议使用只是因为你们使用旧版本米窗，如1.0.6版本或许会更好一点。

**稳定运行是什么意思?**

稳定运行意味着你打开QQ或者WhatApp这类应用时，应用能稳定的在小窗中打开


**这个项目的应用有什么特殊的问题（bug）存在吗**

1. 旋转屏幕可能有点问题（自行体会）
2. 其他bug应该和官方发行的版本bug一致

以上bug看心情修，或者等官方版本吧



**这个项目有什么特别的吗？**

1. 现已支持在Android 9 - Android 12 上稳定运行
2. 支持应用在退出后自动关闭小窗
3. 其他自行体会

**能把应用完善并做得更好吗？**

充钱吧！

**支持Android 8 （9）的版本呢？**

想使用？充钱吧骚年！（建议等官方版本，等等党最后的胜利！）
该版本暂未上传，反正官方也在内测，不如直接找官方开发者进入内测？

至于支持Android 9 的版本，没有设备不好测试，应该也可以稳定运行。

如果你真的非常想用，尝试找我py...或许成功py的话会发个测试版本给你。

**有没有打算支持到Android 5.0 - Android 7.1 ？**

实现应该是没问题，但是......暂时没这打算。

**开放源代码？**

暂时没空搞，等我有空再说

# 下载地址（Download）

用于更新米窗修改版本的仓库：请转到Release页面下载
Github Release:<a href="https://github.com/eswd04/freeform_update/releases"> 跳转下载（download）</a>

国内用户请访问（蓝奏云）：<a href="https://eswd.lanzouj.com/b0rijuti">国内下载</a>

# Other

QQ ： 1712865977 （别骚扰我哦 ^_^ ）
**
