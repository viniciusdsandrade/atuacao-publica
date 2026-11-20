# Pedidos de acesso à informação

Rascunhos para o mantenedor enviar.
O pedido pela Lei nº 12.527/2011 (LAI) exige a identificação de quem pede (art. 10).
Os campos entre colchetes ficam com o mantenedor, e o projeto não envia pedido em nome dele.
O texto da lei foi conferido numa cópia do LexML (`normas.leg.br`) em 2026-11-19.

## Câmara Municipal de Paulínia: proposituras, votações e presença

Por quê: a Câmara publica os três no SISCAM (`paulinia.siscam.com.br`).
O `robots.txt` do SISCAM recusa coletores que não sejam buscadores nomeados, e o projeto não contorna essa recusa (ADR-0066).
Revisto em 2026-11-19: a recusa continua, e Paulínia não tem SAPL.

Canal: o Sistema InformaBR, da CGU (`informabr.cgu.gov.br`). A página da Ouvidoria/SIC da Câmara (`www.camarapaulinia.sp.gov.br/ouvidoria/`) o indica para pedido de acesso à informação. Conferido em 2026-11-20.

Texto:

> À Câmara Municipal de Paulínia.
>
> Com base na Lei nº 12.527/2011, peço, em formato aberto e legível por máquina (CSV, JSON ou XML), os dados que a Câmara publica no SISCAM, de 1º de janeiro de 2025 até a data da resposta:
>
> 1. proposituras, com tipo, número, ano, data de apresentação, ementa, autores e situação;
> 2. votações nominais, com data, sessão, matéria, resultado e voto de cada vereador;
> 3. presença dos vereadores em cada sessão, com as faltas justificadas.
>
> Peço também que a Câmara avalie permitir, no `robots.txt` do SISCAM, o acesso de coletores identificados, com limite de requisições. O art. 8º, § 3º, III, da mesma lei pede o acesso automatizado por sistemas externos em formatos abertos, estruturados e legíveis por máquina.
>
> [nome do requerente]
> [contato para a resposta]

## Assembleia Legislativa do Paraná: verbas de ressarcimento

Por quê: a consulta das verbas por deputado (`api/ressarcimento`) exige reCAPTCHA v3, e o projeto não contorna CAPTCHA (`alep-ressarcimento`, em `docs/FONTES.md`).
As votações nominais da ALEP já têm download aberto, sem CAPTCHA.

Canal: não validado. A busca na web de 2026-11-20 não achou a página do serviço de informação ao cidadão da Assembleia.

Texto:

> À Assembleia Legislativa do Estado do Paraná.
>
> Com base na Lei nº 12.527/2011, peço, em formato aberto e legível por máquina (CSV ou JSON), os dados das verbas de ressarcimento dos deputados estaduais que a Assembleia publica no portal da transparência, de 1º de janeiro de 2022 até a data da resposta: deputado, mês de referência, categoria da despesa, fornecedor (nome e CNPJ; de pessoa física, só o nome), número do documento e valor.
>
> Peço também que a Assembleia publique esses dados para download aberto, sem CAPTCHA, como já faz com as votações nominais. O art. 8º, § 3º, III, da mesma lei pede o acesso automatizado por sistemas externos em formatos abertos, estruturados e legíveis por máquina.
>
> [nome do requerente]
> [contato para a resposta]

## Depois da resposta

- Dado recebido entra como fonte nova no catálogo (`contrib/fontes.yaml`), com o pedido e a data da resposta.
- Se a Câmara liberar o `robots.txt`, a coleta do SISCAM ganha um ADR, como o ADR-0066 pede.
