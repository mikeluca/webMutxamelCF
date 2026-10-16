package com.mikedev.mutxamelcf.model;

import java.util.List;

public record FfcvParseResult(List<FfcvFilaCsv> filas, List<FfcvErrorLinea> errores) {
}
