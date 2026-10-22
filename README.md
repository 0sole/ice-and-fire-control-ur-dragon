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
- **Ejderha menüsü (v0.2.0):** Düdük envanterdeyken **V** (Ayarlar → Kontroller'den değiştirilebilir) ya da düdüğe sağ tık. Iron's Spells tarzı çark: önce ejderha, sonra komut.
  - Otur, Takip et, Serbest, Yanıma gel, İsim ver
  - Kayıp ejderhada **Geri getir** (30 dk bekleme süresi)
  - Tuşu basılı tutup ejderhanın üstünde bırakmak da seçer. Sağ tık / Esc geri gider.

Ayarlar: `config/controlurdragon-common.toml`

## Yol haritası

1. ~~Takip, silinme logu, kill() düzeltmesi, geri getirme komutu~~
2. ~~Düdük ve çark menü: otur, takip, serbest, yanıma gel, isim ver, geri getir~~
3. Tek hedefe saldır / seçili grupla saldır
4. Ejderha başına yapı yıkma aç/kapat (`GriefBreakBlockEvent`) ve davranış ayarları
5. Diğer modlar için destek (Dragon Care, MineColonies, MCA vb.)

## Geliştirme ortamı (IntelliJ IDEA)

1. Proje JDK'sı ve **Gradle JVM** 21 olsun (*Settings → Build Tools → Gradle*).
2. `libs/` klasörüne Ice and Fire ve kütüphane jar'larını koy (bkz. `libs/README.md`).
3. Gradle panelinde yenile. Senkron bitince sağ üstte **Client** ve **Server** çıkar.
4. Mod jar'ı: Gradle panelinde `build → build` ya da `gradlew build`. Çıktı `build/libs/` altında.
