# ColorGrabber 取色模式 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ColorGrabber 实现「只取色」模式：实时/拍照取色，手动白平衡，输出 RGB/HSV/Lab/三通道吸光度，本地历史记录 + 专属相册 + CSV 导出。

**Architecture:** 路线 A —— CameraX 做预览/拍照，Camera2 互操作做手动白平衡与锁定曝光对焦；颜色与白平衡算法为纯 Kotlin 库（可单元测试）；Room 持久化 + MediaStore 专属相册 + CSV 导出。单 Activity + Fragment 三屏（取色 / 选区 / 历史）。

**Tech Stack:** Kotlin, CameraX (core/camera2/lifecycle/view), Camera2 interop, Room (+KSP), Kotlin Coroutines, AndroidX Lifecycle/Activity/Fragment, JUnit4。

---

## 文件结构总览

纯逻辑（可单测，不依赖 Android framework）：
- `app/src/main/java/com/example/colorgrabber/color/ColorAnalyzer.kt` — RGB→HSV/Lab、吸光度
- `app/src/main/java/com/example/colorgrabber/color/ColorModels.kt` — 颜色数据类
- `app/src/main/java/com/example/colorgrabber/wb/WhiteBalanceEngine.kt` — 点白算增益、色温↔增益、归一化
- `app/src/main/java/com/example/colorgrabber/camera/RoiSampler.kt` — 从像素数组取 ROI 平均 RGB

数据层：
- `app/src/main/java/com/example/colorgrabber/data/Measurement.kt` — Room 实体
- `app/src/main/java/com/example/colorgrabber/data/MeasurementDao.kt`
- `app/src/main/java/com/example/colorgrabber/data/AppDatabase.kt`
- `app/src/main/java/com/example/colorgrabber/data/MeasurementRepository.kt`
- `app/src/main/java/com/example/colorgrabber/data/CsvExporter.kt`
- `app/src/main/java/com/example/colorgrabber/data/AlbumStore.kt` — MediaStore 专属相册

相机与 UI：
- `app/src/main/java/com/example/colorgrabber/camera/CameraController.kt`
- `app/src/main/java/com/example/colorgrabber/MainActivity.kt`
- `app/src/main/java/com/example/colorgrabber/ui/CaptureFragment.kt`
- `app/src/main/java/com/example/colorgrabber/ui/PickFragment.kt`
- `app/src/main/java/com/example/colorgrabber/ui/HistoryFragment.kt`
- `app/src/main/java/com/example/colorgrabber/ui/MeasurementAdapter.kt`
- `app/src/main/java/com/example/colorgrabber/ui/RoiOverlayView.kt`

测试：
- `app/src/test/java/com/example/colorgrabber/color/ColorAnalyzerTest.kt`
- `app/src/test/java/com/example/colorgrabber/wb/WhiteBalanceEngineTest.kt`
- `app/src/test/java/com/example/colorgrabber/camera/RoiSamplerTest.kt`
- `app/src/test/java/com/example/colorgrabber/data/CsvExporterTest.kt`
- `app/src/androidTest/java/com/example/colorgrabber/data/MeasurementDaoTest.kt`

---

## Milestone 0：工程基建（Kotlin + 依赖 + 权限）

### Task 0.1：版本目录加入 Kotlin/CameraX/Room/协程

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: 在 `[versions]` 末尾追加**

```toml
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
camerax = "1.4.1"
room = "2.6.1"
coroutines = "1.9.0"
lifecycle = "2.8.7"
activity = "1.9.3"
fragment = "1.8.5"
recyclerview = "1.3.2"
```

- [ ] **Step 2: 在 `[libraries]` 末尾追加**

```toml
androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-activity-ktx = { group = "androidx.activity", name = "activity-ktx", version.ref = "activity" }
androidx-fragment-ktx = { group = "androidx.fragment", name = "fragment-ktx", version.ref = "fragment" }
androidx-recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
```

- [ ] **Step 3: 在 `[plugins]` 追加**

```toml
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: 版本目录加入 Kotlin/CameraX/Room/协程"
```

### Task 0.2：模块 build.gradle 启用 Kotlin 与依赖

**Files:**
- Modify: `build.gradle.kts`（根）
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 根 `build.gradle.kts` 改为**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 2: `app/build.gradle.kts` 的 `plugins {}` 改为**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}
```

- [ ] **Step 3: 在 `android {}` 内 `compileOptions` 后追加**

```kotlin
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
```

- [ ] **Step 4: 替换 `dependencies {}` 为**

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [ ] **Step 5: 同步并编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（暂无 Kotlin 源码也应通过）

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts app/build.gradle.kts
git commit -m "build: 启用 Kotlin/KSP/ViewBinding 与项目依赖"
```

### Task 0.3：声明相机权限

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 在 `<manifest>` 内、`<application>` 前加入**

```xml
    <uses-feature android:name="android.hardware.camera.any" android:required="true" />
    <uses-permission android:name="android.permission.CAMERA" />
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "build: 声明相机权限"
```

---

## Milestone 1：ColorAnalyzer（纯逻辑，TDD）

### Task 1.1：颜色数据模型

**Files:**
- Create: `app/src/main/java/com/example/colorgrabber/color/ColorModels.kt`

- [ ] **Step 1: 写数据类**

```kotlin
package com.example.colorgrabber.color

/** 0..255 整数 RGB */
data class Rgb(val r: Int, val g: Int, val b: Int)

/** h: 0..360, s/v: 0..1 */
data class Hsv(val h: Double, val s: Double, val v: Double)

/** CIE L*a*b* (D65) */
data class Lab(val l: Double, val a: Double, val b: Double)

/** 三通道吸光度 */
data class Absorbance(val aR: Double, val aG: Double, val aB: Double)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/color/ColorModels.kt
git commit -m "feat(color): 颜色数据模型"
```

### Task 1.2：RGB→HSV（TDD）

**Files:**
- Create: `app/src/test/java/com/example/colorgrabber/color/ColorAnalyzerTest.kt`
- Create: `app/src/main/java/com/example/colorgrabber/color/ColorAnalyzer.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.colorgrabber.color

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorAnalyzerTest {
    private val eps = 1e-2

    @Test fun hsv_pureRed() {
        val hsv = ColorAnalyzer.toHsv(Rgb(255, 0, 0))
        assertEquals(0.0, hsv.h, eps)
        assertEquals(1.0, hsv.s, eps)
        assertEquals(1.0, hsv.v, eps)
    }

    @Test fun hsv_white() {
        val hsv = ColorAnalyzer.toHsv(Rgb(255, 255, 255))
        assertEquals(0.0, hsv.s, eps)
        assertEquals(1.0, hsv.v, eps)
    }

    @Test fun hsv_green() {
        val hsv = ColorAnalyzer.toHsv(Rgb(0, 255, 0))
        assertEquals(120.0, hsv.h, eps)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.color.ColorAnalyzerTest"`
Expected: 编译失败（`ColorAnalyzer` 未定义）

- [ ] **Step 3: 写最小实现**

