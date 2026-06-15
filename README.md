# Kurye Takip Servisi

Kuryelerin akış halindeki konum verisini (zaman, kurye, enlem, boylam) REST
üzerinden alan bir uygulama. Üç işi yapar:

- Bir kurye bir Migros mağazasının 100 metresine girdiğinde bunu bir **mağaza
  girişi** olarak kaydeder; aynı mağazaya 1 dakika içinde yapılan tekrar
  girişleri yeniden saymaz.
- Her kuryenin o ana kadarki **toplam seyahat mesafesini** sorgulanabilir tutar.
- Kabul edilen ham konumları denetim izi (audit trail) olarak saklar.

Kullanılan teknolojiler: Java 25, Spring Boot 4.1, Spring Data JPA, MySQL 8.4, Flyway,
springdoc-openapi (Swagger UI), Docker.
Testlerde Testcontainers gerçek bir MySQL üzerinde koşar.

---

## Hızlı başlangıç

Tek gereksinim Docker'dır; JDK kurmaya gerek yok, uygulama imajın içinde derlenir.

```bash
docker compose up --build
```

MySQL ile uygulama birlikte ayağa kalkar. Uygulama: `http://localhost:8080`.
Veritabanı her açılışta sıfırdan kurulur (temiz demo için tmpfs üzerinde), şemayı
Flyway oluşturur.

### İki dakikada doğrula

Ortaköy Migros'a yaklaşan bir kurye: dışarıda → içeri geçiş → içeride kalma.
(`time` alanları yakın geçmişte olmalı; gelecek tarihli noktalar makul-zaman
kontrolünde reddedilir — aşağıda anlatılıyor.)

```bash
curl -X POST localhost:8080/api/v1/locations -H 'Content-Type: application/json' \
  -d '{"courierId":"c1","time":"2026-06-14T10:00:00Z","lat":41.057583,"lng":29.0210292}'
curl -X POST localhost:8080/api/v1/locations -H 'Content-Type: application/json' \
  -d '{"courierId":"c1","time":"2026-06-14T10:00:20Z","lat":41.056583,"lng":29.0210292}'
curl -X POST localhost:8080/api/v1/locations -H 'Content-Type: application/json' \
  -d '{"courierId":"c1","time":"2026-06-14T10:00:40Z","lat":41.055783,"lng":29.0210292}'

# Toplam mesafe (~200 m)
curl localhost:8080/api/v1/couriers/c1/total-travel-distance

# Giriş kaydı (tam 1 tane: içeri geçiş anı; içeride kalmak yeni giriş üretmez)
curl 'localhost:8080/api/v1/store-entrances?courierId=c1'
```

Endpoint'leri tarayıcıdan denemek için Swagger UI: `http://localhost:8080/swagger-ui.html`.

### Geliştirme alternatifi: uygulama lokalde, yalnız MySQL Docker'da

Yukarıdaki `docker compose up --build` zaten **hem MySQL'i hem uygulamayı** tek komutla
container'da ayağa kaldırır (compose dosyasında iki servis vardır; servis adı verilmeyince
ikisi de başlar) — değerlendirme için yapılması gereken tek şey budur. Aşağıdaki alternatif
yalnızca *geliştirme* içindir: kodu her değiştirdiğinde Docker imajını yeniden derlemeden
uygulamayı doğrudan IDE/JVM'de çalıştırmak için, yalnız MySQL'i container'da, uygulamayı
lokalde koşar.

```bash
docker compose up -d mysql   # yalnız veritabanı (servis adı verilince sadece o kalkar)
./mvnw spring-boot:run       # uygulama lokal JVM'de — hızlı kod döngüsü
```

---

## Test çalıştırma

Testler iki kümeye ayrılmıştır ve farklı komutlarla koşar:

```bash
./mvnw test            # unit testler — Docker GEREKMEZ, hızlı
./mvnw verify -Pit     # unit + entegrasyon testleri — Docker GEREKİR (Testcontainers)
```

