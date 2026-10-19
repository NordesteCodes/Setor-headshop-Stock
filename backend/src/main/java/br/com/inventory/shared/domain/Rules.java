package br.com.inventory.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Rules {
    private Rules() {}
    public static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException("BUSINESS_RULE", message);
    }
    public static BigDecimal money(BigDecimal value) {
        require(value != null && value.signum() >= 0 && value.precision() <= 18 && value.scale() <= 2, "Valor monetário inválido; use até duas casas decimais.");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
    public static String text(String value, int max) {
        require(value != null && !value.isBlank() && value.strip().length() <= max, "Texto obrigatório ou muito longo.");
        return value.strip();
    }
}
