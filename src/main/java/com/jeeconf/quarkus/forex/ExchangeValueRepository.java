package com.jeeconf.quarkus.forex;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeValueRepository extends JpaRepository<ExchangeValue, Long> {
  ExchangeValue findByFromCurrencyAndToCurrency(String from, String to);
}