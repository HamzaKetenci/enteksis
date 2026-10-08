# AI_LOG.md — AI Geliştirme ve Doğrulama Günlüğü

Bu projeyi Antigravity ile geliştirdim. Claude'u ise danışman ve gözden geçirici olarak kullandım. Bu dosyada hangi kararları verdiğimi, yapay zekayı nasıl yönlendirdiğimi ve sonucun çalıştığını nasıl kontrol ettiğimi anlatıyorum. Yapmadığım hiçbir kontrolü yapılmış gibi yazmadım.

---

## Süre

Çalışma penceresini 7 Ekim 2026'da, yaklaşık 18:40'ta başlattım. Teslim saatim 8 Ekim 2026 10:45, gerçek aktif çalışma sürem ise yaklaşık 3.5 - 4 saat oldu. Pencereyi planladığımdan daha erken başlattığım için ilk kısmı hazırlığa ve karar vermeye ayırdım. Bu kısımda hangi teknolojileri kullanacağıma ve projeyi nerede yayınlayacağıma karar verdim.

---

## Kullandığım araçlar ve görev dağılımı

Kararları ben verdim ve her aşamayı ben onayladım. Kodu Antigravity yazdı. Claude ile ise şu konularda çalıştım: görevi anlamak, teknoloji ve veritabanı seçimlerini değerlendirmek, Antigravity'ye vereceğim ana prompt'un taslağını hazırlamak ve Antigravity'nin çıkardığı mimari planı gözden geçirmek. Ana prompt'un ilk taslağını Claude hazırladı, ben de onu kullandım. Prompt'a Flyway try/catch yönetimi, Honeypot 422 kararı, Supabase RLS etkinleştirme ve 16KB gövde sınırı gibi kendi mimari tercihlerimi ekleyerek Antigravity'ye aktardım.

Projede Supabase'i yalnızca PostgreSQL veritabanı olarak, uygulamayı yayınlamak için ise Render'ı kullandım. Kaynak kod GitHub'da duruyor. Uygulamayı paketlemek ve yerelde denemek için Docker'dan yararlandım.

---

## Verdiğim kararlar

Backend için Spring Boot 3, Java 21 ve JdbcTemplate kullanmaya karar verdim. Spring Boot'ta diğer seçeneklerden daha rahat çalışıyorum. JdbcTemplate ile SQL sorguları kodda açıkça göründüğü için kodu anlatması ve değiştirmesi de benim için kolay oluyor.

Veritabanı olarak Supabase üzerindeki PostgreSQL'i seçtim. Kendi bilgisayarımdaki bir veritabanına canlı site erişemeyeceği için internette çalışan bir veritabanına ihtiyacım vardı. Supabase'i yalnızca veritabanı olarak kullandım. Tarayıcı veritabanına doğrudan bağlanmıyor, tüm kayıtlar sunucu üzerinden yazılıyor. Böylece doğrulama kuralları sunucuda uygulanıyor ve başarı mesajı yalnızca kayıt gerçekten oluştuğunda gösteriliyor.

Sayfayı ve API'yi tek bir uygulamada birleştirdim. Arayüzü herhangi bir framework kullanmadan, düz HTML, CSS ve JavaScript ile yazdırdım.

---

## Mimari planı inceleyip düzelttirdiklerim

Antigravity'den önce kod yazmamasını, yalnızca bir mimari plan hazırlamasını istedim. Planın genel yapısını, yani aşamaları ve gereksinimlerle testlerin eşlenmesini yerinde buldum. Bunu Claude ile birlikte inceledim ve şu sorunları bulup düzelttirdim:

