# Baianês: style guide for the app's translation

`values-b+pt+BR+baianes/strings.xml` in every module that has `values/strings.xml`.
Regional Portuguese from Bahia (Salvador/Recôncavo), friendly and playful, but **every
control must stay understandable**: a label is a label first, a joke second.

## Rules
- Same keys, same order, same placeholders (`%1$s`, `%1$d`, `%%`), same `<plurals>` structure
  (pt: `one` = 0 and 1, `other`). Never translate `translatable="false"` strings, brand names
  (Escutaê), file formats (OPML, RSS) or the language names in the picker.
- Escape apostrophes (`\'`) and `@`/`?` per Android rules; keep `\n`.
- No emoji. Keep short labels short (buttons, tabs: 1–2 words, max ~14 characters).
- Slang goes in friendly text (empty states, hints, confirmations, toasts, notifications);
  buttons and tabs stay plain Portuguese with at most a light touch. Errors stay clear.
- Informal "você" mixed with "tu/ocê" is NOT used; use "você" or imperative, plus markers below.

## Vocabulary (use, don't overdo: at most one marker per string)
| Idea | Baianês |
|---|---|
| Hey / greeting | Oxe, Eita, Ôrra meu rei, Meu rei / Minha rainha |
| Surprise / empty | Oxente, Eita, Vixe |
| Great / cool | massa, arretado, top demais, show de bola |
| Very | demais, "muito" -> "que só" ("bom que só") |
| Wait | Calma aí, Péra aí, Dá um tempinho |
| Now | agora, já já, "na hora" |
| Friend | meu rei, meu parceiro, painho, mainha (only in warm text) |
| Nothing here | "Num tem nada aqui não" |
| Problem / error | "Deu ruim", "Deu um B.O.", "Vixe, deu pau" |
| Listening | escutar, "dar uma ouvida" |
| Done | "Tá na mão", "Prontinho" |
| Slow / calm | "Devagar, devagarinho", "sem pressa, que a Bahia não tem" |
| Mistake | "Eita, errei" |

Fixed terms (consistent everywhere): episode = episódio, show/podcast = programa (or podcast),
queue (Up next) = "Na fila", download = baixar / baixado, subscribe = seguir ("Seguir"),
unsubscribe = "Deixar de seguir", settings = Ajustes, library = Biblioteca, discover = Descobrir,
home = Início, sleep timer = "Soneca" (label: "Temporizador de sono"), speed = velocidade,
chapters = capítulos.

## Examples
- "Nothing downloaded yet" -> "Oxe, ainda num baixou nada"
- "Something went wrong" -> "Vixe, deu ruim. Tenta de novo?"
- "No episodes" -> "Eita, num tem episódio aqui não"
- "Download" (button) -> "Baixar"; "Cancel" -> "Cancelar"; "Retry" -> "Tentar de novo"