```kotlin
package com.example.colorgrabber.color

import kotlin.math.max
import kotlin.math.min

object ColorAnalyzer {

    fun toHsv(rgb: Rgb): Hsv {
        val r = rgb.r / 255.0
        val g = rgb.g / 255.0
        val b = rgb.b / 255.0
        val cmax = max(r, max(g, b))
        val cmin = min(r, min(g, b))
        val delta = cmax - cmin
        val h = when {
            delta == 0.0 -> 0.0
            cmax == r -> 60.0 * (((g - b) / delta) % 6.0)
            cmax == g -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }.let { if (it < 0) it + 360.0 else it }
        val s = if (cmax == 0.0) 0.0 else delta / cmax
        return Hsv(h, s, cmax)
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.color.ColorAnalyzerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/color/ColorAnalyzer.kt app/src/test/java/com/example/colorgrabber/color/ColorAnalyzerTest.kt
git commit -m "feat(color): RGB→HSV"
```

### Task 1.3：RGB→Lab（TDD）

**Files:**
- Modify: `app/src/test/java/com/example/colorgrabber/color/ColorAnalyzerTest.kt`
- Modify: `app/src/main/java/com/example/colorgrabber/color/ColorAnalyzer.kt`

- [ ] **Step 1: 追加失败测试（类内）**

```kotlin
    @Test fun lab_white() {
        val lab = ColorAnalyzer.toLab(Rgb(255, 255, 255))
        assertEquals(100.0, lab.l, 1e-1)
        assertEquals(0.0, lab.a, 1e-1)
        assertEquals(0.0, lab.b, 1e-1)
    }

    @Test fun lab_black() {
        val lab = ColorAnalyzer.toLab(Rgb(0, 0, 0))
        assertEquals(0.0, lab.l, 1e-1)
    }

    @Test fun lab_red() {
        val lab = ColorAnalyzer.toLab(Rgb(255, 0, 0))
        assertEquals(53.24, lab.l, 0.5)
        assertEquals(80.09, lab.a, 0.5)
        assertEquals(67.20, lab.b, 0.5)
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.color.ColorAnalyzerTest"`
Expected: 编译失败（`toLab` 未定义）

- [ ] **Step 3: 在 `ColorAnalyzer` 内追加实现**

```kotlin
    private fun invGamma(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)

    private fun f(t: Double): Double =
        if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116.0

    fun toLab(rgb: Rgb): Lab {
        val r = invGamma(rgb.r / 255.0)
        val g = invGamma(rgb.g / 255.0)
        val b = invGamma(rgb.b / 255.0)
        // sRGB D65 -> XYZ
        val x = 0.4124 * r + 0.3576 * g + 0.1805 * b
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = 0.0193 * r + 0.1192 * g + 0.9505 * b
        // D65 reference white
        val xn = 0.95047; val yn = 1.0; val zn = 1.08883
        val fx = f(x / xn); val fy = f(y / yn); val fz = f(z / zn)
        val l = 116.0 * fy - 16.0
        val aa = 500.0 * (fx - fy)
        val bb = 200.0 * (fy - fz)
        return Lab(l, aa, bb)
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.color.ColorAnalyzerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/color/ColorAnalyzer.kt app/src/test/java/com/example/colorgrabber/color/ColorAnalyzerTest.kt
git commit -m "feat(color): RGB→CIE Lab (D65)"
```

### Task 1.4：三通道吸光度（TDD）

**Files:**
- Modify: `app/src/test/java/com/example/colorgrabber/color/ColorAnalyzerTest.kt`
- Modify: `app/src/main/java/com/example/colorgrabber/color/ColorAnalyzer.kt`

- [ ] **Step 1: 追加失败测试（类内）**

```kotlin
    @Test fun absorbance_halfIntensity() {
        // I = I0/2 → A = -log10(0.5) ≈ 0.301
        val a = ColorAnalyzer.toAbsorbance(Rgb(128, 128, 128), Rgb(255, 255, 255))
        assertEquals(0.301, a.aR, 1e-2)
        assertEquals(0.301, a.aG, 1e-2)
        assertEquals(0.301, a.aB, 1e-2)
    }

    @Test fun absorbance_equalIsZero() {
        val a = ColorAnalyzer.toAbsorbance(Rgb(200, 200, 200), Rgb(200, 200, 200))
        assertEquals(0.0, a.aR, 1e-3)
    }

    @Test fun absorbance_clampsZeroSample() {
        // 样品为 0 时按 0.5 处理，避免 log(0)
        val a = ColorAnalyzer.toAbsorbance(Rgb(0, 0, 0), Rgb(255, 255, 255))
        assertEquals(-Math.log10(0.5 / 255.0), a.aR, 1e-3)
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.color.ColorAnalyzerTest"`
Expected: 编译失败（`toAbsorbance` 未定义）

- [ ] **Step 3: 在 `ColorAnalyzer` 内追加实现**

```kotlin
    /**
     * 三通道吸光度 A = -log10(I/I0)。
     * 样品/参比通道值为 0 时按 0.5 处理，避免 log(0)/除零。
     */
    fun toAbsorbance(sample: Rgb, reference: Rgb): Absorbance {
        fun ch(i: Int, i0: Int): Double {
            val s = if (i <= 0) 0.5 else i.toDouble()
            val ref = if (i0 <= 0) 0.5 else i0.toDouble()
            return -Math.log10(s / ref)
        }
        return Absorbance(
            aR = ch(sample.r, reference.r),
            aG = ch(sample.g, reference.g),
            aB = ch(sample.b, reference.b)
        )
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.color.ColorAnalyzerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/color/ColorAnalyzer.kt app/src/test/java/com/example/colorgrabber/color/ColorAnalyzerTest.kt
git commit -m "feat(color): 三通道吸光度 A=-log10(I/I0)"
```

---

## Milestone 2：WhiteBalanceEngine（纯逻辑，TDD）

### Task 2.1：点白算增益 + 归一化（TDD）

**Files:**
- Create: `app/src/test/java/com/example/colorgrabber/wb/WhiteBalanceEngineTest.kt`
- Create: `app/src/main/java/com/example/colorgrabber/wb/WhiteBalanceEngine.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.colorgrabber.wb

import com.example.colorgrabber.color.Rgb
import org.junit.Assert.assertEquals
import org.junit.Test

class WhiteBalanceEngineTest {
    private val eps = 1e-3

    @Test fun gains_makeWhitePatchWhite() {
        // 量到的白偏黄 (240,220,180) → 增益使其归一到 255
        val g = WhiteBalanceEngine.gainsFromWhite(Rgb(240, 220, 180))
        assertEquals(255.0 / 240.0, g.r, eps)
        assertEquals(255.0 / 220.0, g.g, eps)
        assertEquals(255.0 / 180.0, g.b, eps)
    }

    @Test fun normalize_appliesGainsAndClamps() {
        val g = Gains(2.0, 1.0, 1.5)
        val out = WhiteBalanceEngine.normalize(Rgb(100, 100, 200), g)
        assertEquals(200, out.r)   // 100*2
        assertEquals(100, out.g)   // 100*1
        assertEquals(255, out.b)   // 200*1.5=300 → 钳到 255
    }

    @Test fun gains_clampedToMax() {
        // 极暗白 (10,10,10) 不应产生超大增益
        val g = WhiteBalanceEngine.gainsFromWhite(Rgb(10, 10, 10))
        assertEquals(WhiteBalanceEngine.MAX_GAIN, g.r, eps)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.wb.WhiteBalanceEngineTest"`
Expected: 编译失败（`WhiteBalanceEngine`/`Gains` 未定义）

