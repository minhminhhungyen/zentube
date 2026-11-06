# Zenvid

**Xem YouTube trên Android – không quảng cáo, không theo dõi, không cần đăng nhập.**

*Watch YouTube on Android without ads, tracking or sign-in. Free software (GPLv3). English summary at the bottom.*

> Zenvid không liên quan tới YouTube hay Google. Việc chặn quảng cáo và tải video có thể trái [điều khoản dịch vụ của YouTube](https://www.youtube.com/t/terms). Bạn tự chịu trách nhiệm khi sử dụng. Hãy ủng hộ những nhà sáng tạo bạn yêu thích bằng cách khác (thành viên kênh, Patreon, mua sản phẩm…).

## Tính năng

- **Không quảng cáo**: video được lấy trực tiếp, không đi qua trình phát quảng cáo.
- **Không cần đăng nhập**: mở là dùng. Lịch sử xem, Xem sau, video đã thích, danh sách phát, kênh đăng ký đều lưu trên máy. Có thể thêm nhiều người dùng, mỗi người có mã PIN riêng (tuỳ chọn).
- **Trang chủ**: đề xuất tính ngay trên máy từ video bạn đã xem và kênh bạn đăng ký; các mục Trực tiếp, Trò chơi, Podcast, Trailer phim.
- **Tìm kiếm**: gợi ý khi gõ, lọc Video / Kênh / Danh sách phát, dán link YouTube để mở thẳng.
- **Trình phát**: chọn chất lượng 144p–4K, tự chọn theo Wi‑Fi / 4G, tốc độ phát, phụ đề, toàn màn hình khi xoay ngang, xem tiếp từ chỗ đã dừng, tự phát video tiếp theo, bình luận.
- **Video dọc** (Shorts, video quay bằng điện thoại): khung phát tự cao lên theo đúng tỉ lệ video (tới ~70% màn hình); toàn màn hình giữ máy dọc; nút phóng to lấp đầy màn hình; cửa sổ nổi cũng dọc theo video.
- **Cử chỉ trên trình phát**: chạm 2 lần bên trái/phải để lùi/tới 10 giây (chạm tiếp để cộng dồn), chạm 2 lần ở giữa để phát/dừng; giữ ngón tay để xem nhanh 2x; vuốt ngang để tua; khi toàn màn hình vuốt dọc nửa trái chỉnh độ sáng, nửa phải chỉnh âm lượng, chụm/xoè 2 ngón để vừa khung/lấp đầy; ở khung nhỏ vuốt lên để toàn màn hình, vuốt xuống để thu nhỏ.
- **Chụp ảnh màn hình video**: lưu đúng khung hình đang phát (không dính nút điều khiển) vào `Pictures/Zenvid`.
- **Nghe ẩn**: chỉ phát âm thanh (đỡ tốn dữ liệu và pin), chạy nền khi tắt màn hình, điều khiển trên thanh thông báo. Có nút trên trình phát, trên cửa sổ nổi và ở menu ⋮ của mọi video.
- **Cửa sổ nổi (PiP)** có nút phát/dừng và Nghe ẩn.
- **Tải xuống**: video 144p–4K hoặc chỉ âm thanh (M4A); tải nền có thông báo, tự tiếp tục khi mở lại app; lưu vào `Movies/Zenvid` và `Music/Zenvid`; xem lại trong app khi không có mạng.
- **Nhập / sao lưu**: nhập kênh đăng ký từ Google Takeout (`subscriptions.csv`) hoặc NewPipe (`.json`); sao lưu / khôi phục toàn bộ dữ liệu ra JSON.
- Mở link YouTube từ trình duyệt hoặc nút Chia sẻ của app khác.

## Quyền riêng tư

- Không có Firebase, Google Play Services, thư viện quảng cáo, thống kê hay báo lỗi tự động.
- Quyền: Internet, trạng thái mạng, thông báo, dịch vụ chạy nền (phát nhạc, tải xuống). Các quyền ID quảng cáo bị gỡ khỏi manifest.
- Dữ liệu chỉ nằm trên máy; tắt sao lưu đám mây (`allowBackup=false`).
- Khi app bị lỗi, nội dung lỗi chỉ lưu trên máy. Lần mở sau bạn thấy nó và tự quyết định có chia sẻ không.
- Kiểm tra bản mới: mỗi ngày một yêu cầu tới `api.github.com`, không kèm thông tin về bạn; tắt được trong Cài đặt.
- App phải kết nối tới máy chủ YouTube để lấy video và ảnh, nên YouTube thấy địa chỉ IP của bạn như khi dùng trình duyệt.

## Cài đặt

Tải file `zenvid-x.y.z.apk` ở trang [**Releases**](https://github.com/minhminhhungyen/zentube/releases), mở trên điện thoại và cho phép "Cài ứng dụng không rõ nguồn gốc". Cần Android 8.0 trở lên.

Kiểm tra file tải về đúng bản gốc: so mã SHA-256 với file `SHA256SUMS.txt` trong cùng bản phát hành.

Zenvid **không có trên Google Play** (chính sách của Google Play không cho phép app chặn quảng cáo / tải video YouTube). Chỉ tải từ trang Releases của dự án.

## Khi video không phát được

YouTube thường xuyên thay đổi để chặn ứng dụng bên thứ ba. Zenvid dùng thư viện [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) – cùng thư viện với NewPipe. Khi YouTube thay đổi:

1. Mở `app/build.gradle.kts`, tìm dòng `NewPipeExtractor:...`.
2. Thay mã phiên bản bằng bản mới nhất mà NewPipe đang dùng (file `gradle/libs.versions.toml` trong kho [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe), dòng `teamnewpipe-newpipe-extractor`).
3. Tăng `versionCode`/`versionName`, phát hành bản mới.

Khi YouTube từ chối một nguồn video (lỗi 403), app tự thử nguồn khác trước khi báo lỗi. Mạng của máy chủ / VPN hay bị chặn hơn mạng gia đình và 4G.

## Tự build

Cần JDK 17 và Android SDK (cách dễ nhất: cài Android Studio rồi mở thư mục này).

```bash
./gradlew assembleDebug          # bản thử, cài được ngay: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # kiểm thử (một số bài cần mạng để gọi YouTube thật)
```

### Ký bản phát hành

Khoá ký **không bao giờ** được đưa lên kho mã (đã có trong `.gitignore`). Ai có khoá đều phát hành được bản "cập nhật" giả cho người dùng của bạn.

1. Tạo khoá một lần và cất giữ cẩn thận (mất khoá thì người dùng phải gỡ app cài lại):
   ```bash
   keytool -genkeypair -v -keystore zenvid-release.jks -alias zenvid -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Build trên máy: chép `keystore.properties.example` thành `keystore.properties`, điền thông tin, rồi `./gradlew assembleRelease`.
   Hoặc dùng biến môi trường `ZENVID_KEYSTORE`, `ZENVID_KEYSTORE_PASSWORD`, `ZENVID_KEY_ALIAS`, `ZENVID_KEY_PASSWORD`.

### Phát hành tự động bằng GitHub Actions

1. Trong kho GitHub: **Settings → Secrets and variables → Actions**, thêm 4 secret:
   `KEYSTORE_BASE64` (kết quả của `base64 -w0 zenvid-release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
2. Tăng `versionName` trong `app/build.gradle.kts` (vd. `1.8.0`) và `versionCode`, commit.
3. Gắn tag và đẩy lên: `git tag v1.8.0 && git push origin v1.8.0`.
4. GitHub tự build, ký và tạo bản phát hành kèm `zenvid-1.8.0.apk` và `SHA256SUMS.txt`. Bản build này tự trỏ phần "kiểm tra bản mới" về chính kho của bạn.

Build trên máy mà muốn bật kiểm tra bản mới: thêm `-Pzenvid.updateRepo=chủ/kho` (hoặc đặt `zenvid.updateRepo=` trong `gradle.properties`).

## Cấu trúc mã nguồn

```
app/src/main/java/vn/zenvid/
├── App.kt, MainActivity.kt        khởi tạo, cửa sổ nổi, mở link
├── data/
│   ├── YouTube.kt                 tìm kiếm, video, kênh, danh sách, bảng tin, đề xuất
│   ├── Library.kt                 người dùng, lịch sử, danh sách, nhập/sao lưu
│   ├── Db.kt                      cơ sở dữ liệu Room
│   ├── Prefs.kt                   cài đặt
│   ├── Models.kt                  kiểu dữ liệu + định dạng tiếng Việt
│   └── HttpDownloader.kt          kết nối mạng (OkHttp)
├── download/                      tải video: chọn định dạng, tải theo khúc, ghép hình+tiếng, dịch vụ nền
├── player/                        trình phát (Media3/ExoPlayer), chọn luồng, phát nền, Nghe ẩn
├── support/                       kiểm tra bản mới, ghi lỗi trên máy
└── ui/                            giao diện Jetpack Compose
```

## Đóng góp

Báo lỗi và góp ý ở mục **Issues**. Khi app bị lỗi, màn hình báo lỗi có nút chia sẻ nội dung lỗi – dán vào issue giúp sửa nhanh hơn. Pull request luôn được chào đón.

## Giấy phép

Zenvid là phần mềm tự do theo giấy phép **GNU GPL v3** (xem file [LICENSE](LICENSE)). Bạn được dùng, sửa và phân phối lại, với điều kiện bản phân phối lại cũng công khai mã nguồn theo GPLv3.

Zenvid sử dụng [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPLv3), [Media3/ExoPlayer](https://github.com/androidx/media), [OkHttp](https://github.com/square/okhttp), [Coil](https://github.com/coil-kt/coil) và Jetpack (Apache 2.0).

---

## English

Zenvid is a free (GPLv3) Android client for YouTube: no ads, no tracking, no account needed. History, Watch later, likes, playlists and subscriptions are stored on the device only. It supports quality selection up to 4K, background "listen" mode (audio only), picture-in-picture, downloads (video or audio) with offline playback, and import from Google Takeout / NewPipe. It uses NewPipeExtractor and is not affiliated with YouTube or Google. Ad blocking and downloading may violate YouTube's Terms of Service; use at your own risk. The UI is currently in Vietnamese – translations are welcome.
