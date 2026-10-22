// Validação pública do certificado: consulta /api/certificados/{codigo} no mesmo domínio.
(function () {
  const form = document.getElementById('validar');
  const campo = document.getElementById('codigo');
  const resultado = document.getElementById('resultado');

  function mostrar(classe, filhos) {
    resultado.className = classe;
    resultado.replaceChildren(...filhos);
  }

  function texto(tag, conteudo) {
    const el = document.createElement(tag);
    el.textContent = conteudo;
    return el;
  }

  function linha(rotulo, valor) {
    const item = document.createElement('div');
    item.append(texto('dt', rotulo), texto('dd', valor));
    return item;
  }

  async function validar(codigo) {
    mostrar('s-resultado', [texto('p', 'Consultando...')]);
    try {
      const resposta = await fetch('/api/certificados/' + encodeURIComponent(codigo), {
        headers: { Accept: 'application/json' },
      });
      if (resposta.status === 404) {
        mostrar('s-resultado s-resultado-erro', [
          texto('strong', 'Certificado não encontrado.'),
          texto('p', 'Confira o código digitado.'),
        ]);
        return;
      }
      if (!resposta.ok) throw new Error(String(resposta.status));
      const c = await resposta.json();
      const dados = document.createElement('dl');
      dados.append(
        linha('Aluno', c.nomeCompleto),
        linha('Curso', c.cursoTitulo),
        linha('Turma', c.turmaNome),
        linha('Emitido em', new Date(c.emitidoEm).toLocaleDateString('pt-BR')),
      );
      if (c.cargaHorariaHoras != null)
        dados.append(linha('Carga horária', c.cargaHorariaHoras + ' h'));
      mostrar('s-resultado s-resultado-ok', [texto('strong', 'Certificado válido.'), dados]);
    } catch {
      mostrar('s-resultado s-resultado-erro', [
        texto('strong', 'Não foi possível consultar agora.'),
        texto('p', 'Tente de novo em instantes.'),
      ]);
    }
  }

  form.addEventListener('submit', function (evento) {
    evento.preventDefault();
    const codigo = campo.value.trim();
    if (!codigo) {
      mostrar('s-resultado s-resultado-erro', [
        texto('strong', 'Informe o código do certificado.'),
      ]);
      campo.focus();
      return;
    }
    history.replaceState(null, '', '?codigo=' + encodeURIComponent(codigo));
    validar(codigo);
  });

  // Link direto do certificado: /certificados/validar/?codigo=...
  const inicial = new URLSearchParams(location.search).get('codigo');
  if (inicial) {
    campo.value = inicial;
    validar(inicial.trim());
  }
})();