- [ ] **Step 3: 写实现**

```kotlin
package com.example.colorgrabber.wb

import com.example.colorgrabber.color.Rgb
import kotlin.math.roundToInt

/** 三通道增益 */
data class Gains(val r: Double, val g: Double, val b: Double)

object WhiteBalanceEngine {
    const val MAX_GAIN = 8.0
    private const val TARGET_WHITE = 255.0

    /** 由量到的白色 ROI 平均色，反推使其归一到纯白的增益（钳到 MAX_GAIN）。 */
    fun gainsFromWhite(white: Rgb): Gains {
        fun gain(c: Int): Double {
            val v = c.coerceAtLeast(1)
            return (TARGET_WHITE / v).coerceIn(0.0, MAX_GAIN)
        }
        return Gains(gain(white.r), gain(white.g), gain(white.b))
    }

    /** 对像素施加增益并钳到 0..255。 */
    fun normalize(rgb: Rgb, gains: Gains): Rgb {
        fun ap(c: Int, g: Double): Int = (c * g).roundToInt().coerceIn(0, 255)
        return Rgb(ap(rgb.r, gains.r), ap(rgb.g, gains.g), ap(rgb.b, gains.b))
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.wb.WhiteBalanceEngineTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/wb/WhiteBalanceEngine.kt app/src/test/java/com/example/colorgrabber/wb/WhiteBalanceEngineTest.kt
git commit -m "feat(wb): 点白算增益 + 白归一化"
```

### Task 2.2：色温微调与开尔文显示（TDD）

**Files:**
- Modify: `app/src/test/java/com/example/colorgrabber/wb/WhiteBalanceEngineTest.kt`
- Modify: `app/src/main/java/com/example/colorgrabber/wb/WhiteBalanceEngine.kt`

> 设计说明：色温微调是「点白增益」之上的偏移 `tempAdjust ∈ [-1,1]`（负=偏冷，正=偏暖）。
> 暖向提升 R 增益、压低 B 增益，G 不变；灵敏度常数 `TEMP_SENSITIVITY=0.5`。
> UI 显示的开尔文值由 `displayKelvin = 5500 - tempAdjust*2700` 近似映射（仅供显示，非物理精确）。

- [ ] **Step 1: 追加失败测试（类内）**

```kotlin
    @Test fun tempAdjust_zeroIsIdentity() {
        val base = Gains(1.2, 1.0, 1.5)
        val g = WhiteBalanceEngine.applyTempAdjust(base, 0.0)
        assertEquals(1.2, g.r, eps); assertEquals(1.0, g.g, eps); assertEquals(1.5, g.b, eps)
    }

    @Test fun tempAdjust_warmRaisesRedLowersBlue() {
        val base = Gains(1.0, 1.0, 1.0)
        val g = WhiteBalanceEngine.applyTempAdjust(base, 1.0)  // 最暖
        assertEquals(1.0 * (1 + 0.5), g.r, eps)
        assertEquals(1.0, g.g, eps)
        assertEquals(1.0 * (1 - 0.5), g.b, eps)
    }

    @Test fun displayKelvin_mapping() {
        assertEquals(5500, WhiteBalanceEngine.displayKelvin(0.0))
        assertEquals(2800, WhiteBalanceEngine.displayKelvin(1.0))
        assertEquals(8200, WhiteBalanceEngine.displayKelvin(-1.0))
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.wb.WhiteBalanceEngineTest"`
Expected: 编译失败（`applyTempAdjust`/`displayKelvin` 未定义）

- [ ] **Step 3: 在 `WhiteBalanceEngine` 内追加实现**

```kotlin
    const val TEMP_SENSITIVITY = 0.5

    /** 在基础增益上施加色温偏移（暖向升 R 降 B），结果钳到 MAX_GAIN。 */
    fun applyTempAdjust(base: Gains, tempAdjust: Double): Gains {
        val t = tempAdjust.coerceIn(-1.0, 1.0)
        val r = (base.r * (1 + TEMP_SENSITIVITY * t)).coerceIn(0.0, MAX_GAIN)
        val b = (base.b * (1 - TEMP_SENSITIVITY * t)).coerceIn(0.0, MAX_GAIN)
        return Gains(r, base.g, b)
    }

    /** tempAdjust → 近似开尔文显示值。 */
    fun displayKelvin(tempAdjust: Double): Int =
        (5500 - tempAdjust.coerceIn(-1.0, 1.0) * 2700).roundToInt()
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.wb.WhiteBalanceEngineTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/wb/WhiteBalanceEngine.kt app/src/test/java/com/example/colorgrabber/wb/WhiteBalanceEngineTest.kt
git commit -m "feat(wb): 色温微调与开尔文显示映射"
```

---

## Milestone 3：ROI 取样（纯逻辑，TDD）

### Task 3.1：从像素数组 ROI 求平均 RGB + 过曝比例（TDD）

**Files:**
- Create: `app/src/test/java/com/example/colorgrabber/camera/RoiSamplerTest.kt`
- Create: `app/src/main/java/com/example/colorgrabber/camera/RoiSampler.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.colorgrabber.camera

import com.example.colorgrabber.color.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoiSamplerTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun averagesRoiOnly() {
        // 2x2 图：左上红，其余黑；ROI 只覆盖左上 1x1
        val w = 2; val h = 2
        val px = intArrayOf(
            argb(200, 100, 50), argb(0, 0, 0),
            argb(0, 0, 0),      argb(0, 0, 0)
        )
        val res = RoiSampler.sample(px, w, h, RoiRect(0, 0, 1, 1))
        assertEquals(Rgb(200, 100, 50), res.mean)
    }

    @Test fun reportsOverexposure() {
        val w = 2; val h = 1
        val px = intArrayOf(argb(255, 255, 255), argb(10, 10, 10))
        val res = RoiSampler.sample(px, w, h, RoiRect(0, 0, 2, 1))
        assertTrue(res.overexposedRatio in 0.49..0.51)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.camera.RoiSamplerTest"`
Expected: 编译失败（`RoiSampler`/`RoiRect` 未定义）

- [ ] **Step 3: 写实现**

```kotlin
package com.example.colorgrabber.camera

import com.example.colorgrabber.color.Rgb
import kotlin.math.roundToInt

data class RoiRect(val x: Int, val y: Int, val w: Int, val h: Int)

data class RoiResult(val mean: Rgb, val overexposedRatio: Double)

object RoiSampler {
    private const val OVEREXPOSED = 250

    /** px 为 ARGB 行优先数组，宽 width 高 height；返回 ROI 内平均 RGB 与过曝比例。 */
    fun sample(px: IntArray, width: Int, height: Int, roi: RoiRect): RoiResult {
        val x0 = roi.x.coerceIn(0, width)
        val y0 = roi.y.coerceIn(0, height)
        val x1 = (roi.x + roi.w).coerceIn(0, width)
        val y1 = (roi.y + roi.h).coerceIn(0, height)
        var sumR = 0L; var sumG = 0L; var sumB = 0L; var n = 0L; var over = 0L
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = px[y * width + x]
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                sumR += r; sumG += g; sumB += b; n++
                if (r >= OVEREXPOSED || g >= OVEREXPOSED || b >= OVEREXPOSED) over++
            }
        }
        if (n == 0L) return RoiResult(Rgb(0, 0, 0), 0.0)
        val mean = Rgb((sumR.toDouble() / n).roundToInt(),
                       (sumG.toDouble() / n).roundToInt(),
                       (sumB.toDouble() / n).roundToInt())
        return RoiResult(mean, over.toDouble() / n)
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.camera.RoiSamplerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/camera/RoiSampler.kt app/src/test/java/com/example/colorgrabber/camera/RoiSamplerTest.kt
git commit -m "feat(camera): ROI 平均 RGB 取样 + 过曝检测"
```

