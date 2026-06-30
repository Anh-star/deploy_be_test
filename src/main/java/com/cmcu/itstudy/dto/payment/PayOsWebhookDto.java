package com.cmcu.itstudy.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayOsWebhookDto {

    private String code;

    private String desc;

    private Boolean success;

    private Data data;

    private String signature;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {

        @JsonProperty("orderCode")
        private Long orderCode;

        private Long amount;

        private String description;

        private String accountNumber;

        private String reference;

        @JsonProperty("transactionDateTime")
        private String transactionDateTime;

        private String paymentLinkId;

        private String code;

        private String desc;

        @JsonProperty("counterAccountBankName")
        private String counterAccountBankName;

        @JsonProperty("counterAccountName")
        private String counterAccountName;

        @JsonProperty("counterAccountNumber")
        private String counterAccountNumber;

        @JsonProperty("virtualAccountName")
        private String virtualAccountName;

        @JsonProperty("virtualAccountNumber")
        private String virtualAccountNumber;

        @JsonProperty("counterAccountBankId")
        private String counterAccountBankId;

        @JsonProperty("currency")
        private String currency;
    }
}