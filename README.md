<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/logo-dark.png">
  <img src="docs/logo-light.png" alt="time-calibrator" width="560">
</picture>

<br>
<br>

[![release](https://img.shields.io/github/v/release/crispinzz/time-calibrator?style=flat-square&label=release&color=e3001b)](https://github.com/crispinzz/time-calibrator/releases/latest)
![platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![kotlin](https://img.shields.io/badge/kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![target](https://img.shields.io/badge/target%20SDK-35-555?style=flat-square)

**Um app Android que calibra o relógio do celular com um relógio externo, como o sinal da escola,<br>o ponto da empresa ou o relógio da parede, e mostra o horário certo em tempo real, também na tela inicial.**

<br>

<img src="docs/screenshot.webp" alt="Tela principal do Time Calibrator: relógio calibrado, ajuste de ±1 s, calibração e widget" width="520">

</div>

---

## O problema

Muito relógio que manda no seu dia **não está sincronizado** com a hora real:

- 🏫 **Escola**: o sinal toca 3 minutos antes do que o celular diz.
- 🏢 **Trabalho**: o relógio de ponto está 2 min 14 s atrasado.
- 🏋️ **Academia, ônibus, prova, plantão**: qualquer lugar com um relógio “oficial” que não bate com o seu.

Em vez de ficar subtraindo minutos de cabeça toda vez, o Time Calibrator mede essa diferença **uma vez** e passa a mostrar o horário daquele relógio, com precisão de segundo, no app e num widget na tela inicial.

## Como funciona

1. Você escolhe um horário de referência, por exemplo **17:40**, quando o sinal toca.
2. No instante em que o relógio externo marcar esse horário, você toca em **Calibrar**.
3. O app compara esse instante com a hora do celular e guarda a diferença:

```text
offset = hora do celular no toque − horário de referência
hora calibrada = agora − offset
```

```text
Celular no toque     17:40:08
Sinal tocou às       17:40:00
──────────────────────────────
Diferença            8 s atrasado   → o app passa a mostrar a hora do sinal
```

Como o app guarda uma diferença em vez de uma hora fixa, ele funciona para qualquer horário (`07:00`, `12:00`, `18:30:15`) e para relógio **adiantado ou atrasado**. A diferença fica limitada a ±12 h, então calibrar às 00:00 com o celular em 23:59:50 dá 10 s, não quase 24 h.

## Recursos

| | |
|---|---|
| ⏱️ **Calibração em um toque** | Escolha o horário do sinal e toque em *Calibrar* na hora certa. |
| ➕ **Ajuste fino** | Botões de −1 s / +1 s para corrigir sem recalibrar, e *Zerar* para voltar à hora do sistema. |
| 🕐 **Vários relógios** | Cada relógio tem nome, calibração e aparência próprios, como “Escola”, “Trabalho” e “Academia”. |
| 📱 **Widgets na tela inicial** | Um widget por relógio, atualizado a cada segundo, com cor de fundo, cor do texto, opacidade e legenda personalizáveis. |
| 🔗 **Código de calibração** | Gera um código curto (ex.: `59X-R8G`) para mandar a um colega, que aplica e fica com o mesmo horário sem calibrar. Tem verificação embutida, então um código digitado errado é recusado em vez de aplicar um ajuste errado. |
| 🌗 **Tema claro, escuro ou automático** | Os widgets seguem o tema do app. |
| 🔁 **Sobrevive ao reinício** | Os widgets se reconfiguram sozinhos após reiniciar o celular ou mudar o fuso/hora do sistema. |

## Instalação

### Opção 1: APK (mais fácil)

1. Baixe o `time-calibrator-v1.0.apk` em **[Releases](https://github.com/crispinzz/time-calibrator/releases/latest)**.
2. Abra o arquivo no celular. Se pedir, permita **“Instalar apps desconhecidos”** para o navegador ou o gerenciador de arquivos.
3. Para adicionar o widget, segure a tela inicial e vá em **Widgets › Time Calibrator**. Também dá para usar o botão *Aplicar widget* dentro do app.

> [!NOTE]
> O APK é assinado com uma chave de debug. O Play Protect pode mostrar um aviso de “app desconhecido”; toque em *Instalar mesmo assim*.

> [!TIP]
> **MIUI / HyperOS (Xiaomi):** para o widget atualizar sempre, ative *Início automático* e defina *Economia de bateria* como **Sem restrições** para o app.

### Opção 2: via ADB

Com a depuração USB ativada no celular:

```bash
adb install time-calibrator-v1.0.apk
```

### Opção 3: compilar do código

Requer o **JDK 17** (o que vem com o Android Studio serve) e o **Android SDK 35**.

```bash
git clone https://github.com/crispinzz/time-calibrator.git
cd time-calibrator
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`. Se preferir, abra a pasta no Android Studio e clique em ▶ Run.

## Requisitos

| | |
|---|---|
| **Android** | 8.0 (Oreo, API 26) ou mais novo |
| **Target SDK** | 35 (Android 15) |
| **Permissões** | `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` para o widget acertar as viradas do dia (meia-noite, 01:00 e 10:00) no segundo exato, e `RECEIVE_BOOT_COMPLETED` para religar os widgets após reiniciar. **Não usa internet.** |
| **Dependências** | `androidx.core` 1.13 · `androidx.appcompat` 1.7 · `material` 1.12 |

## Estrutura

```text
app/src/main/java/br/com/timecalibrator/
├── MainActivity.kt          tela principal, calibração e lista de relógios
├── ClockOffset.kt           cálculo e normalização da diferença (±12 h)
├── CalibrationCode.kt       código compartilhável com verificação
├── ClockWidgetProvider.kt   widget da tela inicial e alarmes das viradas do dia
├── WidgetConfigActivity.kt  personalização do widget
├── Prefs.kt                 armazenamento de cada relógio
└── ui/                      relógio ao vivo, seletores, color picker e sheets
```

---

<div align="center">

Feito por **[Gabriel Crispin](https://github.com/crispinzz)**: um problema real, uma solução pequena.

</div>