---

## Milestone 4：数据层（Room + 相册 + CSV）

### Task 4.1：Measurement 实体 + DAO + 数据库

**Files:**
- Create: `app/src/main/java/com/example/colorgrabber/data/Measurement.kt`
- Create: `app/src/main/java/com/example/colorgrabber/data/MeasurementDao.kt`
- Create: `app/src/main/java/com/example/colorgrabber/data/AppDatabase.kt`

- [ ] **Step 1: 写实体**

```kotlin
package com.example.colorgrabber.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val sampleName: String = "",
    val note: String = "",
    val degradationTime: String = "",   // 降解时间点，可空字符串
    val source: String,                 // "live" 或 "pick"
    val imageUri: String? = null,
    val roiX: Int, val roiY: Int, val roiW: Int, val roiH: Int,
    val rawR: Int, val rawG: Int, val rawB: Int,    // 原始平均 RGB
    val normR: Int, val normG: Int, val normB: Int, // 白归一化后 RGB
    val hsvH: Double, val hsvS: Double, val hsvV: Double,
    val labL: Double, val labA: Double, val labB: Double,
    val absR: Double?, val absG: Double?, val absB: Double?, // 无参比为 null
    val gainR: Double, val gainG: Double, val gainB: Double,
    val tempAdjust: Double,
    val isReference: Boolean = false    // 是否为参比 I0
)
```

- [ ] **Step 2: 写 DAO**

```kotlin
package com.example.colorgrabber.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Insert suspend fun insert(m: Measurement): Long
    @Update suspend fun update(m: Measurement)
    @Delete suspend fun delete(m: Measurement)

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Measurement>>

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    suspend fun getAll(): List<Measurement>

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: Long): Measurement?
}
```

- [ ] **Step 3: 写数据库**

```kotlin
package com.example.colorgrabber.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Measurement::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "colorgrabber.db"
                ).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 4: 编译确认通过（Room KSP 处理）**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/data/Measurement.kt app/src/main/java/com/example/colorgrabber/data/MeasurementDao.kt app/src/main/java/com/example/colorgrabber/data/AppDatabase.kt
git commit -m "feat(data): Measurement 实体 + DAO + Room 数据库"
```

### Task 4.2：DAO 存取 instrumented 测试

**Files:**
- Create: `app/src/androidTest/java/com/example/colorgrabber/data/MeasurementDaoTest.kt`

- [ ] **Step 1: 写测试**

```kotlin
package com.example.colorgrabber.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MeasurementDao

    private fun sample() = Measurement(
        timestamp = 1000L, source = "live",
        roiX = 0, roiY = 0, roiW = 10, roiH = 10,
        rawR = 200, rawG = 100, rawB = 50, normR = 210, normG = 105, normB = 55,
        hsvH = 20.0, hsvS = 0.7, hsvV = 0.8, labL = 50.0, labA = 30.0, labB = 40.0,
        absR = null, absG = null, absB = null,
        gainR = 1.05, gainG = 1.0, gainB = 1.1, tempAdjust = 0.0
    )

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        dao = db.measurementDao()
    }

    @After fun teardown() = db.close()

    @Test fun insertAndRead() = runBlocking {
        val id = dao.insert(sample())
        val read = dao.getById(id)
        assertEquals(200, read?.rawR)
        assertEquals(1, dao.getAll().size)
    }
}
```

