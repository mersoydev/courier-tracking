/**
 * Bu paket {@code @NullMarked}'tir: aksi açıkça belirtilmedikçe içindeki tüm tipler null
 * değildir. Yalnızca gerçekten null olabilen yerler {@code @Nullable} ile işaretlenir.
 * Böylece null sözleşmesi paket düzeyinde tek bir yerde kurulur; hem okuyucu hem de statik
 * analiz araçları neyin null olabileceğini tahmin etmeden bilir. (Spring Framework 7'nin de
 * izlediği JSpecify yaklaşımı: https://jspecify.dev/)
 */
@NullMarked
package com.casestudy.couriertracking.exception;

import org.jspecify.annotations.NullMarked;
