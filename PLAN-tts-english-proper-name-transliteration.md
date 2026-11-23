# Kế hoạch triển khai: Phiên âm sẵn tên riêng & từ tiếng Anh cho Google TTS Tiếng Việt

> **Mục tiêu**: Giữ nguyên 1 giọng Google TTS Tiếng Việt (`vi-VN`), **không tự động đổi sang giọng tiếng Anh**, tránh mọi độ trễ chuyển giọng hoặc khựng câu; đồng thời nạp sẵn bộ từ điển tên nhân vật, họ, địa danh và từ tiếng Anh phổ biến phủ rộng để TTS đọc tự nhiên, dễ nghe và chính xác nhất.

---

## 1. Phân tích hiện trạng & Phạm vi

- **Luồng TTS hiện tại**:
  - `PlayerRuntimeControllerObservers.kt` (`observeSubtitleTts`) đẩy cue text từ `SubtitleCueTracker` vào `SubtitleTtsCoordinator.onSubtitleCueChanged(...)`.
  - `SubtitleTtsCueQueueState.kt` làm sạch text bằng `SubtitleTtsTextCleaner.clean(...)` để chống đọc lặp cue.
  - `SubtitleTtsCoordinator.kt` tính tốc độ đọc qua `SubtitleTtsTimingCalculator` rồi gọi `GoogleOfflineTtsEngine.speak(...)`.
  - `app/src/main/assets/` đã sẵn sàng để chứa file dữ liệu từ điển offline.

> [!IMPORTANT]
> **Nguyên tắc độ trễ bằng 0 (Zero-Latency)**:
> - Tuyệt đối **không** gọi `tts.voice = enVoice` hay chèn SSML `<lang>` khi đọc phụ đề tiếng Việt.
> - Toàn bộ từ điển phiên âm được load **1 lần** từ `assets/` vào `HashMap<String, String>` trong RAM (tra cứu $O(1)$ mất `< 0.05 ms` mỗi cue).

> [!WARNING]
> **Rủi ro biến nhầm từ tiếng Việt không dấu / tên người Việt**:
> - Trong phụ đề tiếng Việt, từ đầu câu luôn viết hoa chữ cái đầu (ví dụ: *Anh, Em, Con, Khi, Nhưng, Trong, Minh, Nam, Lan, Mai...*).
> - Nếu chỉ thấy chữ viết hoa không dấu mà phiên âm bừa sẽ làm hỏng câu tiếng Việt. Bắt buộc phải có **tập từ loại trừ tiếng Việt** và điều kiện nhận diện hình thái chữ cái tiếng Anh trước khi fallback sang rule-based.

---

## 2. Chuỗi phụ thuộc (Dependency Chain)

1. **Dữ liệu Assets** (`app/src/main/assets/tts_en_vi_dict.tsv`)
   └──▶ 2. **Engine phiên âm** (`EnglishToVietnamesePhonetics.kt` + `TtsPhoneticDictionaryLoader.kt` trong `com.nuvio.tv.core.tts`)
        └──▶ 3. **Điều phối TTS** (`SubtitleTtsCoordinator.kt` — áp dụng trước khi tính tốc độ & gọi `engine.speak`)
             └──▶ 4. **Cấu hình & UI** (`PlayerSettingsDataStore.kt`, `SubtitleTtsModels.kt`, `PlaybackSettingsViewModel.kt`, `PlaybackAudioSettings.kt`, `SubtitleTtsOverlay.kt`, `strings.xml`)
                  └──▶ 5. **Kiểm thử** (`EnglishToVietnamesePhoneticsTest.kt`, cập nhật `SubtitleTtsTextCleanerTest.kt`)

---

## 3. Phân rã triển khai theo từng Phase

