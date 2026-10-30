# Configuração do projeto

Valores deste projeto. Os prompts usam os marcadores da primeira coluna no lugar deles, e as specs em
`.sdd/specs/` já trazem os valores.

| Marcador | Valor | Uso |
|---|---|---|
| `<dominio>` | celke.uno | domínio dos endereços e dos e-mails |
| `<sub-site>` | cursos | site público |
| `<sub-aluno>` | aluno | área do aluno e API |
| `<sub-adm>` | adm | painel administrativo e API (restrito por IP) |
| `<sub-media>` | media | vídeos e arquivos (Garage) |
| `<dono-github>` | celkecursos | dono do repositório e das imagens no GHCR |
| `<titular>` | Cesar Szpak | titular dos direitos na licença (`LICENSE.txt`) |

Na homologação, os subdomínios ganham o prefixo `homolog-` (ex.: `homolog-aluno.celke.uno`).

Senhas, tokens e chaves não entram aqui: ficam só nos `.env` de cada ambiente e nos secrets do GitHub.
