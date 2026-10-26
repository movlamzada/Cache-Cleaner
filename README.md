# 🧹 Cache Cleaner

Rootsuz Android tətbiqi. **Əlçatanlıq xidməti** vasitəsilə cihazdakı bütün tətbiqlərin (sistem tətbiqləri daxil) yalnız **keşini** avtomatik təmizləyir.

- 🔒 Hesablara, şəxsi fayllara və məlumatlara **toxunmur**. Yalnız "Keşi təmizlə" düyməsinə basır.
- 📊 Təmizlənən keş MB / GB olaraq canlı göstərilir, irəliləyiş faizlə görünür.
- 🪟 Proses başlayanda avtomatik **PiP** (kiçik pəncərə) rejiminə keçir.
- ⏹ İstənilən vaxt **Dayandır** düyməsi var.
- 📋 Sonda hesabat: neçə MB təmizləndi, nə qədər vaxt çəkdi, hansı tətbiqlər təmizləndi.
- ✅ Android 7.0 və yuxarı. Samsung S23 Ultra və Lenovo Xiaoxin Pad-də sınaqdan keçib.

> Hər istehsalçının Ayarlar ekranı fərqlidir. Düyməni tapa bilmədiyi tətbiqi səssizcə keçir (hesabatda "Keçilən" kimi göstərilir). Bu test versiyasıdır.

---

## 📥 Yükləmə

**Releases** bölməsindən `app-debug.apk` faylını yükləyin.

Repo-dakı `install.bat` (Windows) və `install.sh` (Mac/Linux) faylları quraşdırmanı asanlaşdırır.

---

## 🚀 Quraşdırma

APK-nı birbaşa açanda **Play Protect** və ya **Samsung Auto Blocker** bloklaya bilər. Bu normaldır, çünki tətbiq Play Store-da deyil.

### Üsul 1: Birbaşa quraşdırma
1. `app-debug.apk` faylını telefona yükləyin və **Fayllar** tətbiqindən açın.
2. "Naməlum mənbə" icazəsi istəsə, verin.
3. **Play Protect** və ya **Auto Blocker** xəta versə, söndürün və ya Üsul 2-yə keçin:
   - **Play Protect:** Play Store → profil şəkli → Play Protect → ⚙ → "Tətbiqləri skan et"-i söndürün. (Pəncərədə **Daha ətraflı → Yenə də quraşdır** varsa, onu seçin.)
   - **Samsung Auto Blocker:** Ayarlar → Təhlükəsizlik və məxfilik → **Auto Blocker** → söndürün.

### Üsul 2: ADB ilə (bloku keçir, ən etibarlı) ✅
Birbaşa quraşdırma alınmırsa və ya Play Protect söndürülmürsə, bu yol işləyir.

**Telefonda hazırlıq (bir dəfəlik):**
1. **Ayarlar → Telefon haqqında → Proqram məlumatı → Build nömrəsi**-nə **7 dəfə** basın.
2. **Developer options → USB debugging** aktiv edin.
3. Telefonu USB kabellə kompüterə qoşun. USB rejimi soruşsa, **Fayl köçürmə** seçin.
4. **"USB debugging-ə icazə verilsin?"** pəncərəsində **"Həmişə icazə ver"** işarələyib **İcazə ver** basın.

**Windows:**
1. Rəsmi [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) arxivini yükləyin və **Extract All** edin.
2. `app-debug.apk` və `install.bat` fayllarını açılmış `platform-tools` qovluğuna kopyalayın.
3. `install.bat` faylına **iki dəfə klikləyin**. `Success` yazısı çıxsa, quraşdırılıb.

Əl ilə: `platform-tools` qovluğunun ünvan xanasına `cmd` yazıb **Enter** basın, sonra:
```
adb devices
adb install -r app-debug.apk
```
`adb devices` siyahısında `device` yazılmalıdır (`unauthorized` olsa, telefonda icazəni təsdiqləyin).

**Mac / Linux:** `platform-tools` qovluğunda
```
./adb devices
./adb install -r app-debug.apk
```
(və ya APK ilə `install.sh` faylını həmin qovluğa qoyub `sh install.sh` işlədin)

