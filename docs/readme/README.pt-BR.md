<div align="center">

<img src="../images/jiumi-icon.png" alt="Ícone do Jiumi" width="80" />

# Jiumi 揪咪

Uma ferramenta leve para monitorar a atividade dos apps do Android e gerenciá-los.
<br>Não precisa de root nem de computador. Os registros ficam no celular.

**[Baixar o APK](https://github.com/ybblab/Jiumi/releases/latest)** · [Guia](https://jiumi.ybblab.com/en/download/) · [Site oficial](https://jiumi.ybblab.com/en/)

<sub>Android 11 ou posterior · APK de cerca de 4 MB · <a href="../../LICENSE">Apache 2.0</a></sub>

[简体中文](../../README.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · **Português (Brasil)** · [Русский](README.ru.md)

</div>

## Visão geral do app

<p align="center">
  <a href="../images/monitor.en.png"><img src="../images/monitor.en.png" width="132" alt="Visão geral com o status do monitoramento e as atividades que precisam de atenção" /></a>&nbsp;
  <a href="../images/timeline.en.png"><img src="../images/timeline.en.png" width="132" alt="Linha do tempo das atividades e do status dos apps" /></a>&nbsp;
  <a href="../images/by-type.en.png"><img src="../images/by-type.en.png" width="132" alt="Atividades agrupadas por tipo, com a contagem de apps e registros" /></a>&nbsp;
  <a href="../images/apps.en.png"><img src="../images/apps.en.png" width="132" alt="Lista de apps agrupados por origem da instalação" /></a>
</p>

## Recursos

| Recurso | O que você pode fazer |
| --- | --- |
| Registros de atividade | Consulte **56 tipos de acesso em 12 grupos**, incluindo localização, área de transferência e identificadores do dispositivo. Veja os registros por horário, app ou tipo e filtre as atividades em segundo plano. |
| Atividades que precisam de atenção | Confira transferências de dados e acessos a dados sensíveis em segundo plano, acessos frequentes e tentativas negadas repetidamente. |
| Atividade de rede | Veja os endereços das conexões e as medições de tráfego disponíveis para cada app. Se o registro de domínios estiver ativado, as conexões associadas a um domínio podem exibi-lo. |
| Histórico do sistema | Abra os detalhes de um app para consultar os registros e totais de acesso a permissões que o Android mantém, incluindo algumas atividades anteriores ao início do monitoramento. |
| Exportação de registros | Em **Settings › Export logs**, salve os registros de um período escolhido em um ZIP com arquivos CSV. |
| Notificações | Durante o monitoramento, uma notificação compacta mostra o tempo decorrido e um botão para interromper a sessão. Você pode desativá-la em **Settings › Notifications**. Ela também pode mostrar o número de apps e de registros da sessão. Com **Attention alerts** ativado, você recebe um aviso quando surge um novo item que precisa de atenção, uma vez por app e por item em cada sessão. |

## Gerenciar apps

Os apps são agrupados pela origem da instalação. Abra os detalhes de um app para consultar sua atividade e decidir se quer fazer alguma alteração. Você inicia as ações de gerenciamento. Depois de cada alteração, o Jiumi consulta novamente o estado no Android para confirmar que ela entrou em vigor. Se o Android recusar a alteração, o Jiumi mostra o motivo.

<p align="center">
  <a href="../images/app-disable.en.png"><img src="../images/app-disable.en.png" width="250" alt="Demonstração no site da confirmação de desativação de um app" /></a>&nbsp;
  <a href="../images/app-delete.en.png"><img src="../images/app-delete.en.png" width="250" alt="Demonstração no site da confirmação de desinstalação e do aviso sobre o backup" /></a>&nbsp;
  <a href="../images/app-restore.en.png"><img src="../images/app-restore.en.png" width="250" alt="Demonstração no site das opções para restaurar um app desinstalado ou descartar o backup" /></a>
</p>

| Ação | O que acontece |
| --- | --- |
| Permissões | Ative ou desative permissões individualmente em **Permissions**, nos detalhes do app. **Manageable permissions** desativa de uma vez todas as permissões que podem ser gerenciadas. Ao reativar a opção, apenas as permissões desativadas pelo Jiumi são restauradas. As permissões bloqueadas pelo sistema aparecem como somente leitura. |
| Acesso à rede | Em dispositivos compatíveis, bloqueie ou restaure o acesso à rede de um app com o firewall do Android, sem ocupar a vaga de VPN. O controle fica bloqueado e mostra o motivo quando o app compartilha um UID com outros apps, quando outra ferramenta definiu a regra ou quando o Jiumi não consegue confirmar que apenas aquele app será afetado. |
| Desativar ou ativar | O Android desativa o app para o usuário atual e preserva o app e seus dados. Você pode reativá-lo em **Apps › Disabled**. Apps desativados têm um selo com um floco de neve. |
| Reativado | Se o Jiumi detectar que um app que desativou está ativo de novo, **Re-enabled** aparece na lista e nos detalhes do app. A linha do tempo registra a hora da detecção. Ative **Settings › App management › Disable again when re-enabled** para que o Jiumi desative o app de novo. |
| Desinstalar um app que você instalou | Digite o nome completo do pacote para confirmar. O Jiumi salva uma cópia do APK base e dos APKs divididos, se houver. Você pode tentar reinstalar o app em **Apps › Uninstalled**. **A desinstalação apaga os dados do app. O backup contém apenas os arquivos de instalação.** |
| Desinstalar um app pré-instalado | Ative **Allow uninstalling preinstalled apps** em **Settings › App management** para desinstalar apps pré-instalados compatíveis para o usuário atual. A cópia de fábrica e os dados do app são preservados. Você pode tentar restaurar o app em **Apps › Uninstalled**. Apps desinstalados têm um selo com uma lixeira. |

Em **Settings › App management**, o campo **Runs as** mostra com qual identidade cada ação é executada. Com acesso root ou com o Shizuku iniciado como root, as ações de desativar e ativar apps, desinstalar e restaurar apps pré-instalados são executadas como root. Assim, o Jiumi pode alterar apps do sistema que o celular não deixa o usuário shell modificar. Com a depuração sem fio, essas ações são executadas como usuário shell. Se o celular bloquear uma alteração em um app do sistema, o Jiumi mostra o motivo. Desinstalar um app que você instalou afeta apenas o usuário atual. Uma cópia no perfil de trabalho ou no segundo espaço permanece instalada.

Os componentes essenciais do sistema são protegidos por padrão. Alterar um componente do qual o celular depende pode afetar funções do sistema. O Jiumi mostra um aviso antes dessas alterações.

## Como o Jiumi vê atividades em segundo plano sem root?

Enquanto os apps estão em execução, o Android fornece registros de acesso a permissões, tabelas de conexões, rastreamentos do sistema e registros de eventos. O monitor do Jiumi lê os sinais disponíveis como **usuário shell**. O app organiza os sinais com base em regras e os armazena em um banco de dados no celular. O monitoramento não injeta código em outros apps. Para desativar ou desinstalar apps, revogar permissões ou bloquear o acesso à rede, você precisa iniciar a ação na seção de gerenciamento de apps.

<p align="center"><img src="../images/how-it-works.en.png" alt="Os apps geram sinais do sistema Android. O monitor do Jiumi os lê como usuário shell. O app organiza e guarda os registros no celular. O registro opcional de domínios recebe consultas DNS por uma VPN local." width="900" /></p>

| Origem dos sinais | O que o Jiumi pode ver |
| --- | --- |
| Registros de acesso a permissões (AppOps) | Qual app usou uma permissão e quando, além do histórico mantido pelo Android. |
| Tabelas de conexões | Os endereços aos quais os apps se conectaram. Por padrão, é feita uma amostragem a cada cinco segundos. |
| Rastreamentos do sistema (Perfetto) | Chamadas identificáveis a serviços do sistema e tráfego por conexão quando o sistema oferece suporte. |
| Registros de eventos | Quando um processo foi iniciado e o motivo que o Android registrou. |

**Escolha entre depuração sem fio, Shizuku ou root.** O monitor roda como usuário shell com os três métodos. A depuração sem fio pode ser pareada no próprio celular. O Shizuku pode ser iniciado via ADB ou com root, e você precisa conceder permissão ao Jiumi. No modo root, o Jiumi muda para o usuário shell para iniciar o monitor. Desativar e ativar apps, desinstalar e restaurar apps pré-instalados são ações executadas diretamente como root, permitindo alterar apps que o Android não deixa o usuário shell modificar. O mesmo vale para o Shizuku iniciado como root.

O **registro opcional de domínios** usa uma VPN local para receber consultas DNS e encaminhá-las sem alterações aos servidores DNS da rede atual. O restante do tráfego segue direto para o destino. O registro de domínios ocupa a vaga de VPN do sistema. Consultas que usam o DNS criptografado de um app ou um provedor de DNS privado configurado por nome de host podem não passar pelo registro.

## Baixar e iniciar o monitoramento

1. Em um celular com Android 11 ou posterior, baixe e instale o APK em [GitHub Releases](https://github.com/ybblab/Jiumi/releases/latest).
2. Na primeira vez que abrir o Jiumi, escolha um modo de inicialização. Para usar a depuração sem fio, que é o modo recomendado, conecte-se ao Wi-Fi e siga a configuração para permitir notificações e ativar as opções do desenvolvedor. Digite na notificação do Jiumi o código de pareamento de seis dígitos que o Android exibe.
3. Na tela **Monitor**, toque em **Start monitoring**. Uma notificação persistente aparece durante o monitoramento. Você pode interrompê-lo pela notificação ou pelo app.

Após uma reinicialização do celular, uma atualização do Jiumi ou uma interrupção do serviço de depuração, o Jiumi retoma automaticamente a mesma sessão quando a conexão escolhida estiver disponível. Se o cartão exibir **Monitoring interrupted**, restabeleça a conexão escolhida e toque em **Resume**. O tempo de interrupção não conta como tempo de monitoramento.

Ao usar a depuração sem fio, ativar também a depuração USB ajuda a manter o monitor em execução se o Android desativar a depuração sem fio quando o Wi-Fi for desconectado. A persistência do ADB vem desativada por padrão. Se você ativá-la, uma porta só será reutilizada se o dispositivo já tiver um serviço verificado escutando na interface de loopback. Consulte o [guia de configuração](https://jiumi.ybblab.com/en/download/) para obter mais detalhes.

## Privacidade e limites

- **Os registros ficam no celular.** O Jiumi não usa contas e não envia os registros de monitoramento. O app pode verificar se há atualizações no GitHub. Se você escolher atualizar, o app baixa o APK e verifica o tamanho do arquivo, o hash SHA-256 e a assinatura. A verificação de atualizações pode ser desativada.
- **Os registros descrevem atividades e conexões.** O Jiumi pode mostrar que uma permissão foi usada ou que um acesso foi negado. Ele não consegue ver mensagens, senhas nem o conteúdo do tráfego de rede.
- **A cobertura depende do sistema.** Conexões curtas podem terminar entre duas amostras. Ações como a leitura direta de arquivos podem não produzir um sinal utilizável. A medição do tráfego por conexão exige Android 14 ou posterior e precisa de suporte do sistema. Para reduzir alertas incorretos, o Jiumi deixa de fora algumas atividades que não consegue confirmar.
- **O monitoramento consome mais bateria.** O consumo depende da atividade dos apps. Quando não precisar mais do monitoramento, interrompa-o pela notificação.

## Compilar a partir do código-fonte

Você precisa do JDK 17 e do Android SDK.

```sh
cd code
./gradlew :app:assembleDebug
```

As categorias de monitoramento, as regras de alerta e os apps protegidos são configurados no [diretório de regras](../../code/app/src/main/assets/rules/). Veja [CONTRIBUTING.md](../../CONTRIBUTING.md) para conhecer a organização do código e os passos para contribuir.

## Contribuições e licença

Issues e pull requests são bem-vindos. Relate problemas de segurança em caráter privado de acordo com as orientações em [SECURITY.md](../../SECURITY.md).

O Jiumi é distribuído sob a [licença Apache 2.0](../../LICENSE). Os componentes de terceiros e suas licenças estão listados em [NOTICE](../../NOTICE). Use o Jiumi em dispositivos que você possui ou tem autorização para usar.

## Histórico de estrelas

<a href="https://www.star-history.com/#ybblab/Jiumi&amp;Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date&amp;theme=dark" />
    <img alt="Gráfico do histórico de estrelas do Jiumi no GitHub" src="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date" />
  </picture>
</a>

<div align="center">

**Se o Jiumi ajuda você a entender o que seus apps fazem, dê uma ⭐ no GitHub.**

</div>
