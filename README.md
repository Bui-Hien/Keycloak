# 🚀 Fullstack Keycloak Integration (Spring Boot & React)

Dự án tích hợp bảo mật **Keycloak IAM** cho ứng dụng Fullstack sử dụng **Spring Boot (Backend)** và **React + Vite + TypeScript (Frontend)**. Dự án cung cấp giải pháp xác thực (Authentication) và phân quyền (Authorization) tập trung, an toàn và chuẩn hóa theo giao thức OAuth2/OIDC.

---

## 📂 Cấu Trúc Dự Án (Project Structure)

Thư mục chính của dự án được chia làm 2 phần rõ rệt:

```text
Keycloak/
├── 📁 Backend/          # Spring Boot Application (REST APIs, Security Configuration)
└── 📁 Frontend/         # React Application (Vite, TS, Tailwind/CSS UI, Keycloak Integration)
```

---

## 🛠️ Công Nghệ Sử Dụng (Tech Stack)

### 💻 Backend (Spring Boot)
*   **Java 17** & **Spring Boot 4.0.6**
*   **Spring Security** (OAuth2 Resource Server)
*   **Spring Data JPA** & **MySQL**
*   **Lombok** (Giúp rút gọn code Boilerplate)

### 🎨 Frontend (React)
*   **React 19** & **Vite** (HMR & Fast Build)
*   **TypeScript** (Đảm bảo Type-safety)
*   **CSS** (Giao diện tùy chỉnh, responsive)

### 🔐 Identity & Access Management (IAM)
*   **Keycloak** (Xác thực người dùng, quản lý Roles & Clients, cung cấp JWT Tokens)

---

## 📘 Các Luồng Xác Thực Trong Keycloak (Authentication Flows)

Keycloak hỗ trợ nhiều cơ chế xác thực dựa trên các đặc tả tiêu chuẩn của **OAuth 2.0** và **OpenID Connect (OIDC)**. Dưới đây là hướng dẫn chi tiết về 6 luồng xác thực phổ biến nhất giúp bạn học tập và chọn lựa giải pháp phù hợp cho từng loại ứng dụng.

---

### 1. Standard Flow (Authorization Code Flow)
> 🌟 **Mức độ khuyến nghị:** Rất cao (Tiêu chuẩn bảo mật vàng cho ứng dụng Web).

#### 🧐 Ý nghĩa & Mô tả:
Đây là luồng an toàn nhất và được khuyến khích sử dụng nhiều nhất cho các ứng dụng Web truyền thống (Server-side như Spring Boot, Laravel) hoặc các ứng dụng Single Page App (React, Angular, Vue - kết hợp thêm cơ chế bảo mật **PKCE - Proof Key for Code Exchange**).

#### 🔄 Quy trình hoạt động (Step-by-Step):
1. **Yêu cầu đăng nhập:** Người dùng truy cập ứng dụng của bạn và bấm "Đăng nhập".
2. **Chuyển hướng:** Ứng dụng chuyển hướng (redirect) trình duyệt của người dùng sang giao diện đăng nhập của Keycloak.
3. **Xác thực tại Keycloak:** Người dùng nhập thông tin tài khoản (và mã xác thực 2 lớp MFA nếu cấu hình) trực tiếp trên trang của Keycloak. Ứng dụng hoàn toàn **không** biết thông tin mật khẩu này.
4. **Nhận Code:** Sau khi đăng nhập thành công, Keycloak chuyển hướng người dùng quay lại ứng dụng cùng một mã tạm thời gọi là **Authorization Code**.
5. **Đổi Token:** Ở phía sau (backchannel), ứng dụng sử dụng Authorization Code này gửi trực tiếp lên Keycloak Server để đối chiếu.
6. **Trả về Token:** Keycloak xác minh mã hợp lệ và trả về các Token chính thức: **Access Token**, **ID Token**, và **Refresh Token**.

#### 🔑 Khi nào nên dùng?
* Hầu như luôn luôn sử dụng cho các ứng dụng có giao diện tương tác trực tiếp với người dùng cuối (ví dụ: CRM, HRM, E-Commerce).
* Đối với Single Page App (React, Angular), bắt buộc phải kết hợp với **PKCE** để chống tấn công đánh cắp Authorization Code trên trình duyệt.

---

### 2. Direct Access Grants (Resource Owner Password Credentials Grant)
> ⚠️ **Mức độ khuyến nghị:** Thấp (Không khuyến khích, có nguy cơ bảo mật).

#### 🧐 Ý nghĩa & Mô tả:
Luồng này cho phép ứng dụng thu thập trực tiếp tài khoản (Username) và mật khẩu (Password) của người dùng từ form giao diện tự thiết kế của ứng dụng, sau đó gửi thông tin này bằng một API request đồng thời lên Keycloak để lấy Token.