**Kompüter olmadan (Termux ilə, Android 11+):**
1. Developer options → **Wireless debugging** aktiv edin (Wi-Fi-a qoşulu olun).
2. [Termux](https://f-droid.org/packages/com.termux/) quraşdırın və yazın:
   ```
   pkg update
   pkg install android-tools
   termux-setup-storage
   ```
3. Ekranı bölün: biri Termux, biri Wireless debugging. **Pair device with pairing code** basın:
   ```
   adb pair localhost:PAIRING_PORT
   adb connect localhost:MAIN_PORT
   adb install -r /sdcard/Download/app-debug.apk
   ```

---

## ⚙️ İlk işə salma (icazələr)

Tətbiq sizi addım-addım yönləndirir. Üç icazə **mütləq** lazımdır, icazəsiz proses başlamır:

1. **Üstə görünmə** (Display over other apps) → Cache Cleaner üçün aktiv edin.
2. **PiP** (Picture-in-Picture) → icazə verin.
3. **Əlçatanlıq xidməti** → **Ayarlar → Əlçatanlıq → Quraşdırılmış tətbiqlər → Cache Cleaner** → **aktivləşdirin** və "İcazə ver" basın.

### Əlçatanlıq xidməti aktiv olmursa (boz görünür)
Android 13+ yan yüklənmiş tətbiqlərdə bunu bloklayır:
1. **Ayarlar → Tətbiqlər → Cache Cleaner** (və ya tətbiqdəki **Tətbiq məlumatı** düyməsi).
2. Sağ yuxarıda **⋮ → Məhdud ayarlara icazə ver** (Allow restricted settings).
3. Əlçatanlıq ayarlarına qayıdıb xidməti aktiv edin.

Yenə alınmırsa, ADB ilə:
```
adb shell settings put secure enabled_accessibility_services az.cachecleaner/az.cachecleaner.CleanerService
adb shell settings put secure accessibility_enabled 1
```
Sonra tətbiqi tam bağlayıb yenidən açın.

---

## ▶️ İstifadə
1. Tətbiqi açın, **TƏMİZLƏ** düyməsinə basın.
2. Tətbiq PiP pəncərəsinə keçir. Ayarlar səhifələri avtomatik açılıb bağlanır, **əl vurmayın**.
3. Dayandırmaq üçün **DAYANDIR** düyməsinə (və ya PiP-dəki ⏹ və ekrandakı çipə) basın.
4. Bitəndə yekun hesabat çıxır.

300+ tətbiqli cihazda proses bir neçə dəqiqə çəkə bilər. Telefonu söndürməyin.

---

## ❗ Xətalar və həllər

| Problem | Həll |
|---|---|
| Quraşdırma "bloklandı" / "App not installed" | Play Protect və Auto Blocker-i söndürün, ya da ADB ilə quraşdırın |
| `'adb' is not recognized` | `cmd`-ni `platform-tools` qovluğunda açın |
| `no devices/emulators found` | Kabeli dəyişin, USB rejimini "Fayl köçürmə" edin, USB debugging yoxlayın |
| `unauthorized` | Telefonda USB debugging icazəsini təsdiqləyin |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall az.cachecleaner` yazın, yenidən quraşdırın |
| `INSTALL_FAILED_USER_RESTRICTED` | Auto Blocker-i söndürün |
| Əlçatanlıq xidməti boz | "Məhdud ayarlara icazə ver" (yuxarıda) |
| Bəzi tətbiqlər "Keçilən" olur | Normaldır. Həmin tətbiqin Ayarlar səhifəsində düymə tapılmayıb |

## 🗑 Silmək
Tətbiqi adi qaydada silin və ya: `adb uninstall az.cachecleaner`

## 🔨 Özün qurmaq
Repo-nu **Fork** edin → **Actions → Build APK → Run workflow**. APK **Artifacts** bölməsində yaranır.

## 🔐 Təhlükəsizlik
Xidmət yalnız mətnində "cache / keş" və təmizləmə feli olan, "data / məlumat" sözü olmayan düymələrə basır. Heç bir təsdiq düyməsinə ("OK", "Delete" və s.) **basılmır**. Tətbiq heç bir şəbəkə icazəsi istəmir və məlumat göndərmir. Kod açıqdır, özünüz yoxlaya bilərsiniz.

> ⚠️ **Xiaomi / Redmi / POCO:** bu cihazlarda "Clear data" düyməsi menyu açır və menyudan yalnız "Clear cache" seçilir. Bu hissə hələ real cihazda yoxlanmayıb. İlk dəfə **vacib məlumatı olmayan və ya ehtiyat nüsxəsi olan** cihazda sınayın və prosesə baxın.

---

## 🇬🇧 Quick start (English)
1. Download `app-debug.apk` from **Releases**.
2. If the install is blocked: turn off **Play Protect scanning** and Samsung **Auto Blocker**, or install with ADB.
3. ADB: enable Developer options (tap Build number 7 times) → **USB debugging**, connect the phone, allow the prompt, then run `adb install -r app-debug.apk` from the official [platform-tools](https://developer.android.com/tools/releases/platform-tools) folder (or double-click `install.bat` placed there with the APK).
4. Open the app and grant: **Display over other apps**, **PiP**, **Accessibility service**. On Android 13+: App info → ⋮ → **Allow restricted settings**.
5. Tap **TƏMİZLƏ** (Clean).
