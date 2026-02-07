package com.gsc.stockoverview.utils

import java.math.BigDecimal
import java.math.RoundingMode

fun Double.round(scale: Int): Double =
    BigDecimal(this.toString())
        .setScale(scale, RoundingMode.HALF_UP)
        .toDouble()