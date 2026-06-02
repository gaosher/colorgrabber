# ColorGrabber 取色测浓度

一个面向科研的安卓**取色 App**,用于光催化降解四环素实验中的**比色法浓度测定**——替代商业 App「Color Grab」,做成贴合本课题组拍摄条件与数据需求的专用工具。

## 背景

半导体材料光催化降解四环素;降解过程中显色剂与四环素发生显色反应,**颜色深浅与浓度呈线性关系**,因此可由颜色反推浓度。
拍摄条件固定:**试管置于白色背景前 + 补光**。

App 把试管溶液的颜色量化为 RGB / HSV / Lab / **三通道吸光度**,为后续「颜色→浓度」标定提供稳定、可复现的原始数据。

## 功能(第一阶段:取色)

操作流程贴合直觉:**取景 → 拍摄 → 调参 → 选区取色**。

- **两种取色方式**
  - 取景页:实时预览,画面中心取色框即时读数
  - 测量页:对拍下的静态照片操作——**双指缩放、单指平移图片**,**拖动/拉角调整取色框**,在冻结画面上从容选区
- **取色框内像素求平均**,输出代表色,降低噪点/反光影响;过曝(像素≥250 占比>20%)会提示 ⚠
- **四类色彩输出**:RGB、HSV、Lab、**逐通道吸光度 A_R/A_G/A_B**(基于朗伯比尔 `A = −log₁₀(I/I₀)`)
- **手动白平衡**:对准白背景一键「点白校准」+ 色温/RGB 增益微调;取景时走相机硬件白平衡,测量时走软件归一化,跨设备更一致
- **设参比 I₀**:采空白/白背景作吸光度基线,之后样品自动给出三通道吸光度
- **大号 RGB 读数 + 实时色块**,一眼可读
- **数据持久化**:本地历史(Room)、原图存入专属相册「ColorGrabber/」、记录关联图片与取色框、样品名/备注/降解时间点字段
- **导出**:CSV(UTF-8 BOM,中文不乱码,Excel 可直接打开)

> 「点白校准」管**颜色准不准**(消除补光偏色);「设参比」管**浓度零点 I₀**(吸光度基线)。两者都常对白背景采,但目的不同。

## 技术栈

- Kotlin · AGP 9.2.1 · Gradle 9.4.1 · minSdk 24 / targetSdk 36
- CameraX 1.4.1(预览 + ImageAnalysis,RGBA_8888)+ Camera2 互操作(手动白平衡 / 锁定曝光对焦)
- Room 2.7.1 + KSP · Coroutines · ViewBinding
- Material Components(沉稳墨绿主题)

## 构建与运行

> ⚠ 本机/离线环境注意:AGP 9 强制要 JDK 21 toolchain,而 Gradle 9.4 不接受系统自带的 Ubuntu OpenJDK 21。
> 已在 `gradle.properties` 指向 `~/.gradle/jdks/jdk-21.0.7+6`(Temurin)。构建前先 `export JAVA_HOME=/home/gser/.gradle/jdks/jdk-21.0.7+6`。

```bash
# Debug 包
./gradlew assembleDebug --offline
# 单元测试(色彩换算 / 白平衡引擎,23 个用例)
./gradlew testDebugUnitTest --offline
# 安装到已连接设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 发布(Release)

Release 版已配置签名,签名信息从项目根目录 `keystore.properties` 读取(该文件含密码,**不要提交到版本库**)。

```bash
export JAVA_HOME=/home/gser/.gradle/jdks/jdk-21.0.7+6
./gradlew assembleRelease --offline
# 产物:app/build/outputs/apk/release/app-release.apk
```

- 签名密钥库:`colorgrabber-release.jks`(alias `colorgrabber`,有效期 100 年)
- **请务必备份 `colorgrabber-release.jks` 与 `keystore.properties`**:一旦丢失,将无法用同一签名发布后续更新。
- Release 与 Debug 签名不同,设备上需先卸载 Debug 版再安装 Release 版。

## 项目结构

```
app/src/main/java/com/example/colorgrabber/
├─ color/        ColorModels, ColorAnalyzer        # sRGB→HSV/Lab、吸光度
├─ wb/           WhiteBalanceEngine, WbState        # 白平衡增益/归一化
├─ camera/       CameraController, RoiSampler       # 取景、取色框采样
├─ data/         Measurement, Dao, Database,        # Room 持久化
│                Repository, CsvExporter, AlbumStore
├─ ui/           CaptureFragment(取景)、PickFragment(测量)、
│                HistoryFragment、ZoomableRoiView(缩放/平移/取色框)、
│                RecordDialog、各扩展
└─ MainActivity                                     # Toolbar + 溢出菜单容器
```

## 路线图

- ✅ 第一阶段:取色模式(本版本)
- ⏳ 第二阶段:独立的「颜色→浓度」全流程标定 Activity——用已知浓度建标准曲线,对未知样品直接读浓度(复用 `ColorAnalyzer` / `WhiteBalanceEngine` / `Measurement`)
