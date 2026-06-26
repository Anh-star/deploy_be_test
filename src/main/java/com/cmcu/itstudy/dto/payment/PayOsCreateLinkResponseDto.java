package com.cmcu.itstudy.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayOsCreateLinkResponseDto {

    private String code;

    private String desc;

    private Data data;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {

        @JsonProperty("paymentLinkId")
        private String paymentLinkId;

        @JsonProperty("checkoutUrl")
        private String checkoutUrl;

        @JsonProperty("qrCode")
        private String qrCode;

        @JsonProperty("orderCode")
        private Long orderCode;

        private Long amount;

        private String status;
    }
}