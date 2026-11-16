# AuroraHub · Live Wallpaper Hub

Hub nativo (Kotlin) de **live wallpapers** inspirados em **OxygenOS**, **iOS** e **ColorOS**,
feito para o **Samsung Galaxy S10 / One UI 4.1** (Android 12, API 31, tela **1440 × 3040 px, 19:9**).

O requisito central é atendido por construção: **a animação cobre 100% da tela, sem letterboxing**,
tanto para vídeos MP4 quanto para animações procedurais.

---

## 1. Por que não aparecem bordas pretas

Entregar o `Surface` do `SurfaceHolder` direto ao `MediaPlayer` deixa o enquadramento na mão do
compositor — que normalmente faz *center-inside*:

```
escala = min(1440/1920, 3040/1080) = 0,75   ->  imagem 1440 x 810
bordas = (3040 - 810) / 2 = 1115 px de PRETO em cima e embaixo
```

O AuroraHub decodifica para um **SurfaceTexture** (textura `GL_TEXTURE_EXTERNAL_OES`) e resolve o
enquadramento no fragment shader, com a matemática de `ScaleMath.kt`:

```
escala = max(1440/1920, 3040/1080) = 2,8148  ->  quadro 5404,4 x 3040
corte  = (5404,4 - 1440) / 2 = 1982,2 px por lado (horizontal)
corte vertical = 0 px      |      bordas = 0 px      |      cobertura = 100%
uvScale = (0,26645 ; 1,0)  ->  uv' = (uv - 0,5) * uvScale + 0,5 + paralaxe
```

`MediaPlayer.setVideoScalingMode(SCALE_TO_FIT_WITH_CROPPING)` também é chamado, mas ele só é
honrado em alguns caminhos de SurfaceView — **a garantia vem do shader**, não do framework.

O paralaxe (`onOffsetsChanged`) usa apenas a folga criada pelo crop, então o swipe entre páginas
da home nunca revela a borda do vídeo.

---

## 2. Estrutura de pastas

```
AuroraHub/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/
        │   │   ├── shaders/                 # animações procedurais (GLSL ES 1.0)
        │   │   │   ├── vertex.vert  common.glsl  main.glsl
        │   │   │   ├── video.frag   blit.frag
        │   │   │   ├── oxygen_ribbon.frag   oxygen_aurora.frag
        │   │   │   ├── ios_mesh.frag        ios_bloom.frag
        │   │   │   └── coloros_silk.frag    coloros_particles.frag
        │   │   └── wallpapers/              # MP4 em loop (varridos em runtime)
        │   │       ├── oxygen_ribbon.mp4    (1280x720, 16:9)
        │   │       ├── ios_mesh.mp4         (720x1560, 9:19.5)
        │   │       └── LEIA-ME.txt
        │   ├── java/com/hub/livewallpaper/
        │   │   ├── data/     Wallpaper.kt  Catalog.kt  RenderConfig.kt  Prefs.kt
        │   │   ├── render/   ScaleMath.kt  EglCore.kt  GLProgram.kt  ShaderLibrary.kt
        │   │   │             WallpaperRenderer.kt  VideoSource.kt  RenderSession.kt
        │   │   │             CanvasFallback.kt
        │   │   ├── service/  HubWallpaperService.kt
        │   │   └── ui/       MainActivity.kt  SettingsActivity.kt  WallpaperAdapter.kt
        │   │                 PreviewSurfaceView.kt  WallpaperApply.kt  Thumbnails.kt
        │   └── res/
        │       ├── layout/   activity_main.xml  activity_settings.xml  item_wallpaper.xml
        │       ├── values/   colors.xml  strings.xml  themes.xml
        │       ├── xml/      wallpaper.xml      # metadados do WallpaperService
        │       ├── drawable/ ícones vetoriais + scrims + thumbnail do sistema
        │       └── mipmap-anydpi[-v26]/ ic_launcher.xml
        └── test/java/com/hub/livewallpaper/ScaleMathTest.kt
```

---

## 3. Pipeline

```
MediaPlayer (assets/*.mp4) ──> SurfaceTexture ──> TEXTURE_EXTERNAL_OES ─┐
                                                                       ├─> ScaleMath + crop UV
<nome>.frag  scene(p, uv, t) ───────────────────────────────────────────┘        │
                                                                                 v
                                                        FBO 50/75/100%  ──>  EGL swap ──> Surface do Engine
```

* **Uma thread GL** com `Looper` + `Choreographer`, limite de FPS e pausa total quando invisível.
* **Degradação em cascata:** vídeo → shader procedural → Canvas 2D. Nunca tela preta.
* **Ajustes ao vivo:** `SharedPreferences` + `OnSharedPreferenceChangeListener` empurram um
  `RenderConfig` imutável para o Engine ativo, sem reiniciar o serviço.
* **Prévia WYSIWYG:** a galeria e os ajustes usam a mesma `RenderSession` do wallpaper real.

---

## 4. Compilar

Requisitos: **Android Studio Koala+**, **JDK 17**, AGP 8.5.2, Gradle 8.7, compileSdk 34, minSdk 24.

1. `New Project → Empty Views Activity`, nome **AuroraHub**, package `com.hub.livewallpaper`,
   Kotlin, Minimum SDK **API 24**, Kotlin DSL.
2. Sobreponha os arquivos deste projeto e aceite o **Sync Now**.
3. Testes JVM da matemática de escala (sem aparelho):
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
   → `app/build/outputs/apk/debug/app-debug.apk`
   Release assinado com a chave de debug (uso pessoal):
   ```bash
   ./gradlew :app:assembleRelease
   ```
5. Instalar no S10 (Depuração USB ligada):
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
6. Abrir o app → escolher um wallpaper → **Aplicar** → o sistema abre a prévia oficial →
   **Definir papel de parede** (inicial / bloqueio / ambas).
7. Logcat do Engine:
   ```bash
   adb logcat -s HubWallpaperService:V RenderSession:V VideoSource:V WallpaperRenderer:V
   ```

> `WallpaperManager.setWallpaperComponent()` exige `SET_WALLPAPER_COMPONENT` (signature|privileged).
> Em APK sideloaded ele lança `SecurityException`; o app captura e usa o fluxo oficial
> `ACTION_CHANGE_LIVE_WALLPAPER`. Nenhum app de terceiros pula a confirmação do sistema sem root.

---

## 5. Ajustes disponíveis

| Controle | Faixa | Efeito |
|---|---|---|
| Modo de escala | Preencher / Ajustar | `max()` (0 px de borda) vs `min()` (quadro inteiro) |
| Velocidade | 0,15× – 2,50× | `PlaybackParams` no vídeo; escala de tempo no shader |
| Overscan | 1,00× – 1,30× | zoom extra de segurança contra o zoom da One UI |
| Brilho | 50% – 150% | grading no fragment shader |
| Saturação | 0% – 180% | idem |
| Granulação | 0% – 100% | ruído temporal (evita banding em AMOLED) |
| Qualidade | 50 / 75 / 100% | resolução do FBO interno + blit linear |
| Limite de FPS | 30 / 60 | pacing no Choreographer |
| Silenciar vídeo | on/off | volume do MediaPlayer |
| Pausar na economia | on/off | para o loop quando o sistema entra em power save |

---

## 6. Notas de bateria (One UI 4.1)

* O Engine **não renderiza** com `onVisibilityChanged(false)` (tela apagada, AOD, app em primeiro plano).
* Qualidade 75% + 30 fps reduz o trabalho de fragmento em ~70% em 1440×3040.
* Se a One UI restringir o app: *Configurações → Assistência do dispositivo → Bateria →
  AuroraHub → Não restrito*.
* Nenhum `WAKE_LOCK`, `FOREGROUND_SERVICE`, `INTERNET` ou `BOOT_COMPLETED` é declarado.