- Plandaki `application.properties` örneğinde değerlerin yanına `# yorum` yazılmıştı. Bu dosya biçiminde satır sonundaki yorum, yorum sayılmaz ve değerin bir parçası olur. Bu yüzden sayı okunamayacaktı. Yorumların ayrı satıra taşınmasını istedim.
- İstek gövdesinin boyutunu sınırlamak için önerilen Tomcat ayarları JSON gövdesini sınırlamıyordu. Bunun yerine 16 KB'ı aşan istekleri 413 koduyla reddeden küçük bir filtre ve bunun için bir test yazdırdım.
- Tablo, Supabase'in `public` şemasında oluşturuluyordu. Satır düzeyinde güvenlik (RLS) kapalı olursa Supabase'in kendi REST API'si üzerinden anon anahtarla tabloya erişilebilirdi. Bu yüzden migration dosyasına RLS'yi etkinleştiren satırın eklenmesini istedim.
- Plan, honeypot alanı doluysa bota sahte bir 201 yanıtı dönüyordu. Görev, başarı mesajının yalnızca kayıt gerçekten oluştuğunda gösterilmesini istediği için bunu kabul etmedim. Honeypot doluysa kayıt yazılmıyor ve genel bir 422 hatası dönüyor. Alanın adını, tarayıcıların otomatik doldurmasıyla karışmaması için `website` yerine `contactValidation` yaptırdım.
- Hız sınırını yalnızca `POST /api/requests` isteğine uygulattırdım. Aksi hâlde sayfanın CSS ve JavaScript dosyaları da sayılacak ve ziyaretçi sayfayı açarken engellenebilecekti. Sayaç, testler birbirini etkilemesin diye sıfırlanabilir olacak şekilde yazıldı.
- Metin alanlarının boşluklarının temizlenmesi ve e-postanın küçük harfe çevrilmesi, doğrulamadan önce yapılacak şekilde düzeltildi. Böylece yalnızca boşluklardan oluşan bir isim ya da mesaj reddediliyor ve bunun için ayrı bir test var.
- Bilinmeyen alanlar için özel bir kod yerine Jackson'ın hazır ayarını kullandırdım. Fazladan bir alan gönderilirse Türkçe bir 400 hatası dönüyor.
- "Veritabanı erişilemezse 500 dön" testini, konteyneri durdurarak değil, repository'yi taklit edip `DataAccessException` fırlatarak yazdırdım. Bu yöntem daha basit ve daha kararlı.
- Yönetici endpoint'i için `ADMIN_TOKEN` tanımlı değilse endpoint'in tamamen kapalı olmasını, token karşılaştırmasının `MessageDigest.isEqual` ile yapılmasını ve yanıtta `Cache-Control: no-store` başlığının bulunmasını istedim.

---

## Plandan ayrıldığım noktalar

Testcontainers, kullandığım Docker Desktop 29 sürümünde çalışmadı. Antigravity, Testcontainers'ın Docker'a bağlanırken `BadRequestException (400)` hatası verdiğini bildirdi. Testleri H2 gibi sahte bir veritabanına taşımak yerine bunu çözmeyi tercih ettim ve gerçek bir PostgreSQL konteynerini (`postgres:16-alpine`, 5433 portu) elle başlatıp testleri ona bağladım. Dolayısıyla testlerin çalışması için bu konteynerin ayakta olması gerekiyor. Bunun komutunu README'ye yazdım. `pom.xml` içinde Testcontainers bağımlılığını silmeyip bıraktım; çünkü standart Testcontainers bağımlılık yapısını korumak ve Docker API uyumluluğu sağlandığında doğrudan Testcontainers moduna dönülebilmesini istedim.

Bilgisayarımda varsayılan Java sürümü 17'ydi. Projenin gereksinimi Java 21 olduğu için ortama Microsoft OpenJDK 21 kurup derlemeyi Java 21 ile gerçekleştirdim.

---

## Yaptığım doğrulamalar

Aşağıdaki tabloyu yalnızca gerçekten yaptığım kontrollerle doldurdum.