### Phase 1: Xây dựng bộ từ điển phủ rộng mọi thể loại phim trong `assets/`
- **File mới**: `app/src/main/assets/tts_en_vi_dict.tsv`
- **Cấu trúc gọn nhẹ**: mỗi dòng `từ_gốc<TAB>âm_việt` (UTF-8, bỏ qua dòng trống và `# comment`), dung lượng gọn ~200–350 KB, nạp vào `HashMap` trong RAM khi khởi động TTS.
- **Phân nhóm dữ liệu phủ rộng theo từng thể loại phim**:

  1. **Siêu anh hùng, Hành động & Điệp viên (Superhero / Action / Spy)**:
     - *Marvel / Avengers*: `Thanos -> Tha nốt`, `Tony Stark -> Tô ni Xtác`, `Iron Man -> Ai ân Men`, `Peter Parker -> Pi tơ Pác cơ`, `Spider-Man -> Xpai đơ Men`, `Bruce Banner -> Brút Ban nơ`, `Hulk -> Húc`, `Thor -> Tho`, `Loki -> Lô ki`, `Natasha -> Na ta sa`, `Romanoff -> Rô ma nốp`, `Black Widow -> Blách Uy đâu`, `Steve Rogers -> Xtiu Rô giơ`, `Captain America -> Cáp tơn A me ri ca`, `Bucky Barnes -> Bác ki Ban`, `Wanda Maximoff -> Oan đa Mắc xi mốp`, `Scarlet Witch -> Xcát lét Uých`, `Stephen Strange -> Xti vần Xtơ ren`, `Doctor Strange -> Đốc tơ Xtơ ren`, `Wade Wilson -> Uết Uyu xơn`, `Deadpool -> Đét pun`, `Wolverine -> Uôn vơ rin`, `Logan -> Lô gơn`, `Magneto -> Mác nê tô`, `Hawkeye -> Hóc ai`, `Nick Fury -> Ních Phiu ri`, `Groot -> Grút`, `Rocket -> Róc két`...
     - *DC / Justice League*: `Bruce Wayne -> Brút Uên`, `Batman -> Bát men`, `Clark Kent -> Clác Ken`, `Superman -> Xu pơ men`, `Diana Prince -> Đai a na Prin`, `Wonder Woman -> Oăn đơ U mừn`, `Barry Allen -> Ba ri A lừn`, `Flash -> Phlét`, `Arthur Curry -> A thơ Cơ ri`, `Aquaman -> A cua men`, `Joker -> Giô cơ`, `Harley Quinn -> Hác li Cuyn`, `Lex Luthor -> Lếch Lu tho`, `Alfred -> An phrét`, `Gordon -> Go đần`, `Riddler -> Rít lơ`, `Penguin -> Pen guyn`...
     - *Hành động / Tốc độ / Điệp viên*: `John Wick -> Giôn Uých`, `Winston -> Uyn xtơn`, `Charon -> Ca rơn`, `Ethan Hunt -> Y thơn Hăn`, `James Bond -> Giêm Bon`, `Dom Toretto -> Đôm Tô rét tô`, `Brian O'Conner -> Brai ân O Co nơ`, `Letty -> Lét ti`, `Hobbs -> Hóp`, `Shaw -> So`, `Jason Bourne -> Giây xơn Bon`, `Morpheus -> Mo phi út`, `Trinity -> Tri ni ti`...

  2. **Khoa học viễn tưởng & Vũ trụ (Sci-Fi / Space / Cyberpunk)**:
     - *Star Wars*: `Skywalker -> Xcai oóc cơ`, `Luke -> Lục`, `Leia -> Lê a`, `Anakin -> A na kin`, `Darth Vader -> Đát Vây đơ`, `Obi-Wan -> Ô bi Oan`, `Kenobi -> Kê nô bi`, `Yoda -> Yô đa`, `Han Solo -> Han Xô lô`, `Chewbacca -> Chui ba ca`, `Palpatine -> Pan pa tin`, `Boba Fett -> Bô ba Phét`, `Ahsoka -> A sô ca`, `Grogu -> Grô gu`, `Mandalorian -> Man đa lô ri ân`, `Jedi -> Giê đai`, `Sith -> Xít`...
     - *Dune (Xứ Cát)*: `Paul Atreides -> Pôn A trây đít`, `Atreides -> A trây đít`, `Chani -> Cha ni`, `Jessica -> Giê xi ca`, `Harkonnen -> Hác côn nừn`, `Feyd-Rautha -> Phây Rô tha`, `Duncan Idaho -> Đơn kừn Ai đa hô`, `Gurney Halleck -> Gơ ni Ha lếch`, `Stilgar -> Xtin ga`, `Fremen -> Phrê mừn`, `Arrakis -> A ra kít`...
     - *Cyberpunk & Robot*: `Neo -> Nê ô`, `Spock -> Xpóc`, `Kirk -> Cớc`, `Picard -> Pi các`, `Ripley -> Ríp li`, `Sarah Connor -> Sa ra Co nơ`, `John Connor -> Giôn Co nơ`, `Terminator -> Tơ mi nây tơ`, `Deckard -> Đéc các`, `Optimus Prime -> Óp ti mút Praim`, `Megatron -> Mê ga tron`, `Bumblebee -> Băm bồ bi`...

  3. **Kỳ ảo, Thần thoại & Trung cổ (Fantasy / Medieval / Magic)**:
     - *Harry Potter*: `Harry Potter -> Ha ri Pót tơ`, `Ron Weasley -> Rôn Uýt xli`, `Hermione Granger -> Hơ mai ơ ni Grên giơ`, `Dumbledore -> Đăm bồ đo`, `Severus Snape -> Xê vê rút Xnếp`, `Voldemort -> Vôn đơ mo`, `Sirius Black -> Xi ri út Blách`, `Draco Malfoy -> Đra cô Man phoi`, `Hagrid -> Hác rít`, `McGonagall -> Mác Gô na gơn`, `Gryffindor -> Gríp phin đo`, `Slytherin -> Xlít thơ rin`, `Hogwarts -> Hóc oót`...
     - *Lord of the Rings / The Hobbit*: `Frodo Baggins -> Phrô đô Ba ghin`, `Samwise -> Xam oai`, `Gandalf -> Gan đáp`, `Aragorn -> A ra gon`, `Legolas -> Lê gô lát`, `Gimli -> Gim li`, `Boromir -> Bô rô mia`, `Gollum -> Gô lùm`, `Sauron -> Xau ron`, `Saruman -> Sa ru man`, `Bilbo -> Bin bô`, `Mordor -> Mo đo`, `Gondor -> Gôn đo`, `Rivendell -> Ri vần đen`...
     - *Game of Thrones / House of the Dragon*: `Jon Snow -> Giôn Xnô`, `Daenerys Targaryen -> Đa ne rít Tác ga ri ân`, `Tyrion Lannister -> Ti ri ân Lan ni xtơ`, `Cersei -> Xơ xi`, `Jaime -> Giây mi`, `Arya Stark -> A ri a Xtác`, `Sansa -> Xan xa`, `Bran -> Bran`, `Ned Stark -> Nét Xtác`, `Rhaenyra -> Rây ni ra`, `Daemon -> Đê mừn`, `Westeros -> Oét xtơ rốt`, `Winterfell -> Uyn tơ phel`...
     - *The Witcher*: `Geralt -> Gie ran`, `Rivia -> Ri vi a`, `Yennefer -> Yên nê phơ`, `Ciri -> Xi ri`, `Jaskier -> Giát ski ơ`, `Triss -> Trít`...

  4. **Tội phạm, Băng đảng & Hình sự (Crime / Mafia / Police / Mystery)**:
     - *Bố Già (The Godfather) & Mafia*: `Corleone -> Co le ô nê`, `Vito -> Vi tô`, `Michael -> Mai cồ`, `Sonny -> Xon ni`, `Fredo -> Phrê đô`, `Tom Hagen -> Tôm Hây gừn`, `Don -> Đôn`, `Capone -> Ca pôn`...
     - *Breaking Bad & Better Call Saul*: `Walter White -> Oan tơ Oait`, `Heisenberg -> Hai zừn bớc`, `Jesse Pinkman -> Giét xi Pinh mừn`, `Saul Goodman -> Xôn Gút mừn`, `Gus Fring -> Gớt Phring`, `Mike Ehrmantraut -> Mai Ơ mừn troót`, `Hank Schrader -> Hanh Xcrây đơ`, `Salamanca -> Xa la man ca`...
     - *Peaky Blinders*: `Thomas Shelby -> Tô mát Sen bi`, `Arthur Shelby -> A thơ Sen bi`, `Polly -> Pô li`, `John Shelby -> Giôn Sen bi`, `Alfie Solomons -> An phi Xô lô mừn`...
     - *Sherlock Holmes & Trinh thám*: `Sherlock Holmes -> Sơ lốc Hổm`, `John Watson -> Giôn Oát xơn`, `Moriarty -> Mô ri a ti`, `Mycroft -> Mai cróp`, `Irene Adler -> Ai rin Át lơ`, `Poirot -> Poa rô`...

  5. **Kinh dị & Siêu nhiên (Horror / Supernatural / Thriller)**:
     - *The Conjuring & Ma quỷ*: `Ed Warren -> Ét Oa rừn`, `Lorraine Warren -> Lo rên Oa rừn`, `Annabelle -> An na ben`, `Valak -> Va lắc`, `Pennywise -> Pên ni oai`, `Freddy Krueger -> Phrét đi Cru gơ`, `Jason Voorhees -> Giây xơn Vo hi`, `Michael Myers -> Mai cồ Mai ơ`, `Chucky -> Chắc ki`...
     - *Stranger Things*: `Eleven -> Y le vừn`, `Mike Wheeler -> Mai Uy lơ`, `Dustin -> Đát xtin`, `Lucas -> Lu cát`, `Will Byers -> Uy Ba yơ`, `Jim Hopper -> Gim Hóp pơ`, `Joyce -> Gioi`, `Steve Harrington -> Xtiu Ha ring tơn`, `Vecna -> Véc na`, `Demogorgon -> Đê mô go gơn`, `Hawkins -> Hó kin`...
     - *Supernatural*: `Sam Winchester -> Xam Uyn che xtơ`, `Dean Winchester -> Đin Uyn che xtơ`, `Castiel -> Cát ti en`, `Crowley -> Crô li`, `Lucifer -> Lu xi phơ`...

  6. **Quân sự, Chiến tranh & Lịch sử (Military / Historical / Espionage)**:
     - *Quân hàm / chức danh*: `Captain -> Cáp tơn`, `Lieutenant -> Lu ten nừn`, `Sergeant -> Sa giừn`, `Corporal -> Co pơ rồ`, `Major -> Mây giơ`, `Colonel -> Cớ nơ`, `General -> Gie nơ rồ`, `Commander -> Cơ man đơ`, `Admiral -> Át mi rồ`, `Private -> Prai vịt`, `Agent -> Ê giừn`, `Chief -> Chíp`...
     - *Nhân vật lịch sử & phim tài liệu/chiến tranh*: `Oppenheimer -> Óp pừn hai mơ`, `Einstein -> Anh xtanh`, `Churchill -> Chớc chin`, `Roosevelt -> Rô giơ ven`, `Stalin -> Xta lin`, `Hitler -> Hít le`, `Napoleon -> Na pô lê ông`, `Caesar -> Xê da`, `Alexander -> A lếch zan đơ`, `Ryan -> Rai ân`, `Miller -> Min lơ`...

  7. **Tên Anime & Phim Châu Á giữ phiên âm Latin (Romaji / Pinyin)**:
     - *Anime nổi tiếng*: `Naruto -> Na ru tô`, `Sasuke -> Xa xu kê`, `Sakura -> Xa cu ra`, `Kakashi -> Ca ca si`, `Itachi -> Y ta chi`, `Luffy -> Lu phi`, `Zoro -> Dô rô`, `Nami -> Na mi`, `Sanji -> Xan gi`, `Chopper -> Chóp pơ`, `Goku -> Gô cu`, `Vegeta -> Vê giê ta`, `Tanjiro -> Tan gi rô`, `Nezuko -> Nê du cô`, `Zenitsu -> Dê nít xư`, `Inosuke -> Y nô xư kê`, `Eren -> Ê rừn`, `Levi -> Li vai`, `Mikasa -> Mi ca xa`, `Armin -> A min`, `Gojo -> Gô giô`, `Sukuna -> Xư cu na`, `Yuji -> Y u gi`, `Megumi -> Mê gu mi`, `Nobara -> Nô ba ra`, `Conan -> Cô nan`, `Shinichi -> Si ni chi`, `Ran -> Ran`, `Saitama -> Xai ta ma`, `Light Yagami -> Lait Y a ga mi`, `Ryuk -> Ri úc`...
     - *Họ & tên Hàn/Hoa viết Latin*: `Kim -> Kim`, `Lee -> Li`, `Park -> Pác`, `Choi -> Choi`, `Jung -> Giâng`, `Kang -> Cang`, `Song -> Xong`, `Wang -> Oang`, `Zhang -> Giang`, `Chen -> Chen`, `Liu -> Li u`, `Yang -> Giang`, `Huang -> Hoang`...

  8. **Địa danh & Thế giới giả tưởng (Real & Fictional Worlds)**:
     - *Địa danh giả tưởng*: `Gotham -> Go tham`, `Metropolis -> Mê trô pô lít`, `Wakanda -> Oa can đa`, `Asgard -> Át gát`, `Tatooine -> Ta tu in`, `Coruscant -> Cô ru xừn`, `Pandora -> Pan đô ra`, `Silent Hill -> Xai lừn Hin`, `Raccoon City -> Ra cun Xi ti`...
     - *Địa danh thực*: `New York -> Niu Yoóc`, `Los Angeles -> Lót An giơ lét`, `San Francisco -> Xan Phran xít cô`, `Las Vegas -> Lát Vê gát`, `Washington -> Oa sinh tơn`, `Chicago -> Chi ca gô`, `London -> Luân Đôn`, `Paris -> Pa ri`, `Tokyo -> Tô ky ô`, `Berlin -> Béc lin`, `Moscow -> Mát xcơ va`, `Hollywood -> Hô ly út`, `Silicon Valley -> Xi li côn Va li`, `California -> Ca li phoóc ni a`, `Texas -> Tếch xát`, `Florida -> Phlo ri đa`, `Miami -> Mai a mi`...

  9. **Từ tiếng Anh giao tiếp, thuật ngữ, công nghệ & viết tắt**:
     - *Giao tiếp*: `hello -> hê lô`, `hi -> hai`, `ok -> ô kê`, `okay -> ô kê`, `sorry -> so ri`, `bye -> bai`, `goodbye -> gút bai`, `thank you -> thanh kiu`, `thanks -> thanh`, `please -> plít`, `welcome -> oen căm`, `yes -> dét`, `no -> nô`, `yeah -> de`...
     - *Thuật ngữ / Đời sống*: `boss -> bót`, `server -> xơ vơ`, `file -> phai`, `email -> i meo`, `wifi -> oai phai`, `police -> pô lít`, `doctor -> đốc tơ`, `google -> gu gồ`, `iphone -> ai phôn`, `laptop -> láp tóp`, `camera -> ca mê ra`, `video -> vi đê ô`, `team -> tim`, `game -> gêm`, `plan -> plen`, `check -> chếch`, `test -> tét`, `system -> xít tơm`, `data -> đa ta`, `level -> le vừn`, `code -> cốt`, `link -> linh`, `online -> on lai`, `offline -> óp lai`...
     - *Viết tắt*: `FBI -> ép bi ai`, `CIA -> xi ai ây`, `NSA -> en ét ây`, `SWAT -> xờ oát`, `VIP -> víp`, `CEO -> xi i ô`, `DNA -> đi en ây`, `USA -> iu ét ây`, `UK -> iu cây`, `TV -> ti vi`, `AI -> ây ai`, `ID -> ai đi`, `GPS -> gi pi ét`, `UFO -> iu ép ô`...