- [ ] **Step 2: 运行（需设备/模拟器；无设备则执行阶段补跑）**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.colorgrabber.data.MeasurementDaoTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/example/colorgrabber/data/MeasurementDaoTest.kt
git commit -m "test(data): MeasurementDao 存取 instrumented 测试"
```

### Task 4.3：CSV 导出（TDD，纯逻辑）

**Files:**
- Create: `app/src/test/java/com/example/colorgrabber/data/CsvExporterTest.kt`
- Create: `app/src/main/java/com/example/colorgrabber/data/CsvExporter.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.colorgrabber.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    private fun m(id: Long, name: String) = Measurement(
        id = id, timestamp = 1000L, sampleName = name, source = "live",
        roiX = 0, roiY = 0, roiW = 10, roiH = 10,
        rawR = 200, rawG = 100, rawB = 50, normR = 210, normG = 105, normB = 55,
        hsvH = 20.0, hsvS = 0.7, hsvV = 0.8, labL = 50.0, labA = 30.0, labB = 40.0,
        absR = null, absG = null, absB = null,
        gainR = 1.05, gainG = 1.0, gainB = 1.1, tempAdjust = 0.0
    )

    @Test fun hasBomHeaderAndOneDataRow() {
        val csv = CsvExporter.toCsv(listOf(m(1, "s1")))
        assertTrue(csv.startsWith("﻿"))        // UTF-8 BOM
        assertTrue(csv.contains("样品名"))
        assertEquals(2, csv.trim().lines().size)    // 表头行 + 1 数据行
    }

    @Test fun escapesCommaAndQuote() {
        val csv = CsvExporter.toCsv(listOf(m(1, "a,b\"c")))
        assertTrue(csv.contains("\"a,b\"\"c\""))
    }

    @Test fun nullAbsorbanceIsEmpty() {
        val csv = CsvExporter.toCsv(listOf(m(1, "s1")))
        val dataLine = csv.trim().lines().last()
        assertTrue(dataLine.contains(",,"))         // 连续空吸光度单元格
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.data.CsvExporterTest"`
Expected: 编译失败（`CsvExporter` 未定义）

- [ ] **Step 3: 写实现**

```kotlin
package com.example.colorgrabber.data

object CsvExporter {
    private val HEADERS = listOf(
        "id", "时间戳", "样品名", "备注", "降解时间点", "来源", "图片URI",
        "ROI_x", "ROI_y", "ROI_w", "ROI_h",
        "原始R", "原始G", "原始B", "归一R", "归一G", "归一B",
        "H", "S", "V", "L", "a", "b",
        "吸光度R", "吸光度G", "吸光度B",
        "增益R", "增益G", "增益B", "色温调整", "是否参比"
    )

    fun toCsv(items: List<Measurement>): String {
        val sb = StringBuilder()
        sb.append('﻿')   // UTF-8 BOM，Excel 中文不乱码
        sb.append(HEADERS.joinToString(",") { esc(it) }).append('\n')
        for (m in items) {
            val cells = listOf(
                m.id.toString(), m.timestamp.toString(), m.sampleName, m.note,
                m.degradationTime, m.source, m.imageUri ?: "",
                m.roiX.toString(), m.roiY.toString(), m.roiW.toString(), m.roiH.toString(),
                m.rawR.toString(), m.rawG.toString(), m.rawB.toString(),
                m.normR.toString(), m.normG.toString(), m.normB.toString(),
                fmt(m.hsvH), fmt(m.hsvS), fmt(m.hsvV), fmt(m.labL), fmt(m.labA), fmt(m.labB),
                m.absR?.let { fmt(it) } ?: "", m.absG?.let { fmt(it) } ?: "", m.absB?.let { fmt(it) } ?: "",
                fmt(m.gainR), fmt(m.gainG), fmt(m.gainB), fmt(m.tempAdjust),
                if (m.isReference) "1" else "0"
            )
            sb.append(cells.joinToString(",") { esc(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun fmt(d: Double): String = String.format("%.4f", d)

    private fun esc(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"" + s.replace("\"", "\"\"") + "\"" else s
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.colorgrabber.data.CsvExporterTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/data/CsvExporter.kt app/src/test/java/com/example/colorgrabber/data/CsvExporterTest.kt
git commit -m "feat(data): CSV 导出（UTF-8 BOM + 转义）"
```

### Task 4.4：AlbumStore（专属相册）+ Repository

**Files:**
- Create: `app/src/main/java/com/example/colorgrabber/data/AlbumStore.kt`
- Create: `app/src/main/java/com/example/colorgrabber/data/MeasurementRepository.kt`

> 依赖 Android framework，靠手动验证；此处给出完整实现。

- [ ] **Step 1: 写 AlbumStore**

```kotlin
package com.example.colorgrabber.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

object AlbumStore {
    private const val ALBUM = "ColorGrabber"

    /** 保存位图到系统相册 ColorGrabber/ 文件夹，返回内容 URI。 */
    fun saveImage(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + ALBUM)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    fun deleteImage(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}
```

> minSdk 24 降级说明：Android 9 及以下 `RELATIVE_PATH` 不可用，图片落在 Pictures 根目录而非 ColorGrabber 子相册；
> 这是本期接受的已知降级（相册分组在 Android 10+ 生效），功能不丢。

- [ ] **Step 2: 写 Repository**

```kotlin
package com.example.colorgrabber.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.Flow

class MeasurementRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).measurementDao()

    fun observeAll(): Flow<List<Measurement>> = dao.observeAll()
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun insert(m: Measurement): Long = dao.insert(m)
    suspend fun update(m: Measurement) = dao.update(m)

    suspend fun delete(m: Measurement, alsoDeleteImage: Boolean) {
        if (alsoDeleteImage) m.imageUri?.let { AlbumStore.deleteImage(context, Uri.parse(it)) }
        dao.delete(m)
    }

    fun saveImageToAlbum(bitmap: Bitmap, name: String): Uri? =
        AlbumStore.saveImage(context, bitmap, name)

    suspend fun exportCsv(): String = CsvExporter.toCsv(dao.getAll())
}
```

- [ ] **Step 3: 编译确认通过**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/data/AlbumStore.kt app/src/main/java/com/example/colorgrabber/data/MeasurementRepository.kt
git commit -m "feat(data): 专属相册保存 + MeasurementRepository"
```

---

## Milestone 5：相机控制（CameraX + Camera2 互操作）

### Task 5.1：CameraController

**Files:**
- Create: `app/src/main/java/com/example/colorgrabber/camera/CameraController.kt`

> 难以单测，给出完整实现 + 手动验证。用 ImageAnalysis 的 RGBA_8888 输出，免去 YUV 转换。
> 手动白平衡通过 Camera2 互操作设 AWB_MODE=OFF + COLOR_CORRECTION_GAINS；不支持时返回 false，由软件归一化兜底。

- [ ] **Step 1: 写实现**

```kotlin
package com.example.colorgrabber.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.colorgrabber.wb.Gains
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var camera: androidx.camera.core.Camera? = null
    private var camera2Control: Camera2CameraControl? = null

    /** 每帧回调：传出 ARGB 像素与宽高。节流由调用方做。 */
    var onFrame: ((px: IntArray, width: Int, height: Int) -> Unit)? = null

    @SuppressLint("UnsafeOptInUsageError")
    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy -> handleFrame(proxy) }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )
            camera2Control = camera?.cameraControl?.let { Camera2CameraControl.from(it) }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun handleFrame(proxy: ImageProxy) {
        try {
            val plane = proxy.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val w = proxy.width; val h = proxy.height
            val px = IntArray(w * h)
            val rowPadding = rowStride - pixelStride * w
            var offset = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val r = buffer.get(offset).toInt() and 0xFF
                    val g = buffer.get(offset + 1).toInt() and 0xFF
                    val b = buffer.get(offset + 2).toInt() and 0xFF
                    px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    offset += pixelStride
                }
                offset += rowPadding
            }
            onFrame?.invoke(px, w, h)
        } finally {
            proxy.close()
        }
    }

    /** 设置手动白平衡增益；不支持的机型返回 false。 */
    @SuppressLint("UnsafeOptInUsageError")
    fun setManualWhiteBalance(gains: Gains): Boolean {
        val control = camera2Control ?: return false
        return runCatching {
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE,
                    CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS,
                    RggbChannelVector(
                        gains.r.toFloat(), gains.g.toFloat(), gains.g.toFloat(), gains.b.toFloat()))
                .build()
            control.setCaptureRequestOptions(opts)
            true
        }.getOrDefault(false)
    }

    /** 锁定曝光与对焦。 */
    @SuppressLint("UnsafeOptInUsageError")
    fun lockExposureAndFocus() {
        val control = camera2Control ?: return
        runCatching {
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF)
                .build()
            control.setCaptureRequestOptions(opts)
        }
    }

    fun stop() {
        analysisExecutor.shutdown()
    }
}
```

- [ ] **Step 2: 编译确认通过**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/camera/CameraController.kt
git commit -m "feat(camera): CameraX 预览/分析 + Camera2 手动白平衡与锁定"
```

---

## Milestone 6：UI —— 取色 / 历史 / 选区

### Task 6.1：MainActivity + 导航骨架

**Files:**
- Create: `app/src/main/java/com/example/colorgrabber/MainActivity.kt`
- Create: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 写布局 `activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

- [ ] **Step 2: 写 MainActivity**

```kotlin
package com.example.colorgrabber

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.colorgrabber.ui.CaptureFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, CaptureFragment())
                .commit()
        }
    }
}
```

- [ ] **Step 3: 注册 MainActivity 为 launcher（在 `<application>` 内加入）**

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/MainActivity.kt app/src/main/res/layout/activity_main.xml app/src/main/AndroidManifest.xml
git commit -m "feat(ui): MainActivity 骨架，默认载入取色界面"
```

### Task 6.2：取色界面 CaptureFragment

**Files:**
- Create: `app/src/main/res/layout/fragment_capture.xml`
- Create: `app/src/main/java/com/example/colorgrabber/ui/SeekExt.kt`
- Create: `app/src/main/java/com/example/colorgrabber/ui/CaptureFragment.kt`

