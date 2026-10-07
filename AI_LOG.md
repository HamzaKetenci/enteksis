# AI Geliştirme ve Doğrulama Günlüğü (AI_LOG.md)

Bu belge, **Enteksis Görev Otomasyonu Landing Page ve Talep Formu** projesinin geliştirilmesi sırasında yapay zeka asistanı (Antigravity) ile birlikte atılan adımları, alınan mimari ve tasarım kararlarını, karşılaşılan gerçek teknik engelleri ve doğrulama süreçlerini kronolojik olarak belgeler.

---

## 1. Mimari Planlama ve İnceleme Aşaması
- **Yapılan İş:** Kod yazımına geçilmeden önce tüm bileşenleri, veri tabanı şemasını, API sözleşmelerini ve riskleri içeren detaylı `architecture-plan.md` hazırlandı.
- **Tasarım Seçimleri ve Kararlar:**
  - **JPA yerine Spring JDBC (JdbcTemplate):** SQL'i gizlemeyen, parametreli sorguların açıkça kontrol edilebildiği, mülakat sırasında anlatımı yalın ve performansı yüksek bir mimari seçildi.
  - **Honeypot Stratejisi:** Botlara sahte 201 Created dönmek yerine (brief'in temel kuralı olan *"Başarı mesajı YALNIZCA kayıt gerçekten başarılıysa gösterilmeli"* ilkesine sadık kalınarak) açık HTTP 422 dönülmesi kararlaştırıldı. Honeypot alan adı otomatik doldurmalarla (browser autofill) karışmaması için `contactValidation` olarak adlandırıldı.
  - **Supabase RLS Kararı:** `service_requests` tablosuna `ALTER TABLE service_requests ENABLE ROW LEVEL SECURITY;` uygulandı (hiçbir policy tanımlanmadı). Böylece tablonun Supabase public şemasında anon anahtar üzerinden doğrudan sorgulanması veritabanı motoru düzeyinde engellendi; yalnızca güvenli sunucu tarafı JDBC bağlantısı erişebilir kılındı.
  - **Hata Yönetimi ve Bilgi Sızıntısı:** Supabase veya veritabanı arızalandığında iç hata, SQL sözdizimi veya stack trace sızdırılmadan genel Türkçe mesajla HTTP 500 dönülmesi; doğrulama ihlallerinde HTTP 422 ve `{"errors": {"alan": "mesaj"}}` dönülmesi kararlaştırıldı.

---

## 2. Aşama (a): İskelet Proje, DB Bağlantısı ve Flyway
- **Üretilen Kodlar:**
  - `pom.xml`: Spring Boot 3.3.4, Java 21, JDBC, Flyway (core + postgresql), Testcontainers.
  - `application.properties`: Ortam değişkenlerinden okuma, HikariCP timeout ayarları (`connection-timeout=5000`, `initialization-fail-timeout=-1`).
  - `V1__create_service_requests.sql`: UUID PK (`gen_random_uuid()`), CHECK kısıtları (`char_length`, `service` enum değerleri), RLS etkinleştirme.
  - `FlywayConfig.java`: `FlywayMigrationStrategy` ile try-catch hata yakalama; DB bağlantısı olmasa bile uygulamanın düşmemesi ve ayağa kalkabilmesi sağlandı.
  - `HealthController.java`: `GET /health` ile `SELECT 1` ve tablo varlık kontrolü yapılarak `UP`, `DEGRADED` veya `DOWN` durumları raporlandı.
- **Karşılaşılan Engel ve Çözüm:**
  - Geliştirme ortamında kurulu varsayılan sürümün Java 17 olduğu tespit edildi. Proje gereksinimine sadık kalmak adına ortama Microsoft OpenJDK 21 kuruldu ve derleme Java 21 LTS ile doğrulandı.
- **Doğrulama:**
  - DB kapalıyken uygulamanın çökmeden ayağa kalktığı ve `/health`'in 503 DOWN döndüğü doğrulandı.
  - Gerçek Supabase PostgreSQL bağlantısı ile Flyway'in tabloyu ve RLS'yi başarıyla oluşturduğu elle doğrulandı.

---

## 3. Aşama (b): API, Doğrulama, Güvenlik ve Testler
- **Üretilen Kodlar:**
  - `ServiceRequestDto.java`: Record constructor içinde `trim()` ve e-posta için `.toLowerCase()` normalizasyonu. Boşluktan oluşan değerlerin engellenmesi. RFC 5322 uyumlu ortak e-posta regexi: `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$`.
  - `ServiceRequestRepository.java`: Parametreli JDBC sorgusu (`INSERT INTO ... VALUES (?, ?, ?, ?) RETURNING id`). Loglara asla e-posta veya mesaj yazılmadı (PII güvenliği).
  - `ServiceRequestController.java`: `POST /api/requests` endpoint'i ve honeypot doğrulaması.
  - `GlobalExceptionHandler.java`: 422 alan bazlı hata haritalama, 400 bilinmeyen alan hatası (`fail-on-unknown-properties`), 500 güvenli hata yanıtı.
  - `RateLimitFilter.java`: `POST /api/requests` için IP başına dakikada 5 istek sınırı (`ConcurrentHashMap` tabanlı bellek içi sayaç, testler için `reset()` metodu).
  - `MaxPayloadSizeFilter.java`: 16KB istek boyutu sınırı (aşımda 413 JSON hatası).
  - `SecurityHeadersFilter.java`: CSP (`default-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`.
- **Karşılaşılan Engel ve Çözüm:**
  - Docker Desktop'ın en güncel Docker 29 sürümünde Testcontainers Java kütüphanesinin named-pipe üzerinden handshake yaparken `BadRequestException (Status 400)` vermesi sorunu görüldü. Brief'teki *"Sessizce H2'ye geçme"* kuralına tam uyularak, test ortamı için gerçek bir izole PostgreSQL Docker container'ı (`postgres:16-alpine`, port 5433) ayağa kaldırıldı ve Spring test profili (`application-test.properties`) üzerinden bağlandı.
- **Doğrulama:**
  - Toplam 26 adet birim ve entegrasyon testi yazıldı ve hepsi başarıyla geçti (`BUILD SUCCESS`).
  - 201 Created & DB satır varlığı, her geçersiz alan için 422, honeypot doluyken 422 & kayıt yazmama, mock repository ile 500 DB hata simülasyonu, 429 rate limit aşımı, 400 bilinmeyen alan ve 413 büyük gövde testleri otomatikleştirildi.

---

## 4. Aşama (c): Erişilebilir Landing Page ve Vanilla JS Form
- **Üretilen Kodlar:**
  - `src/main/resources/static/index.html`: Semantik HTML5 (`header`, `main`, `section`, `footer`), tek `h1`, WCAG uyumlu form etiketleri (`label for`), ipuçları ve hata bölgeleri (`aria-describedby`, `aria-live="polite"`, `role="status"`), ekran okuyucudan gizlenmiş honeypot alanı.
  - `src/main/resources/static/css/style.css`: Mobil öncelikli tasarım (375px'te yatay taşma sıfır), WCAG AA kontrastlı renkler, belirgin odak halkası (`:focus-visible`), `prefers-reduced-motion` desteği. Dış CDN veya font kullanılmadı.
  - `src/main/resources/static/js/app.js`: Saf Vanilla JS; XSS koruması için sadece `textContent` (sıfır `innerHTML`), sunucuyla birebir aynı kurallarla istemci doğrulaması, buton gönderme spinner'ı ve çift tıklama koruması, hata durumunda girilen verilerin silinmemesi ve ilk hatalı alana `focus()` verilmesi. Başarı mesajının **yalnızca HTTP 201** yanıtında gösterilmesi.
  - `StaticPageIntegrationTest.java`: Statik dosyaların doğru HTTP 200 ve CSP başlıklarıyla sunulduğunu doğrulayan 3 entegrasyon testi.
- **Doğrulama:**
  - 375px mobil görünüm, klavye navigasyonu (`Tab` tuşu), istemci ve sunucu doğrulama davranışları ve form durumları tarayıcıda elle test edildi.

---

## 5. Aşama (d): Dockerfile ve Deploy Hazırlığı
- **Üretilen Kodlar:**
  - `Dockerfile`: Multi-stage mimari. Aşama 1 `maven:3.9-eclipse-temurin-21-alpine` ile derleme; Aşama 2 `eclipse-temurin:21-jre-alpine` ile çalıştırma.
  - Non-root kullanıcı: `appuser:appgroup` oluşturulup yetkilendirildi.
  - Bellek ayarı: `-XX:MaxRAMPercentage=75` ile Render RAM sınırlarına optimizasyon.
  - Port ayarı: `server.port=${PORT:8080}` dinamik port desteği.
  - `.dockerignore`: `target/`, `.git/`, `.env` gibi gereksiz veya hassas dosyaların context dışı bırakılması.
- **Doğrulama:**
  - `docker build -t enteksis:latest .` komutu ile imaj yerelde derlendi.
  - Container ayağa kaldırılarak `whoami` ile `appuser` (non-root) olduğu ve `/health` endpoint'inden `{"status":"UP","database":"UP"}` döndüğü kanıtlandı.

---

## 6. Aşama (e): Admin Endpoint ve Dokümantasyon
- **Üretilen Kodlar:**
  - `AdminRequestController.java`: `GET /api/requests` endpoint'i.
    - `ADMIN_TOKEN` ortam değişkeni tanımlı değilse veya boşsa endpoint **tamamen devre dışı** (`401 Unauthorized`).
    - Sabit zamanlı güvenli token karşılaştırması (`MessageDigest.isEqual`) ile timing-attack önlemi.
    - `LIMIT 50 ORDER BY created_at DESC` sıralaması.
    - KVKK gereği e-posta maskeleme (`ah***@sirket.com`).
    - İstemci ve vekil sunucu önbelleklemesini önlemek için `Cache-Control: no-store` başlığı.
  - `AdminRequestControllerTest.java`: Eksik token (401), geçersiz token (401), geçerli token (200 + maskeli e-posta + no-store) entegrasyon testleri.
- **Doğrulama:**
  - Toplam 32 testin 32'si de hatasız geçti (`mvn test`).