Bu ayrım bilinçlidir. Entegrasyon testleri gerçek bir MySQL'e ihtiyaç duyar
(Testcontainers). Eğer hepsi tek faza konsaydı, `mvn install` Docker kapalıyken
patlardı — yani derleme fiilen Docker'a bağımlı olurdu. Onun yerine unit testleri
Surefire ile varsayılan `test` fazında (Docker'sız), Docker isteyen `*IT` sınıfları
ise Failsafe ile opt-in `it` profilinde koşar. Sürekli entegrasyon (CI) `-Pit`
kullanır; günlük geliştirme `./mvnw test` ile hızlı döner.

---

## API

Birim her yerde **metre**. Tüm hata cevapları RFC 9457 ProblemDetail
(`application/problem+json`) biçimindedir.

| Yöntem | Endpoint | Cevap |
|--------|----|-------|
| `POST` | `/api/v1/locations` | `202 Accepted` (istek gövdesi: `{courierId, time, lat, lng}`) |
| `GET` | `/api/v1/couriers/{courierId}/total-travel-distance` | `200 {courierId, totalDistanceMeters}` / `404` |
| `GET` | `/api/v1/store-entrances?courierId=...` | `200` giriş kayıtları (zaman sıralı) |

Birkaç contract ayrıntısı:

- **`store-entrances` filtresi opsiyoneldir.** `courierId` verilmezse tüm kuryelerin
  girişleri zaman sıralı döner; verilirse yalnız o kurye. Bilinmeyen kurye `200` +
  boş liste alır — koleksiyon endpoint'inde "eşleşen yok" bir hata değildir.
- **404 yalnızca tekil kaynak endpoint'indedir** (`couriers/{id}/...`). Hiç nokta göndermemiş
  kurye için toplam mesafe sorgusu `404 ProblemDetail` döner.
- **Geçersiz girdi `400`** döner; hangi alanın neden reddedildiği cevabın `errors`
  alanındadır.
- **Geçici çekişme `503 + Retry-After`** döner (ayrıntısı "Eşzamanlılık" başlığında).

**Etkileşimli dokümantasyon (OpenAPI/Swagger).** Uygulama çalışırken canlı OpenAPI 3 spec'i ve Swagger
UI sunulur: Swagger UI `http://localhost:8080/swagger-ui.html`, ham spec `http://localhost:8080/v3/api-docs`.
Spec elle yazılmaz; `springdoc` onu controller imzalarından ve DTO'lardaki Bean Validation
kısıtlarından (`@NotBlank`, `@NotNull`, `@Size`) üretir, böylece koddan sapamaz.

---

## Gereksinim izlenebilirlik tablosu

| Case isteri | Nerede | Test |
|-------------|--------|------|
| Akış halindeki konumları (time, courier, lat, lng) REST ile kabul | `LocationController` → `CourierTrackingService.recordLocation` | `LocationControllerTest`, `CourierTrackingServiceTest` |
| 100 m içine girişi logla | `StoreEntranceDetector` | `StoreEntranceDetectorTest`, `CourierTrackingEndToEndIT` |
| 1 dakika tekrar-giriş bastırma | `StoreEntranceDetector.isDebounceExpired` | `StoreEntranceDetectorTest` |
| `getTotalTravelDistance(courierId)` | `CourierTrackingService.getTotalTravelDistance` → `CourierController` | `CourierControllerTest`, `CourierTrackingEndToEndIT` |
| ≥ 2 tasarım deseni | Strategy, Observer, Repository | — |
| MySQL 8.x + Docker | `docker-compose.yml`, Flyway şeması | `*IT` (gerçek MySQL 8.4) |
| Çalıştırma + test talimatı | Bu README | — |

---

## Varsayımlar

Case metni birkaç yerde yoruma açıktır; aşağıda her birinde hangi yorumu seçtiğimizi ve
nedenini yazdık.

**Giriş, bir durum değil, bir geçiş anıdır.** "Enters" kelimesini olduğu gibi okuyoruz:
giriş, kuryenin bir mağazanın 100 m çemberine *dışarıdan içeri geçtiği* andır. Kurye
içeride kaldığı sürece gelen her konum için tekrar tekrar log yazmıyoruz; yalnızca o tek
geçiş anını yakalıyoruz. Kurye üç dakika içeride otursa bile bu bir giriştir, çünkü tek
bir "dışarıdan içeriye" geçişi olmuştur.

**"1 dakika" bir periyot değil, tekrar-girişleri bastıran bir penceredir.** Aynı kurye +
aynı mağaza için bir giriş yazıldıktan sonra, bir sonraki giriş ancak öncekinin üzerinden
1 dakikadan *fazla* geçmişse sayılır; bu süre içindeki tekrar-girişler yok sayılır. Sebebi
pratiktir: kurye tam sınırın kenarındayken GPS gürültüsü konumu hızla içeri-dışarı oynatabilir
(sınırda hızlı bir salınım), ya da kurye sınırın dibinde birkaç kez girip çıkabilir. Bu kural
olmasa, tek bir gerçek ziyaret onlarca sahte giriş üretirdi; oysa ilk girişten sonraki 1 dakika
içindeki tekrar girişleri saymadığımızdan, bu hızlı gidip gelmelerden yalnızca ilki giriş olarak
kaydedilir, kalanı yok sayılır.

Metindeki "over 1 minute" ifadesi iki noktada belirsizdir; ikisini de kesin seçtik:

- *Süre neyden sayılıyor?* Son yazılan girişin zamanından — kuryenin daireden en son çıktığı
  andan değil. (En temiz ve en yaygın yorum budur.)
- *Eşik dahil mi?* Hayır. "Over 1 minute" gereği süre 1 dakikayı *kesinlikle* aşmalıdır; tam
  60. saniyedeki tekrar-giriş hâlâ bastırılır. Kod bu yüzden "kesin büyük" (`> 1 dakika`)
  karşılaştırması yapar.

Bir de sınır durumu: kuryenin sistemde gördüğümüz *ilk* noktası zaten çemberin içindeyse,
bunu bir giriş sayıyoruz (öncesi "dışarıda" kabul edilir).

**Histerezis yok (tek eşik).** *Histerezis*, girişle çıkış için **iki ayrı eşik** kullanmaktır:
örneğin "100 m'ye girince içeride say, ama ancak 110 m'yi geçince çıktı say". Aradaki 10 m'lik
tampon, kurye tam sınırın üstündeyken konumun ufak oynamalarıyla sürekli "girdi/çıktı" sayılmasını
engeller. Biz tek eşik (100 m) kullanıyoruz, çünkü case yalnızca bir mesafe verdi; ikinci eşiği
(110 m gibi) kendimiz uydurmak olurdu.

Tek eşikle sınırdaki oynama çoğu durumda sorun çıkarmaz: kurye tam sınırda titrese bile — GPS
gürültüsü ya da sınır dibinde hızlı gidip gelme — yukarıdaki **1 dakika debounce** kuralı ilk
girişten sonraki 1 dakika içindeki tekrar girişleri saymaz; bu hızlı giriş-çıkışlardan yalnızca
ilki kaydedilir, sahte giriş üretilmez. Histerezisin çözdüğü asıl sorunu debounce zaten çözüyor.

Geriye tek bir açık senaryo kalır: kurye sınırı *o kadar yavaş* geçer ki her tam tur (içeri gir →
çık → tekrar gir) 1 dakikadan uzun sürer. O zaman debounce penceresi her turda dolduğundan, tek bir
gerçek ziyaret birden çok giriş sayılabilir. Bunu tek eşikle **bilinçle kabul ediyoruz**; pratikte
ender bir durumdur, çünkü gerçek bir kurye sınırda dakikalarca bu kadar yavaş gidip gelmez.

Gerçekten gerekirse çözüm hazır ve nettir: girişe 100 m, çıkışa daha geniş bir eşik (ör. 110 m)
koyup histerezis eklemek. Bu teslimde tek eşik yeterli olduğundan eklemedik.

**Zaman = client'ın beyan ettiği event-time.** 1 dakika kuralı ve sıralama, isteğin
içindeki zamana dayanır, sunucunun alım zamanına değil. Akış gecikmeli ya da sırasız
gelse bile iş anlamı (kuryenin gerçekte ne zaman neredeydi) korunur. Şema kolonları bu
yüzden `event_time` / `last_event_time` adını taşır.

**Sırasız veri kesin biçimde elenir.** Bir nokta, son kabul edilen noktadan *kesinlikle eski*
ise (event-time'ı, son kabul edilenin event-time'ından küçükse) yok sayılır. Yalnızca toplama
eklenmez; ham `courier_locations` tablosuna bile yazılmaz — o tablo akışın yalnızca kabul edilen
kısmını tutar.

Eşit zaman damgası ise meşrudur, reddedilmez. İki durumu ayırmak gerekir:

- *Aynı an, farklı konum:* iki ayrı geçerli noktadır; ikisi de işlenir ve aralarındaki mesafe
  toplama katkı verir.
- *Aynı an, aynı konum:* birebir tekrardır; idempotency onu tekilleştirir, ikinci kez işlenmez
  (bkz. İdempotency bölümü).

Bir incelik var. "Bu nokta eski mi?" kararı, noktanın event-time'ını *o ana kadar kabul edilmiş
en yüksek* event-time ile karşılaştırır — bu değer (`last_event_time`) hep ileri gittiğinden,
o ana dek görülmüş en yeni andır. Aynı yeni kuryenin iki noktası birbirine çok yakın anlarda
gelirse, hangisinin "önce" işleneceğini event-time değil, sunucuya *varış sırası* belirler: ikisi
de aynı satır kilidini almaya yarışır, kilidi önce kapan önce işlenir. Diyelim event-time'ı daha
*geç* olan nokta kilidi önce kaptı — `last_event_time` onun zamanına yükselir. Hemen ardından gelen,
event-time'ı daha *erken* olan nokta ise artık bu eşiğin gerisinde kalır ve sırasız sayılıp elenir.
Bu, seçtiğimiz "son kabul edilenden eskiyi alma" politikasının doğal sonucudur — veri bozulması
değil, bilinçli bir sıralama kararıdır.

**Veri boşluğunda düz çizgi.** Toplam mesafe, ardışık iki kabul edilen nokta arasını kuryenin
*düz gittiği* varsayımıyla, küre üzerindeki en kısa yol (Haversine) kadar sayar. Sinyal kesilip
iki nokta arasında uzun bir boşluk oluşsa bile kural aynıdır: arada ne olduğunu bilmediğimizden
iki nokta düz bir çizgiyle birleştirilir. Gerçek rota kıvrımlıysa bu, mesafeyi olduğundan az
gösterebilir; ama elimizdeki tek veri uç noktalar olduğundan en yalın ve öngörülebilir kabul
budur. Aykırı sıçramaları (ör. GPS'in bir an için kilometrelerce ötede bir nokta üretmesi)
ayıklayan bir anomali/atlama filtresi *kapsam dışıdır*: case bunu istemiyor ve böyle bir filtre,
gerçek ama hızlı hareketi yanlışlıkla eleme riski taşıdığından bilinçle eklenmedi.

**Küre modeli ve birim.** Dünya küre kabul edilir (ortalama yarıçap), birim metredir.

---

## Tasarım kararları

Bu bölüm her önemli kararın gerekçesini ve değerlendirilen alternatifleri açıklar.

### Eşzamanlılık

#### Sorun: kaybolan güncelleme

Her kurye için bir toplam tutuyoruz: o kuryenin gittiği toplam mesafe. Bu, veritabanında
tek bir satır (`courier_travel_stats`). Yeni bir konum gelince şu adımlar işler: son
noktayı oku → yeni noktayla arasındaki mesafeyi hesapla → toplama ekle → son noktayı
güncelle.

Aynı kuryenin iki konumu tam aynı anda gelirse, ikisi de aynı eski son noktayı okuyup
mesafeyi ayrı ayrı ekleyebilir; biri diğerinin yazdığının üzerine yazar ve bir adımın
mesafesi kaybolur. Toplam sessizce yanlışlaşır. Bu duruma lost update denir.

#### Çözüm 1: satırı kilitlemek

Aynı kuryenin isteklerini sıraya sokmak için stats satırını `SELECT ... FOR UPDATE` ile
okuyoruz. Bu, satırı işlem (transaction) bitene kadar kilitler; aynı kuryenin ikinci
isteği birincisi bitene kadar bekler. Böylece oku-hesapla-yaz adımları sıralanır, toplam
doğru kalır. Bir kilidi önceden alıp koruyan bu yaklaşıma pessimistic locking denir.
Farklı kuryeler birbirini beklemez (her kuryenin kendi satırı); kilit işlem bitince
bırakılır. Bu, satır zaten varken doğru çalışır.

#### Sorun 2: kuryenin ilk noktası

Sorun yalnızca satır henüz yokken çıkar — kuryenin ilk noktasında. `FOR UPDATE` kilitleyecek
bir satır bulamaz. İzolasyon düzeyi, eşzamanlı işlemlerin birbirini ne kadar gördüğünü belirleyen
ayardır. MySQL'in varsayılan düzeyi olan REPEATABLE READ altında, var olmayan bir satır için InnoDB
bir *gap lock* alır. Gap lock (aralık kilidi), var olan bir satıra
değil, index'te iki kayıt *arasındaki boşluğa* konan kilittir; tek işi "bu aralığa kimse yeni
satır eklemesin" demektir. Bu kilitlerin kritik özelliği şudur: birbirleriyle *uyumludurlar* —
iki işlem aynı boşluğa aynı anda gap lock koyabilir, birbirini beklemez. Sonuçta aynı yeni
kuryenin iki ilk noktası da `FOR UPDATE`'i geçer (gap lock'lar uyumlu), ikisi de aynı satırı
oluşturmaya (`INSERT`) gider. Ama eklemek için her işlem, *öbürünün* o boşlukta tuttuğu gap
lock'unun kalkmasını beklemek zorundadır; ikisi de karşılıklı beklediğinden bir döngü — deadlock —
oluşur. InnoDB bunu görüp birini geri alır; o istek hata alır (veri bozulmaz, yalnız o tek istek
düşer).

#### Çözüm 2: çakışmayı baştan imkânsız kılmak

İlk noktadaki bu çakışmayı "olduktan sonra yakalamak" yerine baştan imkânsız kıldık.
Satırı kilitlemeden önce, varlığını garanti eden tek bir komut çalıştırıyoruz:

```sql
INSERT INTO courier_travel_stats (...) VALUES (...)
ON DUPLICATE KEY UPDATE courier_id = courier_id
```

Bu komut idempotent'tir (kaç kez çalışırsa çalışsın sonuç aynı): satır yoksa oluşturur,
varsa hiçbir şeye dokunmaz — `courier_id = courier_id` hiçbir şeyi değiştirmez, var olan
son noktayı veya mesafeyi asla ezmez. Bu kalıba upsert denir. Satır garantilendikten
sonra `FOR UPDATE` her zaman gerçek bir satıra denk gelir; gap lock, dolayısıyla ondan
doğan deadlock hiç oluşmaz.

Bu, retry'dan farklı bir yaklaşımdır: retry hatayı geçici bir sorun sayıp yeniden dener;
upsert ise hatanın kaynağını — var olmayan satırı kilitlemeye çalışmayı — ortadan kaldırır.
retry'da çakışma seyrek de olsa mümkün kalır; upsert'te yapısal olarak imkânsızdır. Bunu 20
eşzamanlı ilk nokta ile gerçek MySQL üzerinde, retry olmadan, sıfır hatayla doğruladık
(`CourierTrackingServiceConcurrencyIT`).

> **Neden `INSERT IGNORE` değil.** `INSERT IGNORE` de satırı "varsa dokunma" diye eklerdi, ama
> lock davranışı farklıdır. MySQL'de düz bir `INSERT` — ve `IGNORE` de aslında bir `INSERT`'tir —
> aynı anahtardan zaten varsa, çakışan kayda *shared* bir lock (S; başkaları okuyabilir) koyar.
> Aynı yeni kuryenin iki ilk noktası aynı anda gelir, ikisi de bu S lock'unu alır; sonra her biri
> satırı yazmak için *exclusive* bir lock'a (X; tek sahip) yükselmek ister ama öbürünün S lock'u
> yüzünden yükselemez — ikisi de birbirini bekler, deadlock. `INSERT ... ON DUPLICATE KEY UPDATE` ise
> çakışmada doğrudan X lock aldığından bu ara S adımı hiç oluşmaz; dolayısıyla bu *özel* deadlock sınıfı
> (S→X yükseltme yarışı) ortaya çıkmaz. Burada genel bir "deadlock bağışıklığı" iddia etmiyoruz —
> yalnızca bu sınıfın yok olduğunu; ayrım MySQL 8.4 kılavuzunda birebir belgeli
> ([Locks Set by Different SQL Statements](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html)).

#### Retry neden yok

İlk noktadaki çakışmayı bir retry ile de yönetmek mümkündür: çakışan isteği jitter'lı
(rastgele kısa bekleme) tek seferlik bir yeniden denemeyle tekrar çalıştırmak. Ama retry,
çakışmayı *olduktan sonra* yönetir; upsert ise onu baştan imkânsız kıldığından retry'a hiç
gerek kalmaz. Bu yüzden bu çözümde retry yoktur — bu ölçeğin gerektirmediği bir
mekanizmayı koda koymama ilkesiyle de tutarlı.

Geriye tek teorik durum kalır: aynı kuryede aşırı çekişme altında bir isteğin kilit
beklemesi `innodb_lock_wait_timeout` (5 sn) sınırını aşarsa MySQL onu iptal eder. Bu
ölçekte gerçekleşmez (20 istek ~0,1 sn'de biter), ama olursa istek `503 + Retry-After`
alır. Bu bir retry değildir: sunucu yeniden denemez; geçici çekişmeyi bildirir ve yeniden deneme
sorumluluğunu client'a bırakır. (`503` bilinçli bir seçimdir: `500` "sunucu bozuldu" der, geçici
bir durum için yanlış sinyaldir.)

#### İdempotency: aynı noktanın iki kez işlenmemesi

Bir önceki başlıktaki "retry yok", *sunucunun* kendi kendine yeniden denemediği anlamına gelir.
İdempotency ise ayrı bir konudur: *client* aynı isteği tekrar gönderdiğinde sistemin güvende
kalması. İkisi çelişmez — biri sunucunun davranışı, öbürü endpoint'in client tekrarına karşı dayanıklılığı.

Client aynı noktayı, ağ üzerinden çalışan her sistemde olağan bir durum yüzünden tekrar gönderir:
client bir nokta gönderir, sunucu onu kaydedip commit eder, ama başarı yanıtı dönüş
yolunda kaybolur (zaman aşımı, kopan bağlantı). Client başarıyı *göremediğinden* aynı noktayı yeniden
gönderir. (Yukarıdaki ender `503 + Retry-After` durumu da bunun bir örneğidir; orada yeniden deneme
sorumluluğunu `Retry-After` ile açıkça client'a devrederiz.) İdempotency olmasaydı bu ikinci gönderim noktayı ikinci kez işlerdi: mesafe
çift sayılır, mükerrer bir konum satırı oluşurdu. Bu yüzden konum-kaydetme endpoint'i **idempotent**'tir:
client aynı noktayı kaç kez gönderirse göndersin, sonuç tek bir gönderimle aynı kalır.

**"Aynı nokta" tam olarak ne demek.** Bir noktayı benzersiz kılan dört alan vardır: hangi kurye
(`courier_id`), hangi an (`event_time`) ve nerede (`lat`, `lng`). Bu dörtlü, o noktanın kimliğidir —
başka hiçbir alana (ör. otomatik `id`) bakmadan bir noktayı tek başına tanımlar; veritabanı diliyle
buna **natural key** denir. İki isteğin "aynı nokta" sayılması için bu dört alanın da birebir aynı
olması gerekir.

Bu kimliğe `lat`/`lng`'nin de katılması bilinçli bir seçimdir. Eşit zaman damgasını *farklı* nokta
sayıyoruz (aynı an + farklı konum geçerlidir — bkz. "Varsayımlar"); dolayısıyla "aynı an, farklı
konum" bir tekrar *değildir* ve elenmemelidir. Kimliğe konumu da katarak bunu sağlarız: yalnızca dört
alanın hepsi aynı olan istek bir tekrardır; zamanı aynı ama konumu farklı olan istek ayrı, geçerli
bir noktadır.

**Nasıl çalışır — iki katman.**

- **Ön-kontrol (kod):** Servis, kuryenin kilidi altında ve durumu değiştiren herhangi bir yazımdan
  *önce*, bu dört alanı taşıyan satırın zaten var olup olmadığına bakar. Varsa istek hiçbir şey
  yapmaz; client ilk seferki `202`'yi alır — yeni satır yok, mesafe değişmez. (Ondan önce çalışan tek
  şey, var olan kurye için zaten hiçbir şeyi değiştirmeyen `ensureRow` upsert'idir.)
- **`UNIQUE` kısıtı (veritabanı):** `UNIQUE (courier_id, event_time, lat, lng)` veritabanı düzeyindeki son kontroldür —
  bir hata ya da başka bir kod yolu ön-kontrolü atlasa bile DB mükerrer satıra asla izin vermez. Bu
  kısıt aynı zamanda ayrı bir `(courier_id, event_time)` index'ini gereksiz kılar: soldaki
  `courier_id` öneki `existsByCourierId`'yi zaten karşılar.

**Üç korumayı karıştırma.** Bu endpoint'te üç ayrı kontrol var ve üçü *farklı* soruna bakar:

- `FOR UPDATE` kilidi **eşzamanlı** yazımların birbirini ezmesine karşıdır.
- Watermark (`last_event_time` — o ana dek kabul edilmiş en yüksek, hep ileri giden event-time)
  **sırasız** (eski) noktaya karşıdır.
- Idempotency **mükerrer** (tekrar gönderilen) noktaya karşıdır.

`UNIQUE` kısıtının, kurduğumuz deadlock'suz davranışı bozmadığına dikkat: o kısıt `courier_locations` üzerindedir, `courier_travel_stats`
değil; ve aynı kuryenin yazımları zaten stats kilidiyle sıralandığından bu tabloya eşzamanlı çakışan
ekleme hiç gelmez.

**`double` üzerinde `UNIQUE` güvenilir mi.** MySQL genel olarak float'ı eşitlikle karşılaştırmaya
karşı uyarır — ama o uyarı *farklı yollarla hesaplanmış* değerler içindir. Biz koordinatı
hesaplamayız; client'ın yolladığı değeri olduğu gibi saklar ve birebir kopyasıyla karşılaştırırız.
Aynı ondalık değer her zaman aynı IEEE-754 double olarak saklanır; yazım farkı (`29.02102920` ile `29.0210292`) bile aynı
double'dır. Birebir tekrarın tekilleştiğini end-to-end testimiz `CourierTrackingEndToEndIT` kanıtlar (aynı
nokta iki kez → mesafe bir kez sayılır); aynı-değer-farklı-yazım da bu IEEE-754 özdeşliğinin sonucudur. Bu kullanım
için güvenilir.

**Sınır ve idempotency-key alternatifi.** Bu yaklaşım birebir aynı tekrarı tekilleştirir; bu case'in
ihtiyacı budur. Akla gelen güçlü alternatif **idempotency-key**'dir: idempotent olmayan bir POST'u
güvenle tekrar denenebilir kılmanın sektör standardıdır — bir IETF taslağı
([draft-ietf-httpapi-idempotency-key-header](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/))
hâline gelmiş, [Stripe](https://stripe.com/blog/idempotency) ve AWS gibi sağlayıcılarca uygulanan bir
desendir. Çalışma biçimi şudur: client, o işlemi temsil eden benzersiz bir kimliği — tipik olarak
kendi ürettiği bir UUID'yi — standart `Idempotency-Key` HTTP header'ında gönderir; sunucu işlenmiş
kimlikleri (ör. küçük bir tabloda) saklar ve aynı kimlikle gelen tekrarı yeniden işlemeden ilk yanıtı
döndürür. Kimlik, gönderilen veriden bağımsız olduğundan yeniden serileştirme gibi durumları da çözer.
Bir kurye takip sisteminde, kurye uygulaması da bizim olsaydı doğal evrim yolu idempotency-key
olurdu. Burada uygulamadık çünkü desenin ön koşulu — `Idempotency-Key` header'ını client'ın üretip
göndermesi — bu case'te sağlanmıyor: elimizde yalnızca backend ve case'in verdiği bilgiler var;
kuryenin cihazındaki uygulama bizim tasarımımızda olmadığından bu header'ı göndereceğini varsayamayız.
Backend'in tek başına yapabileceği, noktanın doğal kimliğiyle sunucu-taraflı tekilleştirmedir —
seçtiğimiz de bu.

#### Değerlendirip seçmediğimiz alternatifler

**Retry (tek seferlik yeniden deneme).** En az dokunan, en şeffaf çözüm olurdu; çakışmayı kabul
edilebilir biçimde yönetir, veriyi bozmaz. Sorun şu ki hatayı tedavi eder, kaynağını kaldırmaz:
tek bir yeniden deneme de çakışabilir ve kaç kez deneneceğinin keyfî bir yanıtı vardır. Upsert bu
belirsizliği tümden ortadan kaldırdığı için onu seçtik.

**READ COMMITTED izolasyonu.** MySQL'i varsayılan REPEATABLE READ yerine bu daha gevşek izolasyon
düzeyine almak, ilk noktadaki deadlock'a yol açan gap lock'ları sıradan aramalarda devre dışı
bırakırdı; READ COMMITTED gap lock'u yalnız foreign key ve duplicate-key kontrollerinde kullanır,
sıradan aramalarda değil.
Böylece "var olmayan satır" yarışı, gap-lock deadlock'u biçiminde ortaya çıkmazdı. Ama çakışmayı
*çözmez*, yalnızca biçimini
değiştirir: duplicate-key kontrolü hâlâ çalıştığından aynı kuryeyi aynı anda eklemeye çalışan iki
istekten biri yine başarısız olur — bu kez bir duplicate-key hatasıyla (MySQL hata 1062). İstek yine düşer,
onu yine bir upsert ya da hata yakalama ile karşılamak gerekir. Üstelik tek bir sorgu uğruna tüm
bağlantının okuma tutarlılığını gevşetmek, dar bir soruna geniş bir karardır. Upsert işi zaten
kaynağında bitirdiğinden bu yolun net getirisi kalmadı.

**Optimistic locking (`@Version`).** Bu yaklaşım satırı baştan kilitlemez; "çakışma nadirdir,
yazarken yakalarım" varsayımıyla çalışır (adı da bundandır: *iyimser* kilitleme). Mekanizması bir
*version kolonudur*: tabloya, iş anlamı olmayan fazladan bir sayaç kolonu eklenir ve o satır
her güncellendiğinde bir artırılır. Güncelleme `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`
biçiminde yazılır; araya başka biri girip version'ı değiştirdiyse `WHERE` koşulu hiçbir
satıra uymaz, güncelleme 0 satır etkiler, JPA bunu "biri benden önce yazdı" diye yorumlayıp
`OptimisticLockException` fırlatır — ve işi baştan yapmak, yani yeniden denemek *uygulamaya* düşer.

Bizim senaryomuzda bu kötü çalışır: aynı kuryenin art arda gelen noktaları *tek bir satırı*
(toplam mesafe sayacı) çok sık günceller. Böyle sıcak bir satırda çakışma istisna değil kuraldır;
optimistic burada sürekli version çakışması, yani sürekli yeniden deneme demektir. Pessimistic'te işler
InnoDB satır kilidinde doğal bir kuyruğa girip sırayla beklerken, optimistic her çakışmada yapılan
işi geri alıp baştan denemeye zorlar — sıcak bir sayaç için daha kötüsü. Jakarta'nın kendi kılavuzu da
bunu açıkça söyler: veri sık erişilip değiştiriliyorsa pessimistic, optimistic'ten daha iyi bir
stratejidir ([Jakarta EE — Entity Locking](https://jakarta.ee/learn/docs/jakartaee-tutorial/current/persist/persistence-locking/persistence-locking.html)).
Üstelik bu yol bir version kolonu + migration ister ve ilk noktadaki çakışmayı (satır henüz yokken
karşılaştırılacak bir version de olmadığından) yine çözmez. Sık güncellenen bir sayaç için pessimistic
doğru araçtır.

**Mesafeyi SQL'e taşıyıp tek komutta hesaplamak.** Bugün mesafeyi Java'da hesaplayıp toplama
ekliyoruz; bunun yerine mesafeyi *veritabanının içinde* hesaplatıp toplamı tek bir SQL komutuyla
güncelleyebilirdik. MySQL'in `ST_Distance_Sphere` fonksiyonu tam da bunu verir: iki nokta arasındaki
küre üzerindeki mesafeyi metre cinsinden döndürür — yani Haversine'in işini hazır bir SQL fonksiyonu
olarak ([MySQL 8.4 — Spatial Convenience Functions](https://dev.mysql.com/doc/refman/8.4/en/spatial-convenience-functions.html)).
Eşzamanlılık açısından kazancı şudur: "önce oku, sonra yaz" diye iki ayrı adım kalmaz,
dolayısıyla aralarında kaybolan-güncelleme penceresi de oluşmaz; ilk noktadaki çakışma da aynı tek
atomik adımda çözülür. Bedeli ağırdır. Mesafe hesabı Java'dan SQL'e taşınınca üç şeyi birden
kaybederiz: (1) aşağıdaki Strategy deseni anlamını yitirir — değiştirilebilir `DistanceCalculator`
interface'i ortadan kalkar; (2) çözüm MySQL'in coğrafi fonksiyonlarına bağlanır, başka bir veritabanına
taşınamaz; (3) mesafe matematiğini saf Java'da, beklenen değeri elle hesaplayarak test etme imkânını
kaybederiz. Yani tek bir boyutu (eşzamanlılık) iyileştirmek için üçünü (desen, taşınabilirlik, test
edilebilirlik) feda eder. Eşzamanlılığı zaten upsert + satır kilidiyle çözdüğümüzden ve mesafeyi
bilinçle Java'da tuttuğumuzdan seçmedik.

**Sayacı hiç tutmayıp toplamı okuma anında hesaplamak (append-only).** Bugün her kurye için yürüyen
bir toplam (`courier_travel_stats`) tutuyoruz. Alternatif: hiç toplam tutmamak. Her kabul edilen
noktayı yeni bir satır olarak eklemek (paylaşılan bir sayacı asla güncellememek — yalnızca-ekleme,
yani *append-only*) ve toplam mesafeyi ancak biri *sorduğunda*, o kuryenin tüm noktalarını okuyup
aralarını toplayarak hesaplamak. Paylaşılan bir sayaç olmadığından, en baştaki kaybolan-güncelleme
sorununun *tüm sınıfı* ortadan kalkar; bu yol en yüksek yazma hacmine ölçeklenir. Ama bedeli daha
büyük bir mimari değişikliktir. Toplamı her sorguda baştan hesaplamak, kurye büyüdükçe artan sayıda
noktayı taramak demektir — sorgu başına maliyet, nokta sayısıyla doğru orantılı büyür (O(n); n = o
kuryenin nokta sayısı). Bundan kaçınmak için toplamları arka planda önceden hesaplayan ayrı bir
altyapı (asenkron bir *projection*, yani önceden hesaplanmış ayrı bir okuma tablosu) kurmak gerekir — bu da bizim seçtiğimiz "toplamı yazarken
biriktir, okumayı tek satıra indir" tasarımının (sorgu başına sabit maliyet, O(1)) tam tersidir. Bu
ölçek için gereğinden fazla mühendisliktir. Yine de üretim ölçeğinde doğru evrim yolu
budur; o geçişi "Üretim ölçeğinde nasıl evrilir" başlığında anlatıyoruz.

### Tasarım desenleri

Case en az iki desen istiyor; biz üç kullandık: Strategy, Observer ve Repository. Her birini, bizim
sistemimizde hangi işi gördüğü ve yerine ne koyabileceğimiz üzerinden anlatıyoruz.

#### Strategy — `DistanceCalculator`

İki nokta arasındaki mesafeyi nasıl hesapladığımız ileride değişebilir: bugün Dünya'yı küre kabul
eden Haversine formülünü kullanıyoruz, yarın daha hassas bir model (elipsoit/Vincenty) istenebilir.
Bu yüzden hesabı koda doğrudan gömmek yerine bir interface'in arkasına aldık: `DistanceCalculator`.
Bu interface yalnızca *ne istediğimizi* söyler — "şu iki nokta arası kaç metre?" (`calculateInMeters`). Bu
sorunun *nasıl* yanıtlandığı, interface'i uygulayan sınıfta durur; bugün o sınıf `HaversineDistanceCalculator`.
Mesafeyi kullanan iki yer — mağazaya `≤ 100 m` yakınlığı ölçen `StoreEntranceDetector` ile toplam
mesafeyi biriktiren `TravelDistanceAccumulator` — yalnızca interface'i tanır, `HaversineDistanceCalculator`
adını hiç anmaz. Bir hesabı (burada mesafe formülünü) böyle bir interface'in arkasına alıp gerektiğinde
komple başka bir sınıfla değiştirilebilir kılan desene **Strategy** denir.

Faydası iki türlü. Birincisi: küre yerine elipsoit istense `DistanceCalculator`'ı uygulayan yeni bir
sınıf yazmak yeter, onu kullanan iki kod hiç değişmez. İkincisi test: beklenen mesafeyi kalemle
hesaplayıp (ör. 0,0009° enlem için ≈ 100,0754 m) sınıfın verdiğiyle karşılaştırabiliyoruz — bu, hesap
kendi sınıfında yalıtık durduğu için mümkün.

**Yerine ne koyabilirdik:**

- **Formülü her iki kullanıcının içine ayrı ayrı yazmak.** Aynı matematik iki yerde kopyalanır;
  formülü değiştirmek istersen iki yeri birden düzeltmen gerekir ve mesafe matematiği, giriş-tespiti
  ile toplama mantığının içine karışır.
- **Statik bir yardımcı metoda koymak** (`DistanceUtil.haversine(...)`). Statik çağrı sabittir: testte
  yerine sahte (mock) koyamazsın, farklı bir formülle değiştiremezsin. Şu an testler calculator'ı
  dışarıdan verebiliyor; bu, yalnızca onun bir interface olmasından geliyor.
- **Template Method deseni.** Bu desende bir base class algoritmanın genel adımlarını sabitler,
  değişen tek adımı bir subclass'ın doldurmasına bırakır (inheritance ile). Bizde sabitlenecek ortak
  bir iskelet yok — Haversine'den Vincenty'ye geçince formülün *tamamı* değişir. Override edilecek
  ortak bir adım olmadığından inheritance kurmak gereksiz; tüm hesabı tek bir sınıf olarak değiştirmek
  daha yalın.

#### Observer — `LocationUpdateObserver`

Bir konum kabul edildiğinde birden çok şeyin olması gerekir: kurye bir mağazaya yeni girdiyse giriş
kaydı yazılmalı, bir de kat edilen mesafe toplama eklenmeli. Bu işleri `CourierTrackingService`'in
içinde tek tek çağırmak yerine, servisi onları bilmek zorunda bırakmadık. Servis sadece "şu konum
kaydedildi" diye haber verir; bu habere tepki verecek sınıflar — `LocationUpdateObserver` interface'ini
uygulayanlar — onu kendileri dinler. Bugün iki listener var: `StoreEntranceDetector` (giriş tespiti)
ve `TravelDistanceAccumulator` (mesafe toplama). Spring bu listener'ları servise bir
`List<LocationUpdateObserver>` olarak verir, servis de gelen her konumu döngüyle hepsine iletir. Yeni
bir davranış eklemek — diyelim bir ısı haritası — servise hiç dokunmadan, interface'i uygulayan yeni bir
sınıf yazmaktır. Bir olayı, ona kimin nasıl tepki vereceğini bilmeden duyuran bu desene **Observer**
denir.

Önemli bir ayrıntı: listener'lara giden haber (`LocationUpdate`) *değişmez* bir snapshot'tır. "Önceki
nokta" bilgisi, kilitli stats satırından bir kez okunup bu snapshot'ın içine konur, döngü ondan sonra
başlar. Böylece listener'ların hangi sırayla çalıştığı sonucu değiştirmez — hem mesafe toplayan hem
giriş tespiti yapan, aynı değişmez "önceki nokta"yı görür.

**Yerine ne koyabilirdik:**

- **Servisin listener'ları tek tek, elle çağırması.** O zaman servis her tepkiyi ve hangi sırayla
  çalışacağını bilmek zorunda kalır; üçüncü bir davranış eklemek `recordLocation`'ı yeniden düzenlemek
  olur. Liste üzerinden dağıtınca servis "ne yapılacağını" bilmez; yeni tepki yalnızca yeni bir sınıftır.
- **Spring'in kendi event altyapısı** (`ApplicationEventPublisher` / `@EventListener`). Araya fazladan
  bir katman koyar ve listener'ları asenkron / ayrı yönetilebilir hale getirir. Bizde tepkilerin aynı
  veritabanı işlemi (`@Transactional`) içinde, aynı kilitli satırla ve aynı değişmez "önceki nokta"yla
  çalışması gerekiyor; süreç-içi basit bir liste bunu daha yalın ve daha kontrollü verir.
- **Kafka/RabbitMQ gibi bir mesaj kuyruğu.** Bu ölçekte bir kuyruk sistemi hem işletme yükü hem de
  yeni sorunlar getirir: aynı mesaj birden çok kez gelebilir, geliş sırası garanti değildir ve gelen
  konum anında değil kısa bir gecikmeyle işlenir. Süreç-içi Observer aynı işi tek işlemde, bu
  sorunlar olmadan yapar. (Sistem büyürse doğru yön olabilir; "Üretim ölçeğinde nasıl evrilir"de var.)

#### Repository — `StoreRepository` ve Spring Data JPA

Veriye nereden ulaşıldığını, o veriyi kullanan koddan gizlemek istiyoruz; böylece verinin bir
dosyadan mı yoksa veritabanından mı geldiği çağıran için fark etmez. Veri erişimini böyle bir
interface'in arkasına alan desene **Repository** denir ve bizde iki türü var.

Birincisi elle yazdığımız bir interface: `StoreRepository` yalnızca `findAll()` der. Bugünkü uygulaması
`JsonStoreRepository`, mağazaları açılışta `stores.json`'dan bir kez okuyup doğrular ve değişmez bir
liste tutar. Tek kullanıcısı `StoreEntranceDetector`, verinin bir JSON dosyasından ve bellekten
geldiğini bilmez; yarın mağazalar veritabanına taşınsa yalnızca bu uygulama değişir, detektör aynı kalır.

İkincisi Spring Data JPA repository'leri: bunları yalnızca interface olarak yazarız, gövdelerini Spring
çalışırken kendi üretir. `CourierTravelStatsRepository` (kilitli okuma `findWithLockingByCourierId` ve
`ensureRow` upsert'i), `CourierLocationRepository` (idempotency kontrolü
`existsByCourierIdAndEventTimeAndLatAndLng`), `StoreEntranceRepository`. Servis de listener'lar da hep
bu interface'lere bakar, ham SQL'e değil.

**Yerine ne koyabilirdik:**

- **Servisin içinde doğrudan `EntityManager`/JDBC kullanmak.** Kilit, upsert, idempotency kontrolü gibi
  veritabanı ayrıntıları iş mantığının içine dağılır ve servis belirli bir veritabanı API'sine
  bağlanır. Interface'te `ensureRow` / `findWithLockingByCourierId` / `save` derken servis iş diliyle
  yazılmış kalır.
- **Active Record** (entity kendini kaydetsin, ör. `stats.save()`). Verinin durumu ile saklanma biçimi
  tek sınıfta birbirine karışır. Bizde `CourierTravelStats` yalnızca mesafe/zaman davranışını bilir,
  nasıl saklandığını bilmez; bu onu hem test edilebilir hem saklamadan bağımsız tutar.
- **JPA tabloları için elle DAO sınıfları yazmak.** Spring Data bu sınıfları interface adından ve
  anotasyonlardan kendi üretir; `save` / `findById` / sayfalama gibi tekrar eden kodu elle yazmak
  sıfır kazançtır.

### Paketler ve null-güvenliği

Paketler yeteneğe göre adlandırılır (`distance`, `entrance`, `domain`, `repository`,
`controller`, `dto`, `configuration`, `exception`, `service`).
Her uygulama paketinde bir `package-info.java` dosyası vardır ve tek işi o pakete
`@NullMarked` (JSpecify) uygulamaktır.

`@NullMarked`, paketteki tüm tiplerin varsayılan olarak **null-olmayan** sayılmasını söyler;
yalnızca gerçekten null olabilen yerler `@Nullable` ile işaretlenir. Kodda bunlar sayılıdır ve her
biri somut bir "yokluk" durumunu anlatır: `LocationUpdate.previous` (bir kuryenin ilk noktasının
öncülü olmadığından null'dır), `GET /api/v1/store-entrances` endpoint'indeki opsiyonel `courierId` sorgu
parametresi (`@RequestParam(required = false)` — client vermezse null gelir) ve `RawStore`'un
alanları (doğrulanmamış JSON girdisi olduğundan ad/koordinat eksik gelebilir, doğrulama bunları sonra
reddeder). Böylece null davranışı contract'ı tek yerde, paket düzeyinde kurulur: her alana `@NonNull`
serpiştirmek yerine varsayılan non-null olur, hem okuyucu hem statik analiz araçları neyin null
olabileceğini tahmin etmeden bilir.

Dayanak: bu yaklaşım Spring Framework 7'nin kendi yaptığıdır. Spring 7 de null davranışını
JSpecify ile bildiriyor ve kendi paketleri `@NullMarked` package-info taşıyor; biz de aynısını
yapıyoruz ([Spring Framework Null-safety](https://docs.spring.io/spring-framework/reference/core/null-safety.html),
[JSpecify](https://jspecify.dev/)). Hiç anotasyon koymamak birkaç dosya tasarruf ederdi; karşılığında null davranışı örtük kalır,
contract okuyucunun ve araçların tahminine bırakılır ve NPE riski sessizce taşınır.

### Veri modeli ve denormalizasyon

Üç entity ve bir value object var:

- **`CourierTravelStats`** — kurye başına biriken toplam mesafe + son bilinen nokta.
  Toplam mesafe **okuma anında değil, yazma anında** birikir (bilinçli denormalizasyon):
  her konum geldiğinde güncellenir, böylece toplam mesafe sorgusu geçmişi taramak yerine
  tek satır okur (O(1)). Normalize bir model (sorguda `SUM`) tek doğruluk kaynağı olurdu ama her
  sorguda O(n) tarama getirirdi. Akış doğası ve sık okuma için O(1) tercih edildi. Normalden ne zaman taviz verilir: okuma sıklığı yazma karmaşıklığını
  haklı çıkardığında — burası tam o durum.
- **`CourierLocation`** — kabul edilen her ham nokta; denetim izi. Toplam ondan
  *türetilmiyor* ama saklanıyor: gerektiğinde toplam ham kayıttan yeniden hesaplanabilir
  (denetlenebilirlik, bedelsiz).
- **`StoreEntrance`** — giriş kayıtları; case'in "log" isterinin sorgulanabilir hali.
- **`GeoPoint`** (value object) — `lat`/`lng` çiftini hesaplama sınırında tek tip altında
  taşır; dört ayrı `double` parametresinin yer değiştirme riskini kapatır.

### Mesafe: Haversine ve `double`

Mesafe, küre üzerindeki en kısa yol (büyük çember) olan Haversine formülüyle hesaplanır
(R = 6.371.000 m). Formül önce 0–1 arası bir ara değer (`a`) bulur, sonra ondan merkez açıyı
çıkarır; mesafe = R × açı. Açıyı çıkarmanın iki yolu vardır: `2·asin(√a)` ve `2·atan2(√a, √(1−a))`.
Biz ikincisini, `atan2` formunu kullanıyoruz. `atan2(y, x)` iki argümanlı ark-tanjanttır: orijinden
`(x, y)` noktasına giden açıyı verir ve iki argümanın işaretine bakarak doğru çeyrek dilimi seçer.
Burada onu tercih etmemizin asıl nedeni şudur: `asin`'in tanım aralığı yalnız `[−1, 1]`'dir ve
kayan-nokta yuvarlaması `√a`'yı ender olarak 1'in az üstüne taşıyabilir; o anda `asin` tanımsız kalır,
bu yüzden `asin(min(1, √a))` gibi değeri elle `[−1, 1]` aralığına sıkıştıran bir clamp gerekir.
`atan2` her gerçek argüman çifti için tanımlı olduğundan bu clamp'i gerektirmez; aynı sonucu daha sağlam verir.

Koordinat aralık doğrulaması calculator'ın işi değildir — o saf matematiktir; doğrulama REST
katmanının işidir.

**`double`, `BigDecimal` değil.** `BigDecimal` ondalık kesinlik ve yuvarlama kontrolü verirdi.
Ama GPS verisinin kendi hatası zaten metre mertebesindedir; model
(küre kabulü) bundan daha büyük bir hata taşır; aritmetik hata ikisinin yanında ihmal
edilebilir. `BigDecimal` burada *yalancı hassasiyet* ve gereksiz maliyet olurdu. Hata
bütçesi GPS ≫ model ≫ aritmetik olduğundan `double` doğru seçim.

### Mağaza verisi

Mağazalar, case ile birlikte verilen `stores.json` dosyasından okunur. Bu dosya sabit bir
referans verisidir; uygulama çalışırken değişmez. İçeriği, uygulama açılırken bir kez okunup
belleğe alınır.

Dosya yoksa ya da içeriği bozuksa uygulama hatayı ertelemez: ilk konum isteği geldiğinde çökmek
yerine, daha açılış anında durur ve hiç ayağa kalkmaz. Böylece eksik veya hatalı bir veri dosyası
ilk gerçek istekte değil, dağıtım anında hemen fark edilir. Bir sorunu mümkün olan en erken anda,
sessizce devam etmeden bildiren bu davranışa fail-fast denir.

**Neden bellekte, her seferinde veritabanından değil.** Mağaza listesi *her konum ping'inde*
okunur ama çalışırken *hiç değişmez*. Böyle bir veriyi her ping'te veritabanından çekmek
saniyede binlerce gereksiz tur demektir; bir kez yükleyip bellekten okumak ise standart ve
verimli olan yoldur — özünde, salt-okunur referans verisini uygulama düzeyinde önbelleğe almaktır.
Şunu da eklemek gerekir: mağazalar bir veritabanında dursaydı bile, performans için onları yine
açılışta belleğe yükleyip oradan okurduk. Yani verimli olan kısım *bellekten okuma*dır ve bu,
verinin dosyadan mı yoksa veritabanından mı geldiğine bakmaksızın aynı kalır.

**Kaynak soyutlanmıştır.** Mağazalara `StoreRepository` interface'inden erişilir; kullanan kod
(`StoreEntranceDetector`) verinin nereden geldiğini bilmez. Bugünkü implementation (`JsonStoreRepository`)
JSON'dan yükler; yarın MySQL'den okuyan bir implementation yazılsa yalnız o tek sınıf değişir, çağıran
kod değişmez.

**Veri değişse de yapısal olarak verimlidir.** Bu case'te mağazalar statik olduğundan, çalışırken
yeniden-yükleme ya da önbellek-geçersizleştirme gibi bir mekanizma *kurmuyoruz* — olmayan bir
senaryo için makine eklemek fazlalık olurdu. Ama mağazalar bir gün dinamikleşirse (çalışırken
eklenip çıkarılırsa), tazeleme stratejisi —zamanlanmış yeniden yükleme, süreli (TTL) önbellek, ya
da "değişti" event'iyle geçersiz kılma— tümüyle `StoreRepository` uygulamasının içine girer;
`StoreEntranceDetector` ve bellekten-okuma davranışı değişmez. Yani o değişiklik küçük ve yereldir.
Tasarımın "değişime karşı verimli" olması, bu mekanizmayı bugünden kodlamakla değil, gerektiğinde
ucuza eklenebilir kılmakla sağlanır.

### Mağaza yakınlık taraması

Bir konum geldiğinde, içinde olunan mağazayı bulmak için mağaza listesi taranır
(`StoreEntranceDetector`). Bu tarama bilinçli olarak verimli ve ölçeğe dayanıklıdır:

- Mağaza listesi açılışta belleğe yüklenir; her konum için veritabanına gidilmez.
- Akışın baskın durumu "hiçbir mağazaya yakın değil"dir; mesafe karşılaştırması bu durumda
  hemen kısa devre yapar, dolayısıyla ping başına maliyet birkaç ucuz işlemdir.

Bu nedenle tarama yalnız 5 mağaza için değil, yüzlerce-binlerce mağaza için de rahat çalışır.
Büyüklük olarak: binlerce mağaza ve on binlerce kuryeyle bile saniyedeki toplam hesap yükü tek
bir çekirdeğin işidir, üstelik bölgeye göre kolayca paralelleştirilebilir — yani sistemi
zorlayan bir yük değildir.

Mekânsal bir index (geohash/grid ön-eleme ya da veritabanı tarafında PostGIS) ancak çok daha
büyük ölçekte anlam kazanır: mağaza sayısı on binlere çıkıp veri artık statik referans olmaktan
çıktığında. O gün bile değişiklik yereldir — yakınlık sorgusu `StoreRepository` interface'inin
ardında bir mekânsal uygulamaya geçer, çağıran kod ve kesin `≤ 100 m` kararı aynı kalır. Bunu
bugünden eklemek ise ölçülemez bir kazanç karşılığı yeni bir hata yüzeyi (hücre sınırındaki
mağazalar, çok-hücre sorgusu) getirirdi.

### Şema yönetimi: Flyway

Şemanın sahibi uygulama değil, **Flyway**'dir: `db/migration/V1__create_initial_schema.sql`
şemayı kurar, Hibernate `ddl-auto: validate` ile yalnızca entity'lerin bu tanıma uyduğunu
*doğrular*, şema *üretmez*.

**Neden Liquibase değil.** Liquibase'in ayırt edici gücü veritabanı bağımsızlığıdır
(XML/YAML'den çok-DB'ye SQL üretir). Burada hedef DB isterle sabit (MySQL 8.x) olduğundan
bu güç sıfır getiri sağlar, yalnız gereksiz bir katman ekler. Üstelik şema MySQL'e özgü
`utf8mb4_bin` collation'a dayanır; düz SQL bunu dolaysız ifade eder, bildirimsel soyutlama burada
zaten terk edilirdi. Tek versiyonlu bir şema için versiyonlu düz SQL en şeffaf, en yalın biçimdir.
Liquibase, ileride çok-DB hedefi gelirse hazır olmayı sağlardı; ama bugün için okunması gereken bir
soyutlama katmanından ve sıfır pratik getiriden ibaret.

**Neden ayrı migration projesi değil.** Migration uygulamaya gömülüdür çünkü veritabanının
tek sahibi bu uygulamadır; kod ve şema versiyonu kilitli kalır, tek komutla çalıştırılabilirlik
korunur. Üretim ölçeğinde (çok servis, platform ekibi) migration ayrı bir pipeline adımına taşınır
ve uygulamaya DML-only (şema değiştirmeyen) yetki verilir — o ayrım "Üretim ölçeğinde nasıl evrilir"de.

**Neden `validate`, `update` değil.** `ddl-auto: validate` kullanılır: şemanın sahibi Flyway
olduğundan Hibernate yalnızca entity'lerin şemaya uyduğunu doğrular, şema üretmez. `update`,
şemayı entity'lerden çıkarsayıp otoriter tanımdan sessizce sapma riski taşır; `validate` bu riski
tümden kapatır ve şemanın tek sahibi V1 migration olarak kalır.

### Şema: anahtarlar, tipler ve index'ler

#### Natural key mi, surrogate key mi

Şemada iki tür kimlik var ve her tabloda işine uyanı kullandık. **Natural key**, gerçek dünyadan
gelen, zaten anlamlı bir kimliktir; **surrogate key** ise sistemin ürettiği, anlamsız (genellikle
auto-increment) bir kimliktir.

`courier_travel_stats`'ta her kuryeye **tek satır** düşer ve bu satıra hep `courier_id` ile
erişilir. `courier_id` zaten kuryenin kimliğidir — client'tan gelir, biz üretmeyiz; yani bir natural
key. Onu doğrudan **primary key** yapmak en sade ve en hızlı yoldur: sorgu doğrudan clustered PK
index'ine (satır verisi doğrudan PK index'inde saklanır, ayrı bir satır araması gerekmez) düşer.
Yerine auto-increment bir surrogate key koysaydık, erişim her seferinde önce `courier_id` üzerindeki
ikincil index'e, oradan PK'ya, oradan satıra giderdi — fazladan bir dolaylama, sıfır kazanç.

Dahası, eşzamanlılık çözümümüz bunu *gerektirir*: `ensureRow` upsert'i yalnız `courier_id` tablonun
tek benzersiz anahtarı (PK) olduğu ve ikincil bir unique index bulunmadığı için deadlock'suzdur
(nedeni Eşzamanlılık bölümünde; gerçek MySQL'de doğrulandı). Surrogate key'e geçmek `courier_id`'ye
ayrı bir UNIQUE ikincil index eklemeyi zorunlu kılardı — ki bu, doğruladığımız deadlock'suz davranışı
yeniden riske atardı.

`courier_locations` ve `store_entrances`'ta ise kurye başına **çok satır** vardır; orada `courier_id`
benzersiz değildir, satırın kendi kimliğine ihtiyaç olur. Bu yüzden bu tablolarda auto-increment bir
surrogate key (`id`) kullanılır. Kısaca kural: **tek satır → natural key, çok satır → surrogate key.**

#### Tipler

- **`courier_id VARCHAR(255)`:** client'tan gelen bir string; 255, yaygın bir varchar boyudur ve
  DTO'daki `@Size(max=255)` doğrulamasıyla hizalıdır.
- **`id BIGINT` (auto-increment):** `courier_locations` ve `store_entrances` yüksek hacimli,
  sürekli ekleme yapılan tablolardır. `INT` (4 bayt, tavan ~2,1 milyar) bu tablolarda zamanla
  taşabilir; tavana ulaşınca yeni INSERT'ler patlar. `BIGINT` (8 bayt, tavan ~9,2 × 10¹⁸) pratikte
  tükenmez — yüksek-hacimli auto-increment anahtar için standart, güvenli seçim.
- **`event_time` / `last_event_time` / `entrance_time` `DATETIME(6)`:** mikrosaniye çözünürlük,
  servisin `truncatedTo(MICROS)` contract'ıyla birebir hizalı. `DATETIME` dilimsizdir; `Instant`
  (her zaman UTC) ile tutarlı gidip gelmesi için `hibernate.jdbc.time_zone: UTC` ayarlandı. Böylece
  değer, sunucunun JVM zaman dilimi ne olursa olsun UTC olarak yazılır/okunur — farklı
  dilimli bir makinede ya da diliminin değiştiği bir dağıtımda zaman kaymaz. (Aksi hâlde Hibernate
  `Instant`'ı JVM'in yerel dilimine göre çevirir ve değer dilime bağımlı hâle gelirdi.)
- **`lat` / `lng` `DOUBLE`:** koordinat için standart; saklama ve Haversine matematiği için yeterli
  kesinlikte (hata GPS gürültüsünün çok altında, bkz. Mesafe bölümü). `DOUBLE` *yaklaşık* bir tiptir;
  bu yaklaşıklık yalnızca eşitlik/benzersizlik kontrolünde önemli olur ve oraya yalnızca idempotency `UNIQUE`'i girer — orada da
  güvenli olduğu doğrulandı (İdempotency bölümü).

#### `ENGINE = InnoDB` ve `CHARSET = utf8mb4`

Her `CREATE TABLE`'da açıkça yazılıdır, ama MySQL 8'de **ikisi de zaten varsayılandır** — teknik
olarak gereksizdir. Yine de açıkça belirtmek, şemanın *bağımlı olduğu* şeyi belgeler: InnoDB
(transaction, satır kilidi, `FOR UPDATE`; eski MyISAM bunları desteklemez) ve `utf8mb4` (tam
Unicode; MySQL'in "utf8"i aslında bozuk 3-baytlık `utf8mb3`'tür). Böylece sunucunun varsayılan
ayarına güvenmek gerekmez. Bu MySQL'e özgüdür — ör. MSSQL'de takılabilir storage engine
olmadığından `ENGINE=` cümlesi de yoktur.

#### Index'ler

Her index gerçek bir sorgu desenine göre konuldu; ne eksik, ne fazla:

- **`courier_travel_stats` → PK (`courier_id`).** Tüm erişim `courier_id` ile olduğundan PK
  hepsini karşılar; başka index gerekmez.
- **`courier_locations` → `UNIQUE (courier_id, event_time, lat, lng)`.** Tek index hem bütünlük
  hem erişim görür: idempotency'yi (mükerrer nokta) garantiler ve soldaki `courier_id` önekiyle
  `existsByCourierId`'yi karşılar. Ayrı bir `(courier_id, event_time)` index'i gerekmez — bu UNIQUE
  onun da işini görür. (Ayrıntı: İdempotency bölümü.)
- **`store_entrances` → `(courier_id, store_name, entrance_time)`.** 1 dakika kuralının —en sık
  çalışan— sorgusunu karşılar: `(courier_id, store_name)` ile filtreler, `entrance_time`
  index'te sıralı olduğundan "en son giriş" ek sıralama olmadan gelir.
- **`store_entrances` → `(entrance_time)`.** Filtresiz, zaman-sıralı listeyi karşılar; index zaten
  sıralı olduğundan sıralama bedavadır.

İki `store_entrances` index'i farklı sorguya hizmet eder, çakışmaz; gereksiz index yoktur (her
index bir yazma ve yer maliyetidir). Tek küçük nüans: tek bir kuryenin girişlerini zaman sırasıyla
listeleyen sorgu, composite index'le `courier_id`'yi filtreler ama saf `entrance_time` sırası için
küçük bir bellek-içi sıralama (filesort) yapar — kurye başına giriş sayısı az olduğundan ihmal
edilebilir, bu yüzden ayrı bir index eklemedik.

#### Foreign key neden yok

Foreign key, işaret edeceği bir *ana tablo* (parent) ister; bu alanda öyle bir tablo yoktur.
Kuryeler **kayıtlı bir varlık değildir** — `courier_id` sadece akışla gelen bir etikettir; bir
kurye "var olur" çünkü konum gönderir (ilk konum stats satırını yaratır). Bağlanacak otoriter bir
kurye listesi yoktur. Mağazalar ise **bellektedir** (`stores.json`), DB'de değil; dolayısıyla
`store_entrances`'ın bağlanacağı bir mağaza tablosu da yoktur.

Kurulabilecek tek FK —konum → stats— bile doğru olmazdı: stats satırı zaten aynı transaction'da o
konum yüzünden yaratıldığından bu FK gerçek bir bütünlük katmaz, ama her konum eklemesinde stats
satırına ek kilit etkileşimi (çok yazılan satıra ek çekişme) getirirdi. Bütünlüğü, satır oluşturmayı tek elde
tutan, konumu kabul edip kaydeden mantık (`ensureRow`) zaten sağlar. Kuryeler kayıtlı bir varlık olsaydı ya da
mağazalar DB'de dursaydı FK yerinde olurdu — bu domain'de değil. (Yüksek hacimli, akış-temelli
sistemlerde FK'lar genelde bu nedenlerle bilinçle kullanılmaz.)

### Kolon adlandırması ve collation

Kolon adları ne tuttuklarını söyler: `total_distance_meters` birimi adında taşır;
`event_time` / `last_event_time`, client'ın beyan ettiği zamanı tuttuğunu söyler — bilinçle
`recorded_at`/`last_time` *değil*, çünkü o adlar sahip olmadıkları bir "sunucu alım zamanı"
anlamını ima ederdi. `lat`/`lng` kısaltmaları case verisiyle (`stores.json`) hizalı kalsın
diye korundu.

`courier_id` ve `store_name` kolonları `utf8mb4_bin` collation taşır: MySQL 8'in varsayılan
`utf8mb4_0900_ai_ci` collation'ı büyük/küçük harf VE aksan duyarsızdır — "ali", "ALI" ve
"ALİ"yi tek kimlik olarak birleştirirdi (canlı doğrulandı). Kimliklerin byte düzeyinde, bire bir
eşleşmesi gerekir. Aynı collation entity tarafında da `@Collate("utf8mb4_bin")` ile bildirilir; böylece
Hibernate eşlemesi migration'la birebir aynı kalır ve `ddl-auto: validate` anlamını korur —
collation hem otoriter SQL'de hem entity'de yazılıdır.

### Doğrulama nerede yaşar

Girdi kuralları DTO'da, Bean Validation ile durur (`@NotBlank`, `@NotNull`, `@DecimalMin/Max`, `@Size`);
tetik controller'da `@Valid` ile. JPA entity'lerinde Bean Validation **yoktur**: bir entity'nin
geçerli olması için gereken kurallar (her alanın dolu olması gibi) constructor'da tutulur — tüm
alanlar zorunlu parametredir, yani eksik alanla nesne hiç kurulamaz; null değerler de constructor
içinde reddedilir. Kuralları entity'de toplamak tek
yerde olmayı sağlardı; ama persist anında doğrulama hatası sınırdan uzakta `500` üretirdi; oysa API
contract'ının evi DTO'dur ve orada hata düzgün bir `400`'e çevrilir.
Ölçüt: contract'ın *tanımladığını* doğrula, tanımlamadığını kısıtlama — `courierId`'ye format
dayatmıyoruz (değerlendirici kendi verisini kuralımız elememeli), ama `@Size(max=255)` ile
`varchar(255)` kolon sınırını sınırda `400`'e çeviriyoruz.

Bir ayrıntı: DTO'da koordinatlar **boxed `Double` + `@NotNull`**'dır; primitive `double` olsaydı
eksik alan sessizce `0.0` olur ve geçerli bir koordinat (ekvator/sıfır meridyen) sayılırdı.

Şema tarafında ise her veri kolonu `NOT NULL`'dır (entity'lerde `nullable=false`): bir takip
kaydının her alanı —kurye, zaman, koordinat, mesafe— zorunlu bir olgudur. Bu, DTO doğrulamasının
arkasındaki depolama katmanı kontrolüdür; contract bir alanı kaçırsa bile satır yazılamaz.

### Zaman ve makul-zaman kontrolü

`time` alanı `Instant`'tır; offset'li ISO-8601 kabul edilir (`"Z"` / `"+03:00"`), UTC'ye
normalize edilir; dilimsiz zaman `400` alır — 1 dakika kuralı bu alana dayandığından
belirsiz girdi sessizce yorumlanmaz.

Bir **makul-zaman kontrolü** var: gelecek tarihli (sunucu saati + 5 dk toleransı aşan) bir
nokta `400` ile reddedilir. Sebep asimetrik: eski bir nokta yalnız kendine zarar verir
(sırasız sayılır, elenir), ama gelecek tarihli bir nokta "son bilinen zamanı" ileri
taşıyıp *sonraki tüm meşru noktaları* eletir — yani watermark'ı bozar. Bu bir iş
kuralı değil, girdi makullük kontrolüdür; event-time kararıyla çelişmez. Tolerans
(`max-clock-skew`) koda gömülü değil, `application.yml`'de yaşar.

### Altyapı ve küçük kararlar

- **Virtual threads açık.** Her isteğe bir thread ayrılır. Klasik (platform) thread'lerde bir istek
  bloklayan bir JDBC çağrısında beklerken bir OS thread'ini boşuna meşgul eder; virtual thread'lerde bu
  bekleme OS thread'ini tutmaz, yani az sayıda OS thread'iyle çok daha fazla eşzamanlı istek taşınır.
  Bunu belirsiz bir hız kazancı için değil, Java 25'te artık güvenle kullanılabildiği için açtık:
  JEP 491'den sonra synchronized bloklar virtual thread'i bir OS thread'ine kilitlemiyor (eskiden
  "pinning" denen bu sorun kalktı), yani gizli bir bedeli yok. Asıl eşzamanlılık sınırı yine de
  aşağıdaki Hikari havuzudur.
- **Hikari havuzu bilinçle boyutlandırıldı** (10). Virtual threads istek kabulünü sınırsızlaştırır;
  yani yükü asıl sınırlayan havuzdur (backpressure): 10 bağlantının hepsi meşgulse yeni istekler
  boş bağlantı açılana kadar bekler. `FOR UPDATE` bekleyişi de varsayılan 50 sn yerine 5 sn'ye
  çekildi: takılan bir istek bir bağlantıyı 50 sn işgal edip havuzu tüketmesin.
- **Lombok yok.** Lombok, getter/constructor/equals gibi her sınıfta elle yazılan tekrarlı kalıp
  kodu (boilerplate) derleme sırasında otomatik üretir. Burada buna gerek yok: value object'leri —
  `GeoPoint` gibi, birkaç değeri bir arada tutan küçük veri nesnelerini — zaten `record`'lar
  karşılıyor ve sınıfların constructor'ları az ve kısa. Ayrıca Lombok bir annotation
  processor'dır — derleme anında çalışan bir kod üreteci; yeni bir JDK çıktığında uyumlu sürümünü
  beklemek gerekebilir (kaynak değişmese de derleme kırılabilir) ve kodu IDE'de okunur kılmak için bir
  IDE eklentisi ister. Kazanç küçük, eklemedik. Çok sayıda farklı constructor isteyen bir model
  olsaydı denge değişebilirdi; burada öyle bir durum yok.
- **Builder yok.** Builder, bir nesnenin çok sayıda *opsiyonel* alanı olduğunda işe yarar — aksi
  halde her alan kombinasyonu için ayrı, giderek uzayan constructor'lar yazmak gerekir (telescoping
  constructor). Bizim entity'lerimizde alanlar az ve hepsi *zorunlu*; bu durumda sade bir constructor
  hem daha basit hem de bir alanı unutursan derleme anında hata verir.
- **`open-in-view: false`.** Spring varsayılanı, veritabanı oturumunu (persistence context) HTTP
  isteği bitene — yani yanıt JSON'a serileştirilene — kadar açık tutar; buna Open Session in View
  denir. Bu, yanıt JSON'a yazılırken beklenmedik lazy loading sorguları doğurabildiği ve
  bir veritabanı bağlantısını gereğinden uzun meşgul ettiği için yaygın olarak anti-pattern sayılır.
  Bilinçle kapattık: bizde tüm veri erişimi zaten servis/transaction sınırı içinde biter.
- **Mesaj kuyruğu (Kafka/RabbitMQ) yok.** Gelen konumu *kabul etmeyi* *işlemekten* ayırıp bir
  kuyruğa yazmak, ani yük altında tampon ve ölçek sağlardı. Ama bu ölçekte gereksiz: bir kuyruk
  sistemi hem işletme yükü hem de yeni sorunlar getirir — aynı mesaj birden çok kez gelebilir,
  geliş sırası garanti değildir ve sonuç anlık değil gecikmeli tutarlı olur. Süreç-içi Observer
  aynı işi tek bir veritabanı işlemi (transaction) içinde, bu karmaşıklık olmadan görür. Üretimde
  ölçek büyürse Controller'ın yerini bir `@KafkaListener` alıp aynı `recordLocation` akışını besler.
- **API dokümantasyonu: OpenAPI (springdoc).** OpenAPI spesifikasyonunu elle yazmıyoruz; `springdoc`
  onu çalışan uygulamadan — controller'ların imzalarından ve DTO'lardaki doğrulama kısıtlarından —
  otomatik üretir. Böylece dokümantasyon koddan sapamaz: kod değişince spec de değişir. Elle yazılan
  statik bir OpenAPI dosyası bunu veremez; kod değişince sessizce eskir. Controller'lara `@Operation`
  gibi açıklama anotasyonları da eklemedik: üç endpoint zaten yolundan, HTTP yönteminden ve istek
  gövdesinden anlaşılıyor, fazladan açıklama metni bilgi katmazdı. Tasarım gerekçeleri bu README'de.
- **Secret'lar koda ya da repoya gömülü değil.** Veritabanı bağlantı bilgileri 12-factor tarzı ortam
  değişkenlerinden okunur (`SPRING_DATASOURCE_*`, compose'ta `DB_PASSWORD`); `application.yml` ve
  `docker-compose.yml`'de görünen `courier123` yalnız sıfır-kurulum demo için bir *varsayılandır* —
  compose'taki MySQL zaten yalnız `127.0.0.1`'e bağlı ve `tmpfs`'te (her restart'ta silinir), yani
  gerçek bir secret değil. Gerçek değerler `.env`'e konur; `.env` `.gitignore`'dadır, `.env.example`
  yalnız anahtarları belgeler — değerleri değil. Üretimde bu env değişkenleri bir secret manager'dan
  (AWS Secrets Manager / SSM Parameter Store, HashiCorp Vault, Kubernetes Secrets) enjekte edilir;
  uygulama aynı değişkenleri okuduğundan kod değişmez. Bu case'te gerçek bir secret manager kurmadık
  (altyapısı yok), ama tasarım ona hazırdır: hiçbir secret koda ya da plaintext olarak repoya gömülü değil.
- **Docker imajı: çok aşamalı (multi-stage), çalışma anında JRE.** İlk aşama `eclipse-temurin:25-jdk`
  ile uygulamayı derleyip paketler; çalışma aşaması `eclipse-temurin:25-jre` ile yalnız üretilen jar'ı
  kopyalayıp çalıştırır. `eclipse-temurin` Adoptium'un TCK-onaylı standart OpenJDK dağıtımıdır; `25`
  projenin hedeflediği Java 25 ile hizalı; `-jre` (jdk değil) çalışma imajını seçer çünkü o imaj derlemez,
  yalnız çalıştırır — derleyici/araçlar olmadığından imaj küçük, saldırı yüzeyi dar kalır. Uygulama
  `root` değil, `appuser` (system user) olarak koşar.

### Test stratejisi

- **İki katman: unit (Docker'sız) + entegrasyon (Docker'lı).** Testlerin büyük çoğunluğu
  unit test'tir (`*Test`, 81 adet): Surefire ile varsayılan `test` fazında, mock'lar ve
  web-slice (yalnız web/controller katmanını yükleyen, veritabanını ayağa kaldırmayan dilimlenmiş
  test — `@WebMvcTest`) ile koşar — **gerçek MySQL'e dokunmaz, Docker gerektirmez.** `./mvnw test` Docker
  kapalıyken bile yeşildir. Yalnızca MySQL'e özgü davranışı sınayan entegrasyon testleri
  (`*IT`, 15 adet) Failsafe ile opt-in `it` profilinde, Testcontainers üzerinden gerçek MySQL 8.4'e
  bağlanır; **Docker bedeli yalnızca bu katmana aittir.** Böylece günlük döngü Docker'sız ve hızlı
  kalır (`./mvnw test`), entegrasyon güveni ayrı ve açık bir komutla alınır (`./mvnw verify -Pit`).
- **Entegrasyon katmanı neden H2 değil, gerçek MySQL.** Şema ve eşzamanlılık MySQL'e özgü
  davranışlara dayanır (`utf8mb4_bin` collation, `FOR UPDATE` gap lock semantiği, `ON DUPLICATE KEY`
  kilit davranışı). H2 bunları taklit etmez. Bu davranışları H2 üzerinde sınarsak testler geçer ama
  gerçek MySQL'deki davranışı hiç doğrulamamış oluruz — yanıltıcı bir güven: test yeşil yanar, oysa
  üretimdeki MySQL farklı davranabilir. Sınamamız gereken şey tam da bu MySQL davranışı olduğundan,
  entegrasyon testleri gerçek MySQL 8.4 üzerinde koşar.
- **Cevap anahtarları analitiktir.** Haversine testlerinde beklenen değer koddan
  *türetilmez* (aksi hâlde test, sınadığı kodu kendisiyle doğrulayan döngüsel bir kontrol olurdu);
  kalemle hesaplanır (ör. aynı boylamda 0,0009° fark
  ≈ 100,0754 m). Sınır testi de yarıçapı koddan okuyup kullanmaz, analitik mesafeyi kullanır.
- **Risk-temelli kapsam.** Kutup/tarih çizgisi gibi coğrafi edge case'ler için ayrı test yok:
  alan İstanbul, formül zaten orada kararlı; test bütçesi gerçek risklere (eşzamanlılık,
  sırasız veri, makul-zaman) ayrıldı. Eşzamanlılık korumaları gerçek MySQL üzerinde, koruma
  kaldırılınca *kırılan* testlerle çivilendi (`CourierTrackingServiceConcurrencyIT`).

---

## Üretim ölçeğinde nasıl evrilir

Aşağıdakiler bu teslimde bilinçle yapılmadı; sistem büyüdüğünde gidilecek yollar bunlar.

**Konum kabulü.** Şu an konumlar doğrudan controller'a gelip o anda işleniyor. Çok yüksek
hacimde, veriyi *kabul etmeyi* *işlemekten* ayırmak gerekir: gelen konumlar önce bir mesaj
kuyruğuna (ör. Kafka) yazılır, işleme onları kuyruktan tüketir. Böylece ani yükler kuyrukta
tamponlanır ve yavaş işleme, hızlı kabulü tıkamaz. Controller'ın bugün yaptığı işi bir kuyruk
consumer'ı yapar; iş mantığı değişmez.

**Toplam mesafe.** Bugün toplamı her kurye için tek bir satırda biriktiriyoruz. Çok yüksek
yazma hacminde bu tek satır darboğaza döner. Alternatif, sayacı hiç tutmamaktır: her konumu
değişmez bir satır olarak saklayıp toplamı gerektiğinde hesaplamak (ya da arka planda ayrı bir
okuma tablosuna işlemek). Paylaşılan bir sayaç kalmadığı için eşzamanlı yazımların birbirini
ezme sorunu tümden ortadan kalkar. Bedeli daha karmaşık bir okuma yolu olduğundan bu ölçekte
yapmadık.

**Eşzamanlılık.** Bu çözümde ilk-satır çakışması upsert ile yapısal olarak imkânsız olduğundan
bunun için bir yeniden-deneme mekanizmasına gerek yoktur. Geriye yalnızca tek teorik durum kalır:
çok yüksek çekişmede bir isteğin kilit beklemesi 5 sn'yi aşarsa MySQL isteği iptal eder ve istek
`503 + Retry-After` alır. Bu, bu teslimin yükünde gerçekleşmez (20 istek ~0,1 sn'de biter);
gerçekleşse bile davranış doğrudur — `503 + Retry-After`, tekrar denemeyi client'a devreden standart
HTTP contract'ıdır. Çok daha büyük bir ölçekte kilit-aşımı sıklaşırsa, bu `503`'leri client'a hiç
göstermeden sunucuda yutan ince bir yeniden-deneme katmanı eklenebilir — eksik bir gereklilik değil,
o ölçeğe özgü bir iyileştirme. Bu teslimde o ölçek olmadığı için yok.

**Şema yönetimi.** Migration şu an uygulamanın içinde; veritabanının tek sahibi bu uygulama
olduğundan doğrusu da bu. Çok servisli, ayrı bir platform ekibinin olduğu ortamda şema
değişiklikleri ayrı bir dağıtım adımına taşınır ve uygulamaya yalnız veri yazma yetkisi (şemayı
değiştirme değil) verilir.

**Denetim ve gözlemlenebilirlik.** Bugün yalnız client'ın beyan ettiği zamanı (`event_time`)
saklıyoruz. Üretimde, sunucunun veriyi *aldığı* an için ikinci bir kolon da eklenebilir; bu,
teşhis, yeniden oynatma ve saat-kayması analizinde işe yarar. Ayrıca elenen sırasız noktalar ve
giriş sayıları için metrik toplanır.

**Operasyon.** Kuryelerin canlılığı için son-görülme (heartbeat) takibi; ve kişisel konum
verisi söz konusu olduğundan bir KVKK çerçevesi: saklama süresi, erişim denetimi, gerektiğinde
anonimleştirme.

**Kalite araçları.** Ekip büyüyüp kodun sahipliği paylaşılınca, derleme hattına otomatik
denetimler eklenir: `@NullMarked` anotasyonlarını derleme anında zorlayan NullAway, hata ve
biçim denetçileri (SpotBugs, Checkstyle) ve bir test-kapsamı eşiği (JaCoCo).
