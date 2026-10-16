-- ============================================================================
-- Índice para el recordatorio de resultado pendiente
--
-- RecordatorioResultadoPartidoScheduler (cada 15 minutos) filtra PARTIDOS por
-- AVISO_RESULTADO_ENVIADO = 0 y por rango de DIA. Con este índice la consulta
-- solo recorre los partidos todavía sin avisar.
--
-- IMPORTANTE: se ejecuta a mano en dev y producción ANTES de desplegar.
-- ============================================================================

CREATE INDEX IDX_PARTIDOS_AVISO_DIA ON PARTIDOS (AVISO_RESULTADO_ENVIADO, DIA);
