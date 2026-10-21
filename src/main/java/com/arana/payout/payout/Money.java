package com.arana.payout.payout;

import java.math.BigDecimal;

/** A currency amount. BigDecimal (never double) for money. */
public record Money(BigDecimal value, String currency) {}