- [ ] **Step 1: 写布局 `fragment_capture.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/panel"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <View
        android:id="@+id/roiBox"
        android:layout_width="80dp"
        android:layout_height="80dp"
        android:background="@android:drawable/dialog_holo_light_frame"
        app:layout_constraintTop_toTopOf="@id/previewView"
        app:layout_constraintBottom_toBottomOf="@id/previewView"
        app:layout_constraintStart_toStartOf="@id/previewView"
        app:layout_constraintEnd_toEndOf="@id/previewView" />

    <LinearLayout
        android:id="@+id/panel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp"
        app:layout_constraintBottom_toBottomOf="parent">

        <TextView android:id="@+id/readout"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:textSize="13sp" android:text="RGB / HSV / Lab / 吸光度" />
        <TextView android:id="@+id/wbInfo"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:textSize="12sp" android:text="白平衡：未校准" />

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="色温微调" />
        <SeekBar android:id="@+id/tempSeek"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:max="200" android:progress="100" />

        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal">
            <Button android:id="@+id/btnPickWhite" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="点白校准" />
            <Button android:id="@+id/btnLock" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="锁定" />
            <Button android:id="@+id/btnSetRef" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="设参比" />
        </LinearLayout>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal">
            <Button android:id="@+id/btnRecord" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="记录" />
            <Button android:id="@+id/btnCapture" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="拍照选区" />
            <Button android:id="@+id/btnHistory" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="历史" />
        </LinearLayout>
    </LinearLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
```

> 注：`constraintlayout` 通常随 material 传递依赖；若编译报缺失，在 app 依赖加 `implementation("androidx.constraintlayout:constraintlayout:2.1.4")`。

- [ ] **Step 2: 写 `SeekExt.kt`**

```kotlin
package com.example.colorgrabber.ui

import android.widget.SeekBar

inline fun simpleSeek(crossinline onChange: (Int) -> Unit) =
    object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }
```

- [ ] **Step 3: 写 `CaptureFragment.kt`**

```kotlin
package com.example.colorgrabber.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorgrabber.R
import com.example.colorgrabber.camera.CameraController
import com.example.colorgrabber.camera.RoiRect
import com.example.colorgrabber.camera.RoiSampler
import com.example.colorgrabber.color.ColorAnalyzer
import com.example.colorgrabber.color.Rgb
import com.example.colorgrabber.data.Measurement
import com.example.colorgrabber.data.MeasurementRepository
import com.example.colorgrabber.databinding.FragmentCaptureBinding
import com.example.colorgrabber.wb.Gains
import com.example.colorgrabber.wb.WhiteBalanceEngine
import kotlinx.coroutines.launch

class CaptureFragment : Fragment() {
    private var _b: FragmentCaptureBinding? = null
    private val b get() = _b!!
    private lateinit var camera: CameraController
    private lateinit var repo: MeasurementRepository

    private var baseGains = Gains(1.0, 1.0, 1.0)   // 点白得到
    private var tempAdjust = 0.0
    private var locked = false
    private var lastNormRgb: Rgb = Rgb(0, 0, 0)
    private var lastRawRgb: Rgb = Rgb(0, 0, 0)
    private var lastRoi = RoiRect(0, 0, 0, 0)
    private var referenceRgb: Rgb? = null
    private var frameCounter = 0
    @Volatile private var pendingPickWhite = false
    @Volatile private var pendingCapture = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) camera.start()
        else Toast.makeText(requireContext(), "需要相机权限，请到设置开启", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCaptureBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = MeasurementRepository(requireContext().applicationContext)
        camera = CameraController(requireContext(), viewLifecycleOwner, b.previewView)
        camera.onFrame = ::onFrame

        b.tempSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            tempAdjust = (p - 100) / 100.0
            applyWb()
        })
        b.btnPickWhite.setOnClickListener { pendingPickWhite = true }
        b.btnLock.setOnClickListener {
            locked = !locked
            if (locked) camera.lockExposureAndFocus()
            b.btnLock.text = if (locked) "已锁定" else "锁定"
        }
        b.btnSetRef.setOnClickListener {
            referenceRgb = lastNormRgb
            Toast.makeText(requireContext(), "已设为参比 I0", Toast.LENGTH_SHORT).show()
        }
        b.btnRecord.setOnClickListener { saveCurrent() }
        b.btnCapture.setOnClickListener { pendingCapture = true }
        b.btnHistory.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, HistoryFragment())
                .addToBackStack(null).commit()
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) camera.start()
        else permLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun roiForFrame(w: Int, h: Int): RoiRect {
        val size = (minOf(w, h) * 0.2).toInt()
        return RoiRect((w - size) / 2, (h - size) / 2, size, size)
    }

    private fun onFrame(px: IntArray, w: Int, h: Int) {
        frameCounter++
        if (frameCounter % 6 != 0) return   // 节流：每 6 帧算一次
        val roi = roiForFrame(w, h)
        val res = RoiSampler.sample(px, w, h, roi)
        lastRawRgb = res.mean; lastRoi = roi

        if (pendingPickWhite) {
            baseGains = WhiteBalanceEngine.gainsFromWhite(res.mean)
            pendingPickWhite = false
            requireActivity().runOnUiThread { applyWb() }
        }
        if (pendingCapture) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            pendingCapture = false
            requireActivity().runOnUiThread { onFrameCaptured(bmp) }
        }

        val gains = WhiteBalanceEngine.applyTempAdjust(baseGains, tempAdjust)
        val norm = WhiteBalanceEngine.normalize(res.mean, gains)
        lastNormRgb = norm
        val hsv = ColorAnalyzer.toHsv(norm)
        val lab = ColorAnalyzer.toLab(norm)
        val abs = referenceRgb?.let { ColorAnalyzer.toAbsorbance(norm, it) }
        val overTip = if (res.overexposedRatio > 0.2) "  ⚠过曝" else ""
        requireActivity().runOnUiThread {
            b.readout.text = buildString {
                append("RGB ${norm.r},${norm.g},${norm.b}$overTip\n")
                append("HSV ${"%.0f".format(hsv.h)},${"%.2f".format(hsv.s)},${"%.2f".format(hsv.v)}\n")
                append("Lab ${"%.1f".format(lab.l)},${"%.1f".format(lab.a)},${"%.1f".format(lab.b)}\n")
                append(if (abs != null)
                    "吸光度 R${"%.3f".format(abs.aR)} G${"%.3f".format(abs.aG)} B${"%.3f".format(abs.aB)}"
                else "吸光度：未标定（先设参比）")
            }
        }
    }

    private fun applyWb() {
        val gains = WhiteBalanceEngine.applyTempAdjust(baseGains, tempAdjust)
        val ok = camera.setManualWhiteBalance(gains)
        b.wbInfo.text = "白平衡：色温≈${WhiteBalanceEngine.displayKelvin(tempAdjust)}K  " +
            "增益 ${"%.2f".format(gains.r)}/${"%.2f".format(gains.g)}/${"%.2f".format(gains.b)}" +
            if (!ok) "（软件模式）" else ""
    }

    private fun onFrameCaptured(bmp: Bitmap) {
        lifecycleScope.launch {
            val uri = repo.saveImageToAlbum(bmp, "CG_${System.currentTimeMillis()}")
            if (uri == null) {
                Toast.makeText(requireContext(), "保存图片失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, PickFragment.newInstance(uri.toString()))
                .addToBackStack(null).commit()
        }
    }

    private fun saveCurrent() {
        val gains = WhiteBalanceEngine.applyTempAdjust(baseGains, tempAdjust)
        val hsv = ColorAnalyzer.toHsv(lastNormRgb)
        val lab = ColorAnalyzer.toLab(lastNormRgb)
        val abs = referenceRgb?.let { ColorAnalyzer.toAbsorbance(lastNormRgb, it) }
        val m = Measurement(
            timestamp = System.currentTimeMillis(), source = "live",
            roiX = lastRoi.x, roiY = lastRoi.y, roiW = lastRoi.w, roiH = lastRoi.h,
            rawR = lastRawRgb.r, rawG = lastRawRgb.g, rawB = lastRawRgb.b,
            normR = lastNormRgb.r, normG = lastNormRgb.g, normB = lastNormRgb.b,
            hsvH = hsv.h, hsvS = hsv.s, hsvV = hsv.v,
            labL = lab.l, labA = lab.a, labB = lab.b,
            absR = abs?.aR, absG = abs?.aG, absB = abs?.aB,
            gainR = gains.r, gainG = gains.g, gainB = gains.b, tempAdjust = tempAdjust
        )
        lifecycleScope.launch {
            repo.insert(m)
            Toast.makeText(requireContext(), "已记录", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView(); camera.stop(); _b = null
    }
}
```