### Phase 2: Bộ xử lý phiên âm `EnglishToVietnamesePhonetics`
- **File mới**:
  - `app/src/main/java/com/nuvio/tv/core/tts/TtsPhoneticDictionary.kt`: nạp bất đồng bộ/lazy `tts_en_vi_dict.tsv` qua `AssetManager` (fallback an toàn nếu chạy unit test không có Android `Context` bằng cách hỗ trợ inject `InputStream` / danh sách từ điển lõi).
  - `app/src/main/java/com/nuvio/tv/core/tts/EnglishToVietnameseTransliterator.kt`:
    1. **Bảo vệ dữ liệu đầu vào**:
       - Giữ nguyên URL/email (`http...`, `www...`), số thuần túy, ký tự có dấu tiếng Việt (`ă, â, đ, ê, ô, ơ, ư`, thanh điệu).
       - Giữ nguyên các từ nằm trong **bộ loại trừ tiếng Việt không dấu** (`anh, em, con, cho, khi, nhung, trong, nha, minh, nam, lan, mai, hai, hoa, huong, linh, trang, tuan, hung, dung...`) — trừ khi có dấu hiệu rõ là tên tiếng Anh hoặc nằm trong nhóm ngoại lệ an toàn.
    2. **Thứ tự ưu tiên phiên âm**:
       - **Bước 1 — Cụm từ 2–3 từ**: Khớp các cụm như `New York`, `Los Angeles`, `thank you`, `San Diego` trước khi tách từng từ.
       - **Bước 2 — Sở hữu cách & Hậu tố**: Xử lý `John's` → `Giôn` (hoặc `của Giôn` / `Giôn-x`), `Stark's` → `Xtác`.
       - **Bước 3 — Tra từ điển `HashMap` O(1)**: Nếu khớp từ điển (không phân biệt hoa/thường với tên riêng/từ trong từ điển) → thay bằng âm tiếng Việt chuẩn.
       - **Bước 4 — Chữ viết tắt toàn chữ HOA (2–5 ký tự)**: Như `KGB`, `MIT`, `NYPD` chưa có trong từ điển → đọc từng chữ cái theo âm Việt (`ca gi bi`, `em ai ti`...).
       - **Bước 5 — Quy tắc phiên âm dự phòng (Rule-based)** cho **tên riêng lạ / tên fantasy** (như `Thanos`, `Krypton`, `Voldemort`, `Mandalorian`):
         - Chỉ kích hoạt cho token thỏa điều kiện: viết hoa chữ cái đầu (hoặc toàn chữ thường nhưng chứa ký tự/tổ hợp đặc trưng tiếng Anh như `j, w, z, f, sh, th, ck, str, br, cr, dr, gr, pr, tr, tion, sion`) và **không** nằm trong danh sách từ tiếng Việt loại trừ.
         - Quy đổi âm tiết tiếng Anh → vần tiếng Việt dễ đọc (`sh` → `s`, `th` → `th`/`đ`, `ch` → `ch`, `ph` → `ph`, `ee/ea` → `i`, `oo` → `u`, `ck` → `c`, `x` cuối → `x`...).
       - Bảo toàn dấu câu đi kèm (dấu phẩy, chấm, hỏi, ngoặc kép).

