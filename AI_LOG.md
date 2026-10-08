# AI_LOG

Bu projeyi Antigravity ile geliştirdim, Claude'u ise danışman ve gözden geçirici olarak kullandım. Aşağıda nasıl çalıştığımı, yapay zekayı nasıl yönlendirdiğimi, karşılaştığım sorunları ve sonucu nasıl kontrol ettiğimi anlatıyorum.

## Süre

Çalışma penceresini 7 Ekim 2026'da yaklaşık 18:34'te, istemeden erken başlattım. İlk yarım saat teknoloji yığınını, veritabanını ve yayın yerini seçmekle geçti. Gerçek aktif çalışma sürem yaklaşık 3–4 saat oldu.

## Araçlar ve çalışma şekli

Kararları ben verdim, kodu Antigravity yazdı. Claude ile görevi anlamak, teknoloji ve veritabanı seçimini değerlendirmek, Antigravity'ye vereceğim ana prompt'un taslağını hazırlamak ve Antigravity'nin çıkardığı planı gözden geçirmek için çalıştım. Veritabanı olarak Supabase'deki PostgreSQL'i, yayın için Render'ı, kaynak kod için GitHub'ı kullandım.

Ana prompt'ta Antigravity'den önce kod yazmamasını, yalnızca mimari bir plan çıkarıp onayımı beklemesini, aşama aşama ilerlemesini, Testcontainers çalışmazsa sahte bir veritabanına (H2) sessizce geçmemesini ve log'a yalnızca gerçekten yaptıklarını yazmasını istedim. Planı onayladıktan sonra beş aşamada ilerledik: iskelet ve veritabanı, API ve testler, arayüz, Docker, belgeler. Her aşamadan sonra çıktıyı ben denedim ve onayladıktan sonra bir sonrakine geçtik.

## Kararlarım

Spring Boot 3, Java 21 ve JdbcTemplate kullandım, çünkü Spring Boot'ta rahatım ve JdbcTemplate ile SQL kodda açıkça görünüyor. Veritabanı olarak Supabase'deki PostgreSQL'i seçtim; bilgisayarımdaki veritabanına canlı site erişemeyeceği için internette çalışan bir veritabanı gerekiyordu. Supabase'i yalnızca veritabanı olarak kullandım. Tarayıcı ona doğrudan bağlanmıyor, doğrulama ve kayıt sunucuda yapılıyor. Böylece başarı mesajı yalnızca kayıt gerçekten oluştuğunda gösterilebiliyor. Sayfa ve API tek uygulamada, arayüz ise framework olmadan düz HTML, CSS ve JavaScript ile yazıldı.

Antigravity'nin oluşturduğu paket yapısını da kendim yeniden düzenledim. Sınıfları sorumluluğa göre ayırdım: `request`, `admin`, `health`, `exception`, `config` ve filtrelerin hepsinin bulunduğu `security` paketi. Böyle daha okunaklı buldum.

## Planda düzelttirdiklerim

Antigravity'nin ilk planını Claude ile birlikte inceledim. Genel yapısını kabul ettim ama şunları değiştirttim:

- **Honeypot:** Plan, bot yakalandığında sahte bir 201 dönüyordu. Görev başarı mesajının yalnızca gerçek kayıtta gösterilmesini istediği için kayıt yazmadan 422 dönmesini istedim.
- **Supabase güvenliği:** Tablo Supabase'in `public` şemasında olduğu için, satır düzeyinde güvenlik (RLS) kapalı kalırsa dışarıdan okunabilirdi. Migration'a RLS'yi açan satırı ekletttim.
- **Gövde sınırı:** Plandaki ayarlar JSON gövdesini sınırlamıyordu. Bunun yerine 16 KB'ı aşan istekleri 413 ile reddeden bir filtre yazdırdım.
- **Hız sınırı:** Yalnızca `POST /api/requests` için geçerli olsun, aksi hâlde sayfanın CSS ve JS dosyaları da sayılırdı.
- **Yapılandırma hatası:** Plandaki `application.properties` örneğinde satır sonuna yorum yazılmıştı, bu dosya biçiminde değeri bozar. Yorumları ayrı satıra taşıttım.
- **Doğrulama sırası:** Boşlukları temizleme ve e-postayı küçük harfe çevirme, doğrulamadan önce yapılsın ki yalnızca boşluktan oluşan bir isim geçmesin.

## Karşılaştığım sorunlar

**Testcontainers çalışmadı.** Testler Testcontainers ile yazılmıştı ama kullandığım Docker Desktop sürümünde bağlantı hatası verdi. Testleri sahte bir veritabanına (H2) taşımak istemedim. Antigravity önce Docker Desktop'ta, Docker'ı şifresiz bir ağ portuna açan bir ayarı açmamı önerdi. Açtım ama bu ayarın güvenlik riski taşıdığını ve sorunu çözmediğini fark edip kapattım. Bunun yerine gerçek bir PostgreSQL konteynerini (`postgres:16-alpine`, 5433 portu) elle başlatıp testleri ona bağladım. Kullanılmayan Testcontainers bağımlılığını ise ileride Docker sürümü güncellendiğinde kolayca geri dönebilmek adına pom.xml içinde bilerek bıraktım, silmedim. Testler artık yalnızca bu yerel konteynere bağlanıyor, Supabase'e hiç dokunmuyor.

**Dönen çark hatası.** Canlı sitede gönder butonundaki dönen işaret form boşken bile sürekli görünüyordu. Kodda bir CSS kuralı, HTML'in `hidden` özniteliğini eziyordu. Bunu canlıda ben fark ettim; otomatik testler görsel durumu ölçmediği için yakalayamamıştı. Antigravity'ye bildirdim, tek satırlık bir CSS kuralıyla düzeltildi.

## Nasıl doğruladım

- **Otomatik testler:** `mvn test` komutunu Java 21 ile kendi bilgisayarımda çalıştırdım, 32 testin 32'si geçti.
- **Yerelde elle:** Veritabanı kapalıyken uygulamanın çökmeden açıldığını, gerçek Supabase'e bağlanınca tablonun oluştuğunu, RLS'nin açık olduğunu ve anon anahtarla sorgulayınca satır dönmediğini kontrol ettim. `curl` ile beş istek gönderdim: geçerli istek 201, geçersiz alanlar 422, honeypot dolu 422, fazladan alan 400, arka arkaya 6 istekte 6.'sı 429 döndü.
- **Tarayıcıda:** Sayfayı 375 px genişlikte, klavyeyle ve hatalı ya da geçerli bilgilerle denedim.
- **Docker ve canlı:** İmajı derleyip çalıştırdım. Canlıda ilk denemede ortam değişkenlerini yanlış yazmıştım, form hata verdi ve başarı mesajı çıkmadı; düzeltince çalıştı, kaydın Supabase'e düştüğünü gördüm. Hız sınırını canlıda da denedim. 
