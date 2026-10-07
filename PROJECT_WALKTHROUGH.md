# Enteksis — Kapsamlı Proje ve Mimari Rehberi (PROJECT_WALKTHROUGH.md)

Bu belge, **Enteksis Görev Otomasyonu** projesinin tüm dosya ve dizin yapısını, ön yüz ile arka yüz arasındaki haberleşme akışını, bileşenlerin sorumluluklarını ve geliştirme sürecini baştan sona detaylandırmaktadır. Mülakat hazırlığı ve teknik savunma için referans dokümandır.

---

## 1. Tam Dizin ve Dosya Yapısı

```
enteksis/
├── .env.example                               # Ortam değişkenleri şablonu (DB_URL, DB_USER, vb.)
├── .gitignore                                 # Git takip dışı dosyalar (target/, .env, IDE dosyaları)
├── .dockerignore                              # Docker derlemesine dahil edilmeyecek dosyalar
├── Dockerfile                                 # Multi-stage üretim Docker imajı (Maven + Alpine JRE 21)
├── pom.xml                                    # Maven bağımlılıkları ve Java 21 konfigürasyonu
├── README.md                                  # Genel proje tanıtımı, kurulum ve mimari özeti
├── AI_LOG.md                                  # AI ile geliştirme ve doğrulama kararları günlüğü
├── PROJECT_WALKTHROUGH.md                     # [BU DOSYA] Detaylı kod ve mimari rehberi
│
└── src/
    ├── main/
    │   ├── java/com/enteksis/
    │   │   ├── EnteksisApplication.java       # Spring Boot ana giriş noktası (@SpringBootApplication)
    │   │   │
    │   │   ├── admin/                         # [OPSİYONEL] Yönetici Katmanı
    │   │   │   └── AdminRequestController.java# GET /api/requests (ADMIN_TOKEN korumalı, maskeli listeleme)
    │   │   │
    │   │   ├── config/                        # Güvenlik ve Altyapı Filtreleri
    │   │   │   ├── FlywayConfig.java          # DB kesintisinde uygulamanın çökmesini önleyen strateji
    │   │   │   ├── MaxPayloadSizeFilter.java  # 16KB istek boyutu sınırı (413 Payload Too Large)
    │   │   │   └── SecurityHeadersFilter.java # CSP, X-Frame-Options, X-Content-Type-Options filtreleri
    │   │   │
    │   │   ├── health/                        # Sağlık Denetimi Katmanı
    │   │   │   └── HealthController.java      # GET /health (SELECT 1 ve tablo varlık denetimi)
    │   │   │
    │   │   ├── ratelimit/                     # Hız Sınırlama (Rate Limiting)
    │   │   │   └── RateLimitFilter.java       # IP başına dakikada 5 istek sınırı (429 Too Many Requests)
    │   │   │
    │   │   └── request/                       # Ana İş Mantığı (Talep Toplama)
    │   │       ├── GlobalExceptionHandler.java# 400, 413, 422, 500 merkezi hata yakalayıcı
    │   │       ├── ServiceRequestController.java # POST /api/requests endpoint'i
    │   │       ├── ServiceRequestDto.java     # Normalizasyon, RFC 5322 regex ve Bean Validation
    │   │       └── ServiceRequestRepository.java # JdbcTemplate ile parametreli INSERT sorgusu
    │   │
    │   └── resources/
    │       ├── application.properties         # Port, HikariCP havuzu, DB değişkenleri, Jackson ayarları
    │       │
    │       ├── db/migration/                  # Veritabanı Şema Yönetimi (Flyway)
    │       │   └── V1__create_service_requests.sql # service_requests tablosu, CHECK kısıtları ve RLS
    │       │
    │       └── static/                        # [ÖN YÜZ] Saf HTML, CSS ve Vanilla JS
    │           ├── index.html                 # Semantik, WCAG AA erişilebilir landing page
    │           ├── css/
    │           │   └── style.css              # Mobil öncelikli stil sayfası, görünür odak halkası
    │           └── js/
    │               └── app.js                 # Vanilla JS form yönetimi, A11y odak, XSS koruması
    │
    └── test/
        ├── java/com/enteksis/                 # Otomatik Testler (32 Test)
        │   ├── admin/
        │   │   └── AdminRequestControllerTest.java # 401 ve 200 Admin yetki testleri
        │   │
        │   └── request/
        │       ├── ServiceRequestDatabaseFailureTest.java # DB çökmesi simülasyonu (500 testi)
        │       ├── ServiceRequestDtoValidationTest.java   # 18 adet DTO ve Bean Validation birim testi
        │       ├── ServiceRequestIntegrationTest.java     # 201 satır kaydı, 422, 429, 413, 400 testleri
        │       └── StaticPageIntegrationTest.java         # HTML, CSS, JS 200 OK ve CSP başlık testleri
        │
        └── resources/
            └── application-test.properties    # İzole test DB bağlantısı (Production'a asla dokunmaz)
```