- [ ] **Step 4: 编译确认通过（PickFragment 尚未建，会报错——本步只校验 CaptureFragment 语法，Task 6.4 后整体编译）**

跳过单独编译，留待 Task 6.4。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/fragment_capture.xml app/src/main/java/com/example/colorgrabber/ui/CaptureFragment.kt app/src/main/java/com/example/colorgrabber/ui/SeekExt.kt
git commit -m "feat(ui): 取色界面（实时取色+手动白平衡+记录+拍照入口）"
```

### Task 6.3：历史界面 HistoryFragment + CSV 导出

**Files:**
- Create: `app/src/main/res/layout/fragment_history.xml`
- Create: `app/src/main/res/layout/item_measurement.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/java/com/example/colorgrabber/ui/MeasurementAdapter.kt`
- Create: `app/src/main/java/com/example/colorgrabber/ui/HistoryFragment.kt`
- Modify: `app/src/main/AndroidManifest.xml`（FileProvider）

- [ ] **Step 1: 写 `item_measurement.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="horizontal" android:padding="12dp">
    <ImageView android:id="@+id/thumb"
        android:layout_width="56dp" android:layout_height="56dp"
        android:scaleType="centerCrop" android:background="#222" />
    <LinearLayout android:layout_width="0dp" android:layout_weight="1"
        android:layout_height="wrap_content" android:orientation="vertical"
        android:layout_marginStart="12dp">
        <TextView android:id="@+id/title" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:textStyle="bold" />
        <TextView android:id="@+id/subtitle" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:textSize="12sp" />
    </LinearLayout>
    <Button android:id="@+id/btnDelete" android:layout_width="wrap_content"
        android:layout_height="wrap_content" android:text="删除" />
</LinearLayout>
```

- [ ] **Step 2: 写 `fragment_history.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical">
    <Button android:id="@+id/btnExport" android:layout_width="match_parent"
        android:layout_height="wrap_content" android:text="导出 CSV" />
    <androidx.recyclerview.widget.RecyclerView android:id="@+id/list"
        android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
</LinearLayout>
```

- [ ] **Step 3: 写 `MeasurementAdapter.kt`**

```kotlin
package com.example.colorgrabber.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.colorgrabber.R
import com.example.colorgrabber.data.Measurement

class MeasurementAdapter(
    private val onDelete: (Measurement) -> Unit
) : RecyclerView.Adapter<MeasurementAdapter.VH>() {
    private val items = mutableListOf<Measurement>()

    fun submit(list: List<Measurement>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val title: TextView = v.findViewById(R.id.title)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
        val btnDelete: Button = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_measurement, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        h.title.text = m.sampleName.ifEmpty { "样品#${m.id}" }
        h.subtitle.text = "RGB ${m.normR},${m.normG},${m.normB}  " +
            (if (m.absB != null) "A_B ${"%.3f".format(m.absB)}" else "未标定")
        if (m.imageUri != null) h.thumb.setImageURI(Uri.parse(m.imageUri))
        else h.thumb.setImageDrawable(null)
        h.btnDelete.setOnClickListener { onDelete(m) }
    }
}
```

- [ ] **Step 4: 写 `HistoryFragment.kt`**

```kotlin
package com.example.colorgrabber.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.colorgrabber.data.MeasurementRepository
import com.example.colorgrabber.databinding.FragmentHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class HistoryFragment : Fragment() {
    private var _b: FragmentHistoryBinding? = null
    private val b get() = _b!!
    private lateinit var repo: MeasurementRepository
    private lateinit var adapter: MeasurementAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHistoryBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = MeasurementRepository(requireContext().applicationContext)
        adapter = MeasurementAdapter(onDelete = { m ->
            lifecycleScope.launch { repo.delete(m, alsoDeleteImage = false) }
        })
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeAll().collectLatest { adapter.submit(it) }
        }
        b.btnExport.setOnClickListener { exportCsv() }
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            val csv = repo.exportCsv()
            val dir = File(requireContext().cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "colorgrabber_export.csv")
            file.writeText(csv, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                requireContext(), requireContext().packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "导出 CSV"))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
```

- [ ] **Step 5: 配置 FileProvider + file_paths**

`app/src/main/res/xml/file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="exports" path="exports/" />
</paths>
```

在 `AndroidManifest.xml` 的 `<application>` 内加入：

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/colorgrabber/ui/HistoryFragment.kt app/src/main/java/com/example/colorgrabber/ui/MeasurementAdapter.kt app/src/main/res/layout/fragment_history.xml app/src/main/res/layout/item_measurement.xml app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat(ui): 历史记录列表 + CSV 导出分享"
```

### Task 6.4：选区界面 PickFragment + RoiOverlayView

**Files:**
- Create: `app/src/main/res/layout/fragment_pick.xml`
- Create: `app/src/main/java/com/example/colorgrabber/ui/RoiOverlayView.kt`
- Create: `app/src/main/java/com/example/colorgrabber/ui/PickFragment.kt`

- [ ] **Step 1: 写 `RoiOverlayView.kt`**

```kotlin
package com.example.colorgrabber.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** 在图片上叠加一个可拖动的取色方框。 */
class RoiOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var boxSizePx = 120f
    private var cx = -1f; private var cy = -1f
    private val paint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    /** 拖动回调：传出方框左上角与边长（view 坐标）。 */
    var onBoxMoved: ((left: Float, top: Float, size: Float) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        if (cx < 0) { cx = width / 2f; cy = height / 2f }
        val half = boxSizePx / 2
        canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                cx = event.x.coerceIn(boxSizePx / 2, width - boxSizePx / 2)
                cy = event.y.coerceIn(boxSizePx / 2, height - boxSizePx / 2)
                invalidate()
                onBoxMoved?.invoke(cx - boxSizePx / 2, cy - boxSizePx / 2, boxSizePx)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
```

- [ ] **Step 2: 写 `fragment_pick.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent">
    <ImageView android:id="@+id/image"
        android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="fitCenter" />
    <com.example.colorgrabber.ui.RoiOverlayView android:id="@+id/overlay"
        android:layout_width="match_parent" android:layout_height="match_parent" />
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:layout_gravity="bottom" android:orientation="vertical" android:padding="12dp"
        android:background="#99000000">
        <TextView android:id="@+id/readout" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:textColor="#FFF" android:text="拖动方框取色" />
        <Button android:id="@+id/btnRecordPick" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="记录此点" />
    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 3: 写 `PickFragment.kt`**

```kotlin
package com.example.colorgrabber.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorgrabber.camera.RoiRect
import com.example.colorgrabber.camera.RoiSampler
import com.example.colorgrabber.color.ColorAnalyzer
import com.example.colorgrabber.data.Measurement
import com.example.colorgrabber.data.MeasurementRepository
import com.example.colorgrabber.databinding.FragmentPickBinding
import kotlinx.coroutines.launch

