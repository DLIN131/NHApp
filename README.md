# NHApp

Android 手機用的漫畫閱讀用戶端。Jetpack Compose + Material 3 + Coil。

## 功能
- **首頁**：現在熱門 + 最新上傳，捲到底自動載入下一頁
- **熱門**：今日／本週／本月／歷來熱門排行
- **搜尋**：關鍵字、標籤語法（`tag:`、`artist:`、`-tag:` 排除…），輸入數字直接開啟編號
- **作品頁**：封面、標籤（點了可以延伸瀏覽）、全部頁面縮圖、相關作品
- **閱讀器**：上下捲動或左右翻頁（可選由右往左）、雙指縮放、雙擊放大、進度條跳頁，自動記住讀到哪一頁
- **語言過濾**：只看中文／日文／英文
- **收藏、閱讀紀錄**：存在手機本機，不需要登入
- **App 內更新**：開啟時檢查新版本，可選「立即更新」「稍後再說」或「略過這個版本」

## 下載
到 [Releases](../../releases/latest) 下載最新的 `NHApp.apk` 安裝。

固定下載網址（永遠是最新版）：
`https://github.com/DLIN131/NHApp/releases/latest/download/NHApp.apk`

每次 push 到 `main`，GitHub Actions 會自動建置簽章 APK 並發佈 Release `v1.0.<run_number>`。

## 自己建置
```
./gradlew assembleRelease
```
簽章金鑰放在 `keystore/`（不進版控）；沒有金鑰時會改用 debug 簽章。
