# inFAMOUS Second Son — Daedalus Fork v0.2

## Português

Esta atualização do port Android Bionic mantém o mesmo identificador de aplicativo e exige **o mesmo certificado de assinatura** das builds anteriores para permitir atualização sem perda de dados.

### Novidades
- A tela de Readback passa a oferecer apenas **Relaxed 1** e **Precise 2**.
- **IMPORTANTE: “Precise 2” é apenas o texto apresentado na interface. Sua execução real é o Híbrido V6, com Readback 1, proteção de leitura, cópias por staging e antecipação por fence; não ativa o Precise 2 real.**
- A preferência antiga do Precise é interpretada como Híbrido, evitando ativar o caminho antigo por engano.
- Removida a opção duplicada de proteção híbrida do painel de correções do renderer.
- Corrigidos textos, rótulos e traduções da interface, inclusive menus e mensagens de configuração.
- Versão do aplicativo: **0.2** (código **17**). O pacote permanece **com.daedalus.iss.bionic**.

### Preservado
- Runtime Android Bionic, FEX e Vulkan nativo; recursos de instalação PKG, drivers, salvamentos, Bloodframe, controles, HUD e patches existentes.
- Nenhum código é copiado da branch `experimental-native-passes-arm64`.
- Certificado de assinatura original; o workflow falha se a chave original não estiver disponível ou se o certificado não coincidir.

### Instalação
Android 11+, ARM64, Vulkan compatível. Instale a v0.2 sobre a versão anterior com o mesmo pacote e assinatura. Nenhum jogo, firmware, chave ou Lossless.dll acompanha o APK.

## English

- Android Bionic v0.2 keeps the original app ID and release signing certificate.
- Readback choices are **Relaxed 1** and **Precise 2**. **The Precise 2 UI label intentionally runs the Hybrid V6 backend with Readbacks=1; it does not run the real Precise 2 implementation.**
- Previously saved Precise preferences automatically select Hybrid functionality.
- Duplicate hybrid renderer toggle removed; UI wording and translations revised.
- All prior core game, audio, control, driver, PKG and save features remain. No new native-passes experimental code has been imported.
- APK version name 0.2, version code 17. The build is rejected if the original signing key or certificate is missing.