class PickFragment : Fragment() {
    private var _b: FragmentPickBinding? = null
    private val b get() = _b!!
    private lateinit var repo: MeasurementRepository
    private var bitmap: Bitmap? = null
    private var imageUri: String? = null
    private var curRoi = RoiRect(0, 0, 0, 0)   // 位图像素坐标

    companion object {
        private const val ARG_URI = "uri"
        fun newInstance(uri: String) = PickFragment().apply {
            arguments = Bundle().apply { putString(ARG_URI, uri) }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPickBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = MeasurementRepository(requireContext().applicationContext)
        imageUri = requireArguments().getString(ARG_URI)
        bitmap = imageUri?.let {
            requireContext().contentResolver.openInputStream(Uri.parse(it))?.use { s ->
                BitmapFactory.decodeStream(s)
            }
        }
        b.image.setImageBitmap(bitmap)
        b.overlay.onBoxMoved = { left, top, size -> onBoxMoved(left, top, size) }
        b.btnRecordPick.setOnClickListener { recordPick() }
    }

    /** overlay（全屏 view）坐标 → 位图像素坐标（ImageView 用 fitCenter）。 */
    private fun onBoxMoved(left: Float, top: Float, size: Float) {
        val bmp = bitmap ?: return
        val vw = b.image.width.toFloat(); val vh = b.image.height.toFloat()
        val scale = minOf(vw / bmp.width, vh / bmp.height)
        val dispW = bmp.width * scale; val dispH = bmp.height * scale
        val offX = (vw - dispW) / 2; val offY = (vh - dispH) / 2
        val bx = ((left - offX) / scale).toInt().coerceIn(0, bmp.width - 1)
        val by = ((top - offY) / scale).toInt().coerceIn(0, bmp.height - 1)
        val bs = (size / scale).toInt().coerceAtLeast(1)
        curRoi = RoiRect(bx, by, bs.coerceAtMost(bmp.width - bx), bs.coerceAtMost(bmp.height - by))
        showReadout()
    }

    private fun sampleCurrent() = bitmap?.let { bmp ->
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        RoiSampler.sample(px, bmp.width, bmp.height, curRoi)
    }

    private fun showReadout() {
        val res = sampleCurrent() ?: return
        val lab = ColorAnalyzer.toLab(res.mean)
        b.readout.text = "RGB ${res.mean.r},${res.mean.g},${res.mean.b}  " +
            "Lab ${"%.1f".format(lab.l)},${"%.1f".format(lab.a)},${"%.1f".format(lab.b)}"
    }

    private fun recordPick() {
        val res = sampleCurrent() ?: return
        val hsv = ColorAnalyzer.toHsv(res.mean); val lab = ColorAnalyzer.toLab(res.mean)
        val m = Measurement(
            timestamp = System.currentTimeMillis(), source = "pick", imageUri = imageUri,
            roiX = curRoi.x, roiY = curRoi.y, roiW = curRoi.w, roiH = curRoi.h,
            rawR = res.mean.r, rawG = res.mean.g, rawB = res.mean.b,
            normR = res.mean.r, normG = res.mean.g, normB = res.mean.b, // 静态图已是成像结果，norm=raw
            hsvH = hsv.h, hsvS = hsv.s, hsvV = hsv.v,
            labL = lab.l, labA = lab.a, labB = lab.b,
            absR = null, absG = null, absB = null,
            gainR = 1.0, gainG = 1.0, gainB = 1.0, tempAdjust = 0.0
        )
        lifecycleScope.launch {
            repo.insert(m)
            Toast.makeText(requireContext(), "已记录此点", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
```

- [ ] **Step 4: 整体编译确认通过**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/fragment_pick.xml app/src/main/java/com/example/colorgrabber/ui/PickFragment.kt app/src/main/java/com/example/colorgrabber/ui/RoiOverlayView.kt
git commit -m "feat(ui): 拍照存相册 + 精细选区取色"
```

---

## Milestone 7：整体验证

### Task 7.1：全部单测 + 构建 APK + 手动验证

- [ ] **Step 1: 运行所有单元测试**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 全部 PASS（ColorAnalyzer / WhiteBalanceEngine / RoiSampler / CsvExporter）

- [ ] **Step 2: 构建 Debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 3: 手动验证清单（需真机 + 白背景 + 补光 + 试管）**

1. 首次启动申请相机权限；拒绝有提示。
2. 预览正常，中心取色框可见，底部实时显示 RGB/HSV/Lab。
3. 白背景对准中心框点「点白校准」→ 白平衡信息更新，预览白处变中性。
4. 拖色温滑条，预览实时偏冷/暖；白平衡信息显示色温与增益。
5. 点「锁定」后曝光/对焦不再自动变化。
6. 对准空白显色管点「设参比」→ 吸光度从「未标定」变为数值。
7. 点「记录」→ 提示已记录；进「历史」可见该条。
8. 点「拍照选区」→ 跳到选区界面，原图显示；拖方框读数随之变；点「记录此点」入库。
9. 系统相册存在 `ColorGrabber` 相册且有照片（Android 10+）。
10. 历史界面「导出 CSV」→ 弹出分享，Excel 打开中文不乱码、字段齐全。
11. 历史项「删除」可删除记录。

- [ ] **Step 4: Commit（如手动验证有修复）**

```bash
git add -A
git commit -m "fix: 手动验证问题修复"
```

---

## 自查记录（写计划时已核对）

- **spec 覆盖**：四类色值(M1)、实时+拍照取色(M3/6.2/6.4)、手动白平衡四件套点白+色温+RGB增益+显示参数(M2/6.2，
  RGB 增益微调通过 `Gains` 直接编辑能力具备，UI 当前以色温滑条为主，RGB 增益滑条作为可选增强见下)、
  参比 I0(6.2)、本地历史+CSV+相册+标签字段(M4/6.3)、降级策略(M5)、过曝/未校准/未标定提示(6.2)、测试策略(各 TDD 任务 + 7.1) 均有任务。
- **类型一致**：`Rgb/Hsv/Lab/Absorbance/Gains/RoiRect/RoiResult/Measurement` 跨任务签名一致；
  `WhiteBalanceEngine.gainsFromWhite/applyTempAdjust/normalize/displayKelvin`、
  `ColorAnalyzer.toHsv/toLab/toAbsorbance`、`RoiSampler.sample` 命名生产/测试一致。
- **已知降级（明确决策，非占位符）**：Android 9 及以下相册不分子目录；不支持手动 WB 的机型回退软件归一化。
- **本期取舍**：RGB 增益滑条微调与「记录时填样品名/备注/降解时间点对话框」的 UI 入口未在本计划逐步展开，
  数据层与算法已完全支持；执行阶段如需，作为 Task 6.5（记录对话框）与 6.6（RGB 增益滑条）追加，不影响主流程。
