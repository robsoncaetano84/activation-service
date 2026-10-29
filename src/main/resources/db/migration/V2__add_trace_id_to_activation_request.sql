-- traceId da requisição que criou a solicitação; o worker usa para continuar o rastreio
-- nos logs e no cabeçalho enviado ao provedor
ALTER TABLE activation_request ADD COLUMN trace_id VARCHAR(64);