### Phase 3: Tích hợp vào `SubtitleTtsCoordinator`
- **File chỉnh sửa**:
  - [SubtitleTtsCoordinator.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/tts/SubtitleTtsCoordinator.kt):
    - Khởi tạo/warm-up từ điển nền khi `startSession()` hoặc khởi tạo `SubtitleTtsCoordinator` (trên `Dispatchers.IO`, không chặn Main thread).
    - Tại `startSpeakingCue(...)`, `handlePlaybackCompleted(...)`, và `previewVoice(...)`:
      - Nếu `config.transliterateEnglishNames` đang bật **và** ngôn ngữ TTS hiện tại là tiếng Việt (`preferredLanguageCode` bắt đầu bằng `vi`, hoặc giọng đang chọn là `vi-`):
        - Chạy `transliterator.transliterate(cleanText)` để tạo `spokenCleanText` rồi bọc dấu kết câu.
        - Tính `targetSpeed` và `estimatedSpeechMs` dựa trên `spokenCleanText` (để Google TTS auto-speed ước lượng đúng số âm tiết tiếng Việt thực tế sẽ đọc, ví dụ `Christopher` → `Crít xtô phơ` là 4 âm tiết thay vì 1 từ).
    - Giữ nguyên `cleanText` gốc bên trong `SubtitleTtsCueQueueState` để logic chống lặp cue hoạt động chính xác ngay cả khi bật/tắt tùy chọn giữa chừng.
    - Cập nhật `GoogleOfflineTtsEngine` giữ nguyên hành vi **không tự đổi sang giọng tiếng Anh**.