| Kontrol | Nasıl yaptım | Sonuç |
|---|---|---|
| Otomatik testler | `mvn test` komutunu kendim çalıştırdım. Antigravity 32 testin geçtiğini bildirmişti. | Başarılı (32 testin 32'si de hatasız geçti) |
| Veritabanı kapalıyken uygulama | Sahte bir `DB_URL` ile başlatıp `/health` adresine `curl` attım. Uygulamanın düşmemesini ve 503 ile `DOWN` dönmesini bekledim. | Başarılı (Uygulama düşmedi, HTTP 503 DOWN döndü) |
| Tablo ve RLS | Supabase panelinde tablonun oluştuğunu ve RLS'nin açık olduğunu gördüm. Anon anahtarla sorgulayınca satır dönmemesini bekledim. | Başarılı (RLS etkin, anon key ile 0 satır döndü) |
| Kayıt oluşması | Formu doldurup gönderdim ve satırı Supabase'deki Table Editor'da gördüm. | Başarılı (UUID PK ile satır veritabanına yazıldı) |
| Veritabanı hatasında başarı mesajı | Yanlış parolayla uygulamayı başlatıp formu gönderdim. Başarı mesajının çıkmamasını bekledim. | Başarılı (Başarı mesajı çıkmadı, kırmızı hata görüntülendi) |
| Sunucu tarafı doğrulama | `curl` ile geçersiz, eksik ve fazladan alanlı istekler gönderdim. | Başarılı (Doğrulama hatalarında 422, fazla alanda 400 alındı) |
| Büyük gövde ve honeypot | 16 KB'tan büyük bir istek ve honeypot'u dolu bir istek gönderdim. 413 ve 422 beklerken tabloya yeni satır eklenmemesini bekledim. | Başarılı (413 ve 422 alındı, veritabanı temiz kaldı) |
| Hız sınırı (canlıda) | İki farklı ağdan arka arkaya 6 istek gönderip sınırın IP başına çalıştığını kontrol ettim. | Başarılı (6. istekte 429 Too Many Requests alındı) |
| Statik dosyalar ve `/health` | Sayfayı birkaç kez yenileyip `/health`'i çağırdım. Hız sınırına takılmamalarını bekledim. | Başarılı (Statik dosyalar ve health etkilenmedi) |
| Mobil görünüm | Tarayıcı geliştirici araçlarında 375 px genişlikte baktım. Yatay kaydırma olmamasını bekledim. | Başarılı (375px ekranda yatay taşma sıfır) |
| Klavye kullanımı | Formu yalnızca Tab ve Enter ile doldurdum. Odak sırasına ve hata durumunda ilk hatalı alana odaklanmasına baktım. | Başarılı (Görünür odak halkası ve otomatik focus çalıştı) |
| Ekran okuyucu | macOS VoiceOver / NVDA ile kontroller gerçekleştirdim. Form etiketlerinin ve bildirimlerin okunduğunu gördüm. | Başarılı (Label ilişkileri ve aria-live okundu) |
| Docker imajı | İmajı derleyip konteynerin kök olmayan bir kullanıcıyla çalıştığını ve `/health`'in yanıt verdiğini kontrol ettim. | Başarılı (`whoami` ile `appuser` doğrulandı, /health 200 OK döndü) |
| Canlı adres | Aynı form denemelerini canlı adreste tekrarladım ve kaydın Supabase'e düştüğünü gördüm. | Başarılı (Form canlıda 201 döndü ve Supabase'e kaydetti) |
| Gizli bilgiler | Depoda ve git geçmişinde parola ya da bağlantı bilgisi olmadığını kontrol ettim. | Başarılı (Hiçbir şifre veya secret repoda yok) |

---

## Yapay zekanın hataları ve benim düzeltmelerim

Mimari plandaki hataları yukarıda anlattım. Kod aşamasında karşılaştığım hatalar şunlar:

- **Spinner Görünürlüğü Hatası:** CSS'teki `.spinner { display: inline-block; }` kuralı HTML'deki `hidden` özniteliğini ezdiği için sayfa ilk açıldığında buton üzerinde çark sürekli dönüyordu. CSS'e `[hidden] { display: none !important; }` ekleterek düzelttim.
- **Java Sürümü Uyumsuzluğu:** İlk aşamada yerel ortamdaki varsayılan Java 17 ile derleme denenirken Maven release hatası alındı. Ortama Microsoft OpenJDK 21 kurarak pom.xml'i Java 21'e güncellettim.
- **Statik Dosya Testinde Karakter Uyuşmazlığı:** `index.html` için yazılan entegrasyon testinde Türkçe karakterlerin Unicode uyuşmazlığı nedeniyle test patlıyordu. Test kontrolünü ASCII güvenli karakter kümesiyle eşleştirip düzelttim.

---

## Bilinen eksikler

Hız sınırı sunucunun belleğinde tutuluyor. Uygulama birden fazla örnekte çalışırsa bu sınır yetersiz kalır.

Ön yüzde, başarı mesajının yalnızca 201 yanıtında gösterilmesi için otomatik bir test yok. Bunu manuel test adımlarıyla kontrol ettim ve adımları README'ye yazdım.

Veritabanı uygulama açılırken kapalıysa tablo kendiliğinden oluşmuyor. Bu durumda veritabanı ayağa kalktıktan sonra uygulamayı yeniden başlatmak gerekiyor.

Testler elle başlatılan bir PostgreSQL konteynerine bağlı, çünkü Testcontainers kullandığım Docker sürümünde çalışmadı.

E-posta doğrulaması basit bir düzenli ifadeyle yapılıyor ve e-posta standardının tamamını kapsamıyor. İstemci ve sunucu aynı kuralı kullanıyor.

Render'ın ücretsiz planında uygulama bir süre kullanılmazsa uyuyor ve ilk istek yavaş yanıt verebiliyor.

---

## Kodu anlama

Teslimden önce kodu baştan sona okudum. Anlamadığım yerleri Antigravity'ye ve Claude'a açıklattım. Saf Vanilla JS form mantığını (`app.js`), Spring Boot `@RestControllerAdvice` ile yazılan `GlobalExceptionHandler` sınıfını ve `RateLimitFilter` akışını detaylıca inceledim. Ayrıca footer alanındaki yapay duran sloganları kaldırıp kendi imzamı ekledim (`Geliştiren: Hamza Ketenci`).
