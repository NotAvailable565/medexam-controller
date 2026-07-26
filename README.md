# 医考助手 — 手柄刷医考帮 🎮

用手柄控制医考帮答题，不依赖屏幕坐标，题目长短不影响按键精准度。

## 工作原理

手柄按键 → 无障碍服务 → 在屏幕上找到"A.""B."等按钮 → 模拟点击

---

## 🚀 如何获取 APK（无需安装任何开发工具）

### 第一步：注册 GitHub

1. 打开浏览器，访问 https://github.com
2. 点右上角 **Sign up**
3. 输入邮箱、密码、用户名，完成注册
4. 去邮箱里点验证链接

### 第二步：创建仓库

1. 登录后，点右上角 **+** → **New repository**
2. Repository name 填 `medexam-controller`
3. 选 **Public**（公开）
4. **不要勾选** "Add a README file"
5. 点 **Create repository**

### 第三步：上传文件

1. 在仓库页面，点 **uploading an existing file**（蓝色链接）
2. 把 **整个 `medexam-controller` 文件夹** 拖进浏览器窗口
   > 文件夹位置：`C:\Users\axiao\Documents\Codex\2026-07-26\new-chat\work\medexam-controller`
   > 或者直接拖 zip 包：`C:\Users\axiao\Documents\Codex\2026-07-26\new-chat\work\medexam-controller.zip`
3. 等待文件上传完成
4. 页面下方点 **Commit changes**（绿色按钮）

### 第四步：下载 APK

1. 上传完成后，GitHub 会**自动开始编译**（约 3-5 分钟）
2. 点仓库顶部的 **Actions** 标签
3. 看到 `Build APK` 的工作流在运行，等它变绿 ✓
4. 点进去，页面底部 **Artifacts** 区域
5. 点击 `medexam-controller-apk` 下载

### 第五步：安装到平板

把下载的 APK 传到平板上安装。

---

## 📱 安装后配置

### 1. 获取医考帮的包名

1. 打开"医考助手"App
2. 打开**调试模式**（点"调试模式：关"按钮切换为开）
3. 切换到医考帮 App，按手柄 **L1 键**
4. 屏幕会出现 Toast 提示，显示类似 `com.xxx.yikaobang` 的包名
5. 回到医考助手，把包名填入输入框，点**保存**

### 2. 启用无障碍服务

1. 点"打开无障碍设置"
2. 找到"医考助手"，开启
3. 确认系统弹出的权限提示

### 3. ColorOS 保活（必须做，不然会被系统杀掉）

- **锁定后台**：多任务界面长按"医考助手"→ 锁定
- **关闭电池优化**：设置 → 应用 → 医考助手 → 电池 → 不优化
- **开启自启动**：设置 → 应用 → 自启动 → 允许

### 4. 开始使用

关闭调试模式，打开医考帮，手柄就能用了。

---

## 🎯 按键映射

| 手柄按键 | 功能 |
|---------|------|
| A / B / X / Y | 选择选项 A / B / C / D |
| R1（右肩键）| 选择选项 E |
| L1（左肩键）| 提交 / 确认 |
| D-Pad 左 / 右 | 上一题 / 下一题 |
| D-Pad 上 / 下 | 滚动 |
| Start | 下一题 |
| Select | 上一题 |

---

## 🛠 如果按键不灵

修改 `app/src/main/java/com/medexam/controller/MedExamService.kt` 中的匹配文字，比如把 "上一题" 改成你 App 里实际显示的文字。重新上传，GitHub 自动重新编译。