### Phase 4: Lưu cấu hình & Tùy chọn UI
- **File chỉnh sửa**:
  - [PlayerSettingsDataStore.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt): thêm `ttsTransliterateEnglishNames: Boolean = true` + preference key `tts_transliterate_english_names` + setter `setTtsTransliterateEnglishNames(enabled: Boolean)`.
  - [SubtitleTtsModels.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/tts/SubtitleTtsModels.kt): thêm `transliterateEnglishNames: Boolean = true` vào `SubtitleTtsConfig` và map trong `toSubtitleTtsConfig()`.
  - [PlaybackSettingsViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsViewModel.kt): thêm `setTtsTransliterateEnglishNames(...)`.
  - [PlaybackAudioSettings.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackAudioSettings.kt) & [SubtitleTtsOverlay.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleTtsOverlay.kt): thêm công tắc bật/tắt **"Phiên âm tên riêng & từ tiếng Anh"** trong cả Cài đặt phát lại và Overlay TTS trong trình phát.
  - `app/src/main/res/values/strings.xml` & `app/src/main/res/values-vi/strings.xml`: thêm chuỗi mô tả song ngữ EN/VI.

### Phase 5: Kế hoạch kiểm thử (Verification Plan)
1. **Unit Test tự động**:
   - Tạo `app/src/test/java/com/nuvio/tv/core/tts/EnglishToVietnameseTransliteratorTest.kt` kiểm tra:
     - Tên riêng bất quy tắc (`Michael`, `Sean`, `Chloe`, `Rachel`, `Christopher`, `Stephen`, `George`).
     - Cụm địa danh (`New York`, `Los Angeles`, `San Francisco`, `Washington`).
     - Tên fantasy/lạ qua rule-based (`Thanos`, `Gotham`, `Stark`, `Krypton`).
     - Từ tiếng Anh phổ biến & viết tắt (`hello`, `FBI`, `CIA`, `VIP`, `John's`).
     - **An toàn tiếng Việt**: không biến nhầm các từ viết hoa đầu câu tiếng Việt (`Anh`, `Em`, `Khi`, `Nhưng`, `Trong`, `Minh`, `Nam`, `Lan`).
     - Tải thực tế file `app/src/main/assets/tts_en_vi_dict.tsv` trong unit test để đảm bảo không lỗi cú pháp, không dòng hỏng.
   - Chạy `.\gradlew.bat :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests "com.nuvio.tv.core.tts.*"`.
2. **Kiểm tra thủ công trên Android TV**:
   - Bật "Đọc phụ đề thành tiếng" với giọng `vi-VN` offline.
   - Thử câu mẫu & xem phim có tên nhân vật Âu–Mỹ + từ tiếng Anh xen kẽ, kiểm tra: không trễ, không đổi giọng sang tiếng Anh, tên nhân vật đọc rõ ràng dễ hiểu.