---

## 2. Ön Yüz ve Arka Yüz Nasıl Haberleşiyor?

Uygulama **monolitik tek container** mimarisindedir. Ön yüz (HTML/CSS/JS) doğrudan Spring Boot'un statik dosya sunucusu (`src/main/resources/static`) tarafından kök dizinden (`/`) servis edilir. Dışarıdan ayrı bir Node.js veya Nginx sunucusu yoktur.

### Uçtan Uca İstek ve Veri Akışı Şeması:

```
[Kullanıcı Tarayıcısı]
       │
       ▼ (1. İstemci Kontrolü)
[static/js/app.js]
  - Alanlar dolu mu?
  - E-posta regex'e uyuyor mu?
  - Karakter limitleri (İsim: 2-80, Mesaj: 10-1000) uygun mu?
  - HATA VARSA: Sunucuya gitmez, ilgili alana focus() verir ve mesaj yazar.
       │
       ▼ (2. HTTP POST /api/requests ile JSON Gönderimi)
[Spring Boot Filtre Zinciri]
  1. SecurityHeadersFilter: Yanıta CSP, X-Frame-Options ekler.
  2. MaxPayloadSizeFilter: Gövde > 16KB ise 413 döner, belleği korur.
  3. RateLimitFilter: Bu IP son 1 dakikada 5'ten fazla istek attı mı? Evetse 429 döner.
       │
       ▼ (3. Deserialization & Doğrulama)
[ServiceRequestDto (Record)]
  - Constructor: name.trim(), email.trim().toLowerCase(), message.trim()
  - @Pattern(RFC 5322 regex), @Size, @NotBlank kontrolleri çalışır.
  - Tanınmayan alan varsa Jackson 400 Bad Request döner.
       │
       ▼ (4. Controller İş Mantığı)
[ServiceRequestController]
  - contactValidation (Honeypot) dolu mu? Doluysa bot -> 422 döner (DB'ye yazmaz).
  - Boşsa -> Repository çağrılır.
       │
       ▼ (5. Veritabanı Katmanı)
[ServiceRequestRepository]
  - JdbcTemplate.queryForObject(
      "INSERT INTO service_requests (...) VALUES (?, ?, ?, ?) RETURNING id"
    )
  - Parametreli sorgu -> SQL Injection imkansızdır.
  - Loglara PII (e-posta/mesaj) yazılmaz, sadece üretilen UUID loglanır.
       │
       ▼ (6. Veritabanı Motoru)
[Supabase PostgreSQL]
  - V1 migration ile açılan RLS devrededir (dışarıdan anon key erişemez).
  - CHECK kısıtları doğrulanır ve satır diske kalıcı yazılır.
       │
       ▼ (7. Yanıt Dönüşü)
[HTTP 201 Created] -> {"message": "Talebiniz başarıyla alındı."}
       │
       ▼ (8. Ön Yüzde Başarı Gösterimi)
[static/js/app.js]
  - status === 201 doğrulanır.
  - Form alanları sıfırlanır (form.reset()).
  - Yeşil başarı kutusu görüntülenir.
```

---

## 3. Klasör Klasör Sorumluluklar