#### 🔄 Quy trình hoạt động (Step-by-Step):
1. **Nhập liệu:** Người dùng nhập trực tiếp Username và Password vào form đăng nhập trên ứng dụng của bạn.
2. **Yêu cầu Token:** Ứng dụng gửi một request dạng POST chứa trực tiếp username, password, client_id (và client_secret nếu cần) lên Keycloak endpoint `/token`.
3. **Cấp Token:** Keycloak xác thực thông tin và phản hồi bằng Access Token và Refresh Token ngay lập tức.

#### ❌ Điểm yếu bảo mật:
* **Mất tính độc lập:** Làm mất đi ý nghĩa chính của Keycloak (Single Sign-On - SSO và bảo mật tập trung). Ứng dụng của bạn buộc phải xử lý mật khẩu thô của người dùng.
* **Nguy cơ rò rỉ:** Nếu mã nguồn ứng dụng bị hack hoặc lập trình viên lưu log không an toàn, mật khẩu của người dùng có thể bị lộ.
* Không hỗ trợ các tính năng hiện đại như đăng nhập qua bên thứ ba (Google, Facebook) hoặc MFA trực tiếp.

#### 🔑 Khi nào nên dùng?
* Chỉ dùng cho các ứng dụng cũ (Legacy Systems) không thể cập nhật để hỗ trợ chuyển hướng (redirect) giao diện.
* Sử dụng trong môi trường viết code kiểm thử tự động (**Automation Tests** / Integration Tests) để giả lập đăng nhập nhanh chóng.
* **Khuyến cáo:** Nên vô hiệu hóa (Disable) luồng này trên môi trường Production.

---

### 3. Implicit Flow
> 🚫 **Mức độ khuyến nghị:** CẤM SỬ DỤNG (Đã bị khai tử - Deprecated).

#### 🧐 Ý nghĩa & Mô tả:
Đây là luồng xác thực cũ được thiết kế cho các ứng dụng chạy trên trình duyệt (JavaScript-only) trước khi có các tiêu chuẩn bảo mật tốt hơn. 

#### 🔄 Quy trình hoạt động (Step-by-Step):
1. **Yêu cầu đăng nhập:** Người dùng bấm đăng nhập trên trình duyệt.
2. **Chuyển hướng:** Ứng dụng chuyển sang Keycloak để đăng nhập.
3. **Cấp trực tiếp Token:** Thay vì trả về Authorization Code như Standard Flow, Keycloak sẽ **trả thẳng Access Token** về trình duyệt thông qua tham số trên URL (`#access_token=...`).

#### ❌ Tại sao bị khai tử?
* **Cực kỳ kém an toàn:** Vì Access Token được truyền qua URL hiển thị công khai trên thanh địa chỉ, nó rất dễ bị ghi lại trong lịch sử trình duyệt (Browser History), bị đánh cắp bởi các extension độc hại hoặc thông qua tấn công XSS (Cross-Site Scripting).
* Không hỗ trợ cấp Refresh Token bảo mật.

#### 🔑 Khi nào nên dùng?
* **Không dùng dưới mọi hình thức.** Đã được thay thế hoàn toàn bởi **Standard Flow kết hợp PKCE**.

---

### 4. Service Accounts Roles (Client Credentials Grant)
> 🤖 **Mức độ khuyến nghị:** Rất cao (Tiêu chuẩn cho tích hợp hệ thống M2M).

#### 🧐 Ý nghĩa & Mô tả:
Luồng này dành riêng cho việc giao tiếp trực tiếp giữa máy với máy (Machine-to-Machine - M2M) hoặc hệ thống với hệ thống mà **không có sự tham gia của con người** (người dùng cuối).

#### 🔄 Quy trình hoạt động (Step-by-Step):
1. **Yêu cầu Token:** Một hệ thống (Ví dụ: Hệ thống HRM của doanh nghiệp) cần gọi API của hệ thống khác (Ví dụ: Hệ thống CRM). Hệ thống HRM sẽ gửi trực tiếp `client_id` và `client_secret` của chính nó tới Keycloak.
2. **Cấp Token:** Keycloak xác minh thông tin định danh của Client và trả về một Access Token đại diện cho chính thực thể ứng dụng đó (Service Account).
3. **Gọi dịch vụ:** Ứng dụng HRM đính kèm token này vào header `Authorization: Bearer <token>` để gọi API sang CRM.

#### 🔑 Khi nào nên dùng?
* Viết các dịch vụ chạy tự động ngầm (Cronjobs, Background Tasks, Sync Services) - Ví dụ: Đồng bộ dữ liệu nhân sự lúc 12h đêm.
* Giao tiếp nội bộ giữa các dịch vụ trong kiến trúc Microservices.

---

### 5. OAuth 2.0 Device Authorization Grant (Device Flow)
> 📺 **Mức độ khuyến nghị:** Cao (Khi phát triển trên thiết bị không có trình duyệt/nhập liệu kém).

