# Enteksis — İşletmeler İçin Görev Otomasyonu

İşletmelerin tekrarlayan manuel iş süreçlerini, periyodik raporlamalarını ve sistemler arası veri aktarımlarını otomatikleştiren teknoloji hizmeti için geliştirilmiş landing page ve güvenli talep toplama sistemi.

---

## Canlı Bağlantı ve Teslim Bilgileri
- **Canlı URL:** [https://enteksis.onrender.com](https://enteksis.onrender.com) 
- **Kaynak Kod Deposu:** https://github.com/HamzaKetenci/enteksis

> **NOT:** Render ücretsiz planında uygulama yaklaşık 15 dakika boyunca istek almadığında uyku moduna (spin-down) geçer. Bu nedenle ilk istekte açılış süresi 30–50 saniye sürebilir. Sonraki istekler anında yanıtlanır.

---

## Proje Özeti ve Teknik Yığın

Uygulama, hem ön yüzü hem de API katmanını tek bir Spring Boot container'ında barındıran monolitik bir mimaridir:

- **Çalışma Zamanı & Dil:** Java 21 
- **Framework:** Spring Boot 3.3.4 (Spring Web, Bean Validation)
- **Veritabanı Erişimi:** Spring JDBC (`JdbcTemplate`).
- **Veritabanı & Migration:** PostgreSQL (Supabase Session Pooler, SSL zorunlu) + Flyway Migration
- **Ön Yüz:**  HTML5, Modern CSS, Vanilla JavaScript (Hiçbir framework, dış CDN, harici font veya inline script kullanılmadı).
- **Konteynerleştirme:** Multi-stage Dockerfile (Non-root `appuser`, `-XX:MaxRAMPercentage=75`).

---

## Mimari Kararlar ve Ödünleşimler (Trade-offs)

1. **JPA Yerine Spring JDBC (`JdbcTemplate`):**
   - *Gerekçe:* Karmaşık ORM soyutlamaları, N+1 sorgu problemleri veya Hibernate önbellek yan etkileri olmadan, doğrudan şeffaf ve parametreli SQL sorguları yazıldı.
2. **Supabase Row Level Security (RLS):**
   - *Gerekçe:* Tablo Supabase üzerinde varsayılan `public` şemasında yer alır. Supabase'in istemci REST API'si üzerinden tablonun anon key ile doğrudan okunmasını veya değiştirilmesini engellemek için `ALTER TABLE service_requests ENABLE ROW LEVEL SECURITY;` uygulandı (hiçbir policy tanımlanmadı). Böylece verilere **yalnızca** sunucu tarafındaki yetkili JDBC bağlantısı erişebilir.
3. **Bot Koruması (Honeypot) ve 422 Kararı:**
   - *Gerekçe:* Formda tarayıcı otomatik doldurmasıyla (autofill) çakışmayacak `contactValidation` isimli gizli alan tanımlandı. Botlar bu alanı doldurduğunda sahte 201 Created dönülmedi; projenin *"Başarı mesajı YALNIZCA kayıt gerçekten başarılıysa gösterilmeli"* ilkesine sadık kalınarak HTTP 422 dönüldü ve veritabanına kayıt yazılmadı.
4. **Bellek İçi (In-Memory) Rate Limiting:**
   - *Gerekçe:* Ekstra Redis veya harici kütüphane bağımlılığı eklememek adına, `POST /api/requests` endpoint'ine IP başına dakikada 5 istek sınırı getiren yerel bir Servlet filtresi (`RateLimitFilter`) yazıldı. Çoklu instance senaryolarında sınırın paylaşılmaması bilinen bir ödünleşimdir.
5. **Kişisel Veri ve Hata Güvenliği:**
   - *Gerekçe:* SQL injection parametreli sorgularla engellendi. Uygulama loglarına e-posta veya mesaj içerikleri asla yazılmadı (KVKK/PII güvenliği). Veritabanı kesintilerinde veya beklenmeyen hatalarda istemciye SQL hatası, tablo adı veya stack trace sızdırılmayıp genel Türkçe hata mesajıyla HTTP 500 dönüldü.

---

## Ortam Değişkenleri

Uygulama çalışmak için aşağıdaki ortam değişkenlerine ihtiyaç duyar. Gerçek parolalar ve sırlar asla repoya eklenmez; yerel geliştirme için `.env.example` referans alınarak `.env` oluşturulur.

| Değişken | Açıklama | Örnek Değer |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC bağlantı adresi (SSL gereklidir) | `jdbc:postgresql://db.xxxx.supabase.co:5432/postgres?sslmode=require` |
| `DB_USER` | Veritabanı kullanıcı adı | `postgres.xxxx` |
| `DB_PASSWORD` | Veritabanı şifresi | `superSecretDbPass123` |
| `PORT` | Uygulamanın dinleyeceği port (Varsayılan: 8080) | `8080` |
| `ADMIN_TOKEN` | *(Opsiyonel)* Yönetici endpoint'i erişim anahtarı | `super-secret-admin-token-123` |

> **Önemli:** `ADMIN_TOKEN` ortam değişkeni tanımlanmamışsa veya boşsa, `/api/requests` admin listeleme endpoint'i güvenlik gereği **tamamen devre dışı** kalır (`401 Unauthorized`).

---

## Yerelde Kurulum ve Çalıştırma

### Gereksinimler
- Java 21 JDK
- Apache Maven 3.9+
- Docker Desktop (Testler ve imaj build için)

### Adım 1: Depoyu Klonlayın ve Dizine Geçin
```bash
git clone https://github.com/HamzaKetenci/enteksis
cd enteksis
```

### Adım 2: Ortam Değişkenlerini Tanımlayın
`.env.example` dosyasını `.env` olarak kopyalayın ve Supabase veritabanı bilgilerinizi girin:
```bash
cp .env.example .env
```

PowerShell üzerinden `.env` dosyasını oturuma yükleyin:
```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim(), "Process")
    }
}
```

### Adım 3: Uygulamayı Başlatın
```bash
mvn spring-boot:run
```
Uygulama başladığında tarayıcınızdan `http://localhost:8080` adresine gidebilirsiniz.

---

## Testleri Çalıştırma

Tüm birim ve entegrasyon testlerini koşturmak için:
```bash
mvn test
```
*Testler production / Supabase veritabanına asla bağlanmaz. Gerçek izole bir PostgreSQL Docker ortamı üzerinde tüm senaryolar (201 satır kaydı, 422 doğrulama kuralları, 429 rate limit, 413 boyut aşımı, 400 bilinmeyen alan, 500 DB hata simülasyonu, A11y statik sayfalar ve 401/200 Admin yetkilendirmesi) otomatik doğrulanır.*

---

## Docker ile Çalıştırma

Multi-stage Docker imajını yerelde derlemek ve non-root kullanıcıyla ayağa kaldırmak için:

```bash
# İmajı oluştur
docker build -t enteksis:latest .

# Container'ı çalıştır
docker run -d -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://HOST:5432/postgres?sslmode=require" \
  -e DB_USER="postgres.xxxx" \
  -e DB_PASSWORD="xxxx" \
  -e ADMIN_TOKEN="admin123" \
  enteksis:latest
```

---

## API Uç Noktaları

### 1. `POST /api/requests` (Talep Oluşturma)
- **Gövde:**
  ```json
  {
    "name": "Ali Yılmaz",
    "email": "ali@sirket.com",
    "service": "gorev-otomasyonu",
    "message": "Süreçlerimizi otomatikleştirmek istiyoruz.",
    "contactValidation": ""
  }
  ```
- **Yanıtlar:**
  - `201 Created`: `{"message": "Talebiniz başarıyla alındı."}`
  - `422 Unprocessable Entity`: `{"errors": {"alan": "Türkçe hata mesajı"}}`
  - `429 Too Many Requests`: `{"error": "Çok fazla istek gönderildi. Lütfen bir dakika bekleyiniz."}`
  - `413 Payload Too Large`: Gövde > 16KB ise.
  - `400 Bad Request`: JSON bozuksa veya tanınmayan alanlar varsa.
  - `500 Internal Server Error`: `{"error": "Talebiniz kaydedilirken bir hata oluştu. Lütfen daha sonra tekrar deneyiniz."}`

### 2. `GET /health` (Sistem Sağlığı)
- **Yanıtlar:**
  - `200 OK`: `{"status": "UP", "database": "UP"}`
  - `503 Service Unavailable`: Veritabanına ulaşılamıyorsa `{"status": "UP", "database": "DOWN"}`

### 3. `GET /api/requests` (Admin Listeleme - Opsiyonel)
- `Authorization: Bearer <ADMIN_TOKEN>` gerektirir.
- `Cache-Control: no-store` döner. E-postalar maskelenir (`al***@sirket.com`). Son 50 kaydı getirir.

---

## Bilinen Eksikler ve Sınırlar

1. **Ön Yüz Uçtan Uca (E2E) Test Otomasyonu:**
   - Ön yüzün yalnızca HTTP 201'de yeşil başarı mesajı göstermesi, çift tıklama koruması ve erişilebilir odak akışı için Selenium/Cypress gibi bir E2E test otomasyon paketi dahil edilmemiştir (Spring MockMvc düzeyinde statik asset testleri yapılmıştır).
2. **Dağıtık Ortamlarda Rate Limit:**
   - Rate limit filtresi JVM belleğinde (`ConcurrentHashMap`) tutulmaktadır. Birden fazla sunucu/instance devreye girdiğinde sayaçlar bağımsız işleyecektir. İleri aşamada Redis tabanlı merkezi bir token-bucket yapısına geçilmelidir.
3. **Uygulama Açılışında Veritabanı Kesintisi:**
   - Uygulama başlarken veritabanı kapalıysa `FlywayConfig` hata yakalayarak uygulamanın çökmesini engeller ve `/health` endpoint'inde `database: DOWN` raporlar. Ancak veritabanı daha sonra açılsa bile Flyway migration otomatik tetiklenmez; uygulamanın yeniden başlatılması (restart) gerekir.

---

## Kullanılan Şablon ve Dış Kaynaklar
- **Kullanılan Şablon / Harici UI Kiti:** **YOK**.
- Tüm HTML semantiği, CSS stilleri ve Vanilla JavaScript kodları sıfırdan ve gereksinimlere özel olarak yazılmıştır.