### A. `src/main/resources/static/` (Ön Yüz Katmanı)
- **`index.html`:** Sayfanın iskeletidir. Semantik HTML5 etiketleriyle (`header`, `main`, `section`, `footer`) yazılmıştır. Tek bir `h1` bulunur. Her form alanının `<label for="...">` etiketi vardır. Ekran okuyucuların hataları dinamik okuyabilmesi için `aria-describedby`, `aria-invalid` ve `aria-live="polite"` kullanılmıştır. Botları yakalayan gizli `contactValidation` alanı buradadır.
- **`style.css`:** Mobil öncelikli (Mobile First) tasarımdır. 375px genişliğindeki telefon ekranlarında yatay kaydırma çubuğu kesinlikle çıkmaz. Renk kontrastları WCAG AA standardındadır (>4.5:1). Klavye kullanıcıları için belirgin mavi odak halkası (`:focus-visible`) ve animasyon hassasiyeti olanlar için `prefers-reduced-motion` barındırır.
- **`app.js`:** Sayfanın beynidir. Sunucuya istek atmadan önce formu istemci tarafında denetler. Güvenlik gereği kesinlikle `innerHTML` kullanmaz, tüm metinleri `textContent` ile basarak XSS açıklarını kapatır. Çift gönderimi önlemek için butonu pasifleştirip spinner döndürür. **YALNIZCA 201 Created geldiğinde başarı mesajı gösterir.**

### B. `src/main/java/com/enteksis/config/` ve `ratelimit/` (Güvenlik Katmanı)
- **`SecurityHeadersFilter.java`:** HTTP başlıklarına Content Security Policy (`default-src 'self'`), `X-Frame-Options: DENY` (Clickjacking koruması) ve `X-Content-Type-Options: nosniff` ekler.
- **`MaxPayloadSizeFilter.java`:** Kötü niyetli kullanıcıların sunucuya devasa JSON yükleyerek bellek şişirmesini (DoS) engeller. 16 KB sınırını aşan istekleri doğrudan 413 ile keser.
- **`RateLimitFilter.java`:** `ConcurrentHashMap` kullanarak istemci IP'si başına dakikada 5 istek sınırı koyar. Form spam'ini engeller. Aşımda 429 döner.
- **`FlywayConfig.java`:** Veritabanı kesintisinde uygulamanın çökmesini engelleyen `FlywayMigrationStrategy` bean'idir. Hata durumunda log atar ama uygulamanın başlamasına izin verir.

### C. `src/main/java/com/enteksis/request/` (Ana API Katmanı)
- **`ServiceRequestDto.java`:** Java 21 Record yapısıdır. Veri taşır. Constructor'ında veriyi sanitize eder (`trim` ve `toLowerCase`). İsim (2-80), mesaj (10-1000) ve ortak RFC 5322 e-posta regex kurallarını Bean Validation ile denetler.
- **`ServiceRequestController.java`:** `POST /api/requests` isteğini karşılar. Honeypot (`contactValidation`) doluysa botu 422 ile reddeder. Temizse servisi çağırıp 201 Created döner.
- **`ServiceRequestRepository.java`:** `JdbcTemplate` ile doğrudan PostgreSQL'e bağlanır. ORM (JPA) soyutlaması olmadan şeffaf, parametreli SQL çalıştırır. Loglara hassas veri yazmaz.
- **`GlobalExceptionHandler.java`:** `@RestControllerAdvice` ile tüm hata tiplerini yakalar:
  - Validasyon hatası &rarr; `422` (alan bazlı Türkçe harita)
  - Tanınmayan alan &rarr; `400`
  - Veritabanı / SQL çökmesi &rarr; `500` (iç detayları gizleyerek genel mesaj döner)

### D. `src/main/java/com/enteksis/health/` (Sağlık Katmanı)
- **`HealthController.java` (`GET /health`):** Veritabanına fiziksel ping (`SELECT 1`) atar ve `information_schema.tables` sorgusuyla `service_requests` tablosunun varlığını doğrular. Her şey tamsa 200 `{"status":"UP","database":"UP"}`, tablo yoksa `DEGRADED`, bağlantı kopuksa 503 `DOWN` raporlar.

### E. `src/main/java/com/enteksis/admin/` (Yönetici Katmanı - Opsiyonel)
- **`AdminRequestController.java` (`GET /api/requests`):** `ADMIN_TOKEN` ortam değişkeni tanımlıysa çalışır, yoksa tamamen devre dışıdır (401). Timing attack'e karşı `MessageDigest.isEqual` kullanır. Son 50 kaydı getirir, KVKK gereği e-postaları maskeler (`al***@sirket.com`) ve `Cache-Control: no-store` döner.