#### 🧐 Ý nghĩa & Mô tả:
Luồng xác thực này được thiết kế dành riêng cho các thiết bị bị giới hạn về khả năng nhập liệu (không có bàn phím đầy đủ) hoặc hoàn toàn không có trình duyệt web để hiển thị giao diện đăng nhập (ví dụ: Smart TV, máy chơi game console PlayStation/Xbox, thiết bị IoT).

#### 🔄 Quy trình hoạt động (Step-by-Step):
1. **Khởi động:** Thiết bị (Smart TV) gửi yêu cầu đăng nhập lên Keycloak.
2. **Cấp mã:** Keycloak trả về hai mã: **Device Code** (dành cho thiết bị) và **User Code** (đoạn mã ngắn gồm 6-8 chữ cái dành cho người dùng).
3. **Hiển thị:** TV hiển thị mã **User Code** kèm theo một đường dẫn (hoặc mã QR) lên màn hình cho người dùng xem.
4. **Xác thực:** Người dùng sử dụng điện thoại hoặc máy tính truy cập đường dẫn, đăng nhập tài khoản của họ và nhập mã **User Code** hiển thị trên TV.
5. **Thăm dò (Polling):** Trong lúc đó, TV liên tục gửi yêu cầu thăm dò (poll) lên Keycloak để kiểm tra xem người dùng đã nhập mã thành công chưa.
6. **Hoàn thành:** Ngay khi người dùng phê duyệt thành công trên điện thoại, Keycloak sẽ cấp Access Token cho TV ở lượt poll tiếp theo, giúp TV tự động đăng nhập.

#### 🔑 Khi nào nên dùng?
* Các ứng dụng chạy trên Smart TV (Netflix, YouTube), máy chơi game.
* Ứng dụng chạy trên thiết bị IoT, máy chấm công thông minh, màn hình Dashboard văn phòng chuyên dụng.

---

### 6. OIDC CIBA Grant (Client Initiated Backchannel Authentication)
> 🛡️ **Mức độ khuyến nghị:** Rất cao (Cho các giao dịch tài chính hoặc phê duyệt tác vụ nhạy cảm).

#### 🧐 Ý nghĩa & Mô tả:
CIBA là cơ chế xác thực nâng cao, tách biệt hoàn toàn thiết bị yêu cầu đăng nhập (như máy tính hoặc máy POS) và thiết bị thực hiện xác thực trực tiếp của người dùng (như điện thoại thông minh cá nhân có cài đặt ứng dụng bảo mật). Điểm đặc biệt của CIBA là không có bước chuyển hướng (Redirect) trình duyệt nào diễn ra.

#### 🔄 Quy trình hoạt động (Step-by-Step):
1. **Yêu cầu đăng nhập:** Người dùng ngồi trên trình duyệt máy tính nhập ID/Email và nhấn "Đăng nhập". Ứng dụng gửi trực tiếp yêu cầu xác thực này lên Keycloak bằng backchannel.
2. **Bắn thông báo:** Keycloak nhận diện người dùng và bắn một thông báo đẩy (**Push Notification**) về điện thoại thông minh đã đăng ký của người dùng đó (App Authenticator của doanh nghiệp).
3. **Xác nhận sinh trắc học:** Người dùng mở điện thoại, xác nhận giao dịch đăng nhập bằng quét vân tay, FaceID hoặc mật khẩu bảo mật riêng.
4. **Phê duyệt:** Người dùng nhấn "Đồng ý/Approve" trên điện thoại.
5. **Nhận Token:** Keycloak nhận phản hồi đồng ý từ thiết bị di động, lập tức cấp Token trực tiếp cho ứng dụng trên máy tính mà không cần người dùng phải làm bất kỳ thao tác nào trên trình duyệt máy tính.

#### 🔑 Khi nào nên dùng?
* Hệ thống **Ngân hàng, Fintech, Ví điện tử** (Phê duyệt giao dịch chuyển tiền hoặc thanh toán online).
* Phê duyệt các tác vụ nhân sự nhạy cảm cấp cao trong doanh nghiệp (như phê duyệt chi lương, thay đổi bảng lương nhân sự, ký hợp đồng lao động điện tử).

---

## ⚙️ Hướng Dẫn Cấu HÌnh Keycloak (Keycloak Setup)

Để chạy được dự án, bạn cần cấu hình một Client trong Keycloak:

1.  **Tạo Realm mới** (Ví dụ: `my-realm`).
2.  **Tạo Client** cho React Frontend:
    *   **Client ID**: `react-client` (hoặc tên tùy chọn).
    *   **Client Protocol**: `openid-connect`.
    *   **Access Type**: `public` (hoặc chọn Standard Flow với PKCE).
    *   **Valid Redirect URIs**: `http://localhost:5173/*` (địa chỉ chạy React app).
    *   **Web Origins**: `http://localhost:5173`.
3.  **Tạo Roles & Users**:
    *   Tạo các roles như `ROLE_USER`, `ROLE_ADMIN`.
    *   Tạo người dùng mẫu và gán quyền tương ứng để kiểm tra phân quyền.
