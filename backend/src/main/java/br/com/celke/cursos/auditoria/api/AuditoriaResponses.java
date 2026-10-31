package br.com.celke.cursos.auditoria.api;

import br.com.celke.cursos.auditoria.application.AuditoriaService.RegistroComUsuario;
import br.com.celke.cursos.auditoria.domain.RegistroAuditoria;
import br.com.celke.cursos.shared.auditoria.OperacaoAuditoria;
import java.time.Instant;

/** Respostas da trilha de auditoria (ADM). */
public final class AuditoriaResponses {

    private AuditoriaResponses() {}

    /**
     * Uma ação registrada.
     *
     * @param antes campos antes da mudança, em JSON (nulo na inclusão)
     * @param depois campos depois da mudança, em JSON (nulo na exclusão)
     * @param usuarioNome nulo quando foi o sistema (worker, agendamento) ou ação sem login
     */
    public record Registro(
            Long id,
            Instant em,
            OperacaoAuditoria operacao,
            String entidade,
            String entidadeId,
            Long usuarioId,
            String usuarioNome,
            String usuarioEmail,
            String origem,
            String ip,
            String userAgent,
            String requestId,
            String antes,
            String depois) {

        public static Registro de(RegistroComUsuario item) {
            RegistroAuditoria r = item.registro();
            return new Registro(
                    r.getId(),
                    r.getEm(),
                    r.getOperacao(),
                    r.getEntidade(),
                    r.getEntidadeId(),
                    r.getUsuarioId(),
                    item.usuario() == null ? null : item.usuario().getNome(),
                    item.usuario() == null ? null : item.usuario().getEmail(),
                    r.getOrigem(),
                    r.getIp(),
                    r.getUserAgent(),
                    r.getRequestId(),
                    r.getAntes(),
                    r.getDepois());
        }
    }
}