### F. `src/main/resources/db/migration/` (Veritabanı Şeması)
- **`V1__create_service_requests.sql`:** Tabloyu oluşturur, UUID PK tanımlar, CHECK kısıtları koyar. En önemlisi `ALTER TABLE service_requests ENABLE ROW LEVEL SECURITY;` çalıştırarak Supabase anon key'in tabloyu okumasını veritabanı motoru seviyesinde engeller.

---

## 4. Projeyi Nasıl Geliştirdik? (Aşama Aşama Tarihçe)

Proje rastgele değil, 5 disiplinli aşamada test odaklı (TDD prensipleriyle) geliştirildi:

1. **Planlama Aşaması:** Kod yazmadan önce `architecture-plan.md` hazırlandı, dosya yapısı, API sözleşmeleri, güvenlik ve test eşleşmeleri kararlaştırıldı.
2. **Aşama (a) — İskelet & DB:** Maven pom.xml (Java 21), application.properties, HikariCP timeout ayarları, Flyway V1 migration ve `/health` controller'ı yazıldı. DB kapalıyken çökmeden ayakta kalabildiği test edildi.
3. **Aşama (b) — API, Doğrulama & Güvenlik:** DTO, Repository, Controller, ExceptionHandler, RateLimit, Payload filtresi ve Güvenlik başlıkları yazıldı. İzole Docker PostgreSQL container'ı üzerinde 26 test koşturuldu.
4. **Aşama (c) — UI & Form:** Saf HTML5, CSS ve Vanilla JS yazıldı. Mobil görünüm, WCAG AA kontrast, odak yönetimi, çift tıklama engeli ve yalnızca 201'de başarı gösterme kuralı uygulandı.
5. **Aşama (d) — Docker & Deploy:** Maven build + Alpine JRE 21 multi-stage Dockerfile yazıldı. Non-root `appuser` kullanıcısı ve `-XX:MaxRAMPercentage=75` ile Render ortamına uyarlandı.
6. **Aşama (e) — Admin & Dokümantasyon:** Admin endpoint'i, testleri, `AI_LOG.md` ve `README.md` hazırlandı. Toplam test sayısı 32'ye ulaştı ve hepsi geçti.

---

## 5. Mülakatta Seni Öne Çıkaracak 5 Kritik Savunma Noktası

1. **"Neden JPA değil de JdbcTemplate?"** &rarr; *"Küçük ve net bir CRUD işleminde ORM'in getirdiği N+1, önbellek karmaşası ve soyutlama maliyeti gereksizdir. `JdbcTemplate` ile doğrudan parametreli SQL yazarak performansı ve şeffaflığı sağladım."*
2. **"Supabase'de RLS'yi neden açtın?"** &rarr; *"Tablo public şemadadır. Supabase varsayılan olarak anon key ile REST API açar. RLS'yi açıp policy tanımlamayarak dışarıdan tabloya veri çekilmesini DB motoru düzeyinde engelledim; sadece sunucumun JDBC bağlantısı erişebilir."*
3. **"Neden Honeypot'a sahte 201 dönmedin?"** &rarr; *"Brief'te başarı mesajının YALNIZCA kayıt gerçekten başarılı olduğunda gösterilmesi şart koşuldu. Botu aldatmak adına sahte 201 dönmek bu iş kuralını çiğnerdi. Bu yüzden açıkça 422 döndüm ve DB'ye yazmadım."*
4. **"Frontend'de XSS'i nasıl önledin?"** &rarr; *"DOM manipülasyonunda kesinlikle `innerHTML` kullanmadım, tüm verileri `textContent` ile güvenli şekilde bastım. Ayrıca CSP (`default-src 'self'`) ile dış kaynaklı scriptleri engelledim."*
5. **"Rate limit filtresinin bilinen eksiği nedir?"** &rarr; *"Bellek içi (`ConcurrentHashMap`) tutulduğu için dağıtık çoklu sunucu (multi-instance) mimarilerinde Redis tabanlı bir rate limiter'a ihtiyaç duyar. Bunu README'de bilinen eksik olarak dürüstçe açıkladım."*
