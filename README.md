# IaF: Control Ur Dragon

Ice and Fire CE için ejderha kontrol, koruma ve kurtarma eklentisi.
Minecraft 1.21.1 · NeoForge 21.1 · Ice and Fire CE 2.1.4

## Şu an çalışanlar (v0.1.0)

- **Takip ve yedek:** Sahibi olan her ejderhanın durumu ve tam verisi 30 saniyede bir dünya kaydına yazılır (`data/controlurdragon_records.dat`).
- **Silinme dedektörü:** Evcil bir ejderha ölmeden dünyadan silinirse loga uyarı ve tam stack trace yazılır. Hangi modun sildiği buradan görülür. Dragon Horn'a alınan ejderhalar "eşyada" sayılır, kayıp sayılmaz.
- **`kill()` düzeltmesi:** Ice and Fire'da `kill()` canlı ejderhayı iz bırakmadan siliyordu. Artık normal şekilde ölüyor; ceset ve ölüm mesajı oluşuyor. Kapatmak için ayarlardaki `fixSilentKill`.
- **Komutlar:**
  - `/cud list` → ejderhaların ve durumları (hayatta, yüklü değil, eşyada, KAYIP, ölü)
  - `/cud info <uuid>` → ayrıntılar; kayıpsa hangi kodun sildiği
  - `/cud restore <uuid>` → kayıp ejderhayı son yedekten yanına getirir. Ejderha dünyada hâlâ varsa kopya oluşturmaz. Oyuncular için 30 dakika bekleme süresi var, operatörler beklemez.
- **Ejderha Düdüğü:** Şimdilik sağ tıklayınca listeyi gösterir. Sonraki adımda tuşlar ve menü buna bağlanacak.

Ayarlar: `config/controlurdragon-common.toml`

## Yol haritası

1. ~~Takip, silinme logu, kill() düzeltmesi, geri getirme komutu~~
2. Düdük envanterdeyken tuşlar ve menü: ejderha seç → komut (otur, takip, toplan, bekle)
3. Tek hedefe saldır / seçili grupla saldır
4. Ejderha başına yapı yıkma aç/kapat (`GriefBreakBlockEvent`) ve davranış ayarları
5. Diğer modlar için destek (Dragon Care, MineColonies, MCA vb.)

## Geliştirme ortamı (IntelliJ IDEA)

1. JDK 21 kurulu olsun (IDEA: *File → Project Structure → SDK → Download JDK → 21*).
2. *File → Open* ile bu klasörü aç, "Trust project" de. Gradle senkronu otomatik başlar. İlk seferde Minecraft ve NeoForge indirildiği için birkaç dakika sürer.
3. *Settings → Build, Execution, Deployment → Build Tools → Gradle* altında **Gradle JVM = 21** olsun.
4. Senkron bitince sağ üstteki çalıştırma listesinde **Client** ve **Server** görünür. **Client** ile oyunu Ice and Fire yüklü olarak açarsın.
5. Mod jar'ı: Gradle panelinde `build → build` ya da terminalde `gradlew build`. Çıktı `build/libs/` altında.

`libs/` klasöründeki jar'lar git'e eklenmez, bkz. `libs/README.md`.

## Projeyi IDEA'ya almak

IDEA'da *File → New → Project from Version Control* → URL: `https://github.com/0sole/ice-and-fire-control-ur-dragon`.
Klonladıktan sonra `libs/` klasörüne yukarıdaki jar'ları kopyala.
