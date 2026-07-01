package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.PayOsProperties;
import com.cmcu.itstudy.dto.payment.PayOsCreateLinkResponseDto;
import com.cmcu.itstudy.dto.payment.PayOsWebhookDto;
import com.cmcu.itstudy.service.contract.PayOsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Service
public class PayOsServiceImpl implements PayOsService {

    private static final Logger log = LoggerFactory.getLogger(PayOsServiceImpl.class);

    private final PayOsProperties props;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PayOsServiceImpl(PayOsProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public PayOsCreateLinkResponseDto createPaymentLink(long orderCode, long amount, String description) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderCode", orderCode);
            body.put("amount", amount);
            body.put("description", description);
            body.put("cancelUrl", props.getCancelUrl());
            body.put("returnUrl", props.getReturnUrl());

            Map<String, String> signatureData = new TreeMap<>();
            signatureData.put("amount", String.valueOf(amount));
            signatureData.put("cancelUrl", props.getCancelUrl());
            signatureData.put("description", description);
            signatureData.put("orderCode", String.valueOf(orderCode));
            signatureData.put("returnUrl", props.getReturnUrl());

            String signature = createSignatureFromMap(signatureData, props.getChecksumKey(), orderCode, amount, description);
            body.put("signature", signature);

            String jsonBody = objectMapper.writeValueAsString(body);
            String jsonBodyPretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);

            String url = props.getApiBaseUrl() + "/v2/payment-requests";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("x-client-id", props.getClientId())
                    .header("x-api-key", props.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            log.info("====== PayOS DEBUG REQUEST START ======");
            log.info("PayOS URL = {}", url);
            log.info("PayOS HEADER x-client-id = {}", props.getClientId());
            log.info("PayOS HEADER x-api-key   = {} (first 6 chars)", safePrefix(props.getApiKey()));
            log.info("PayOS HEADER Content-Type = application/json");
            log.info("PayOS BODY (pretty) =\n{}", jsonBodyPretty);
            log.info("PayOS BODY (raw) = {}", jsonBody);
            log.info("====== PayOS DEBUG REQUEST END ======");

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("====== PayOS DEBUG RESPONSE START ======");
            log.info("PayOS RESPONSE status = {}", response.statusCode());
            log.info("PayOS RESPONSE headers =\n{}", response.headers().map());
            log.info("PayOS RESPONSE body (raw) = {}", response.body());
            try {
                Object prettyJson = objectMapper.readTree(response.body());
                log.info("PayOS RESPONSE body (pretty) =\n{}",
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prettyJson));
            } catch (Exception parseEx) {
                log.info("PayOS RESPONSE body is not valid JSON, skip pretty print");
            }
            log.info("====== PayOS DEBUG RESPONSE END ======");

            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("PayOS createPaymentLink failed: HTTP " + response.statusCode());
            }

            PayOsCreateLinkResponseDto parsed = objectMapper.readValue(response.body(), PayOsCreateLinkResponseDto.class);
            if (!"00".equals(parsed.getCode()) || parsed.getData() == null) {
                throw new IllegalStateException("PayOS createPaymentLink business error: code=" + parsed.getCode() + " desc=" + parsed.getDesc());
            }
            return parsed;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("PayOS createPaymentLink error", e);
            throw new IllegalStateException("Cannot create PayOS payment link: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(PayOsWebhookDto payload) {
        if (payload == null || payload.getData() == null || payload.getSignature() == null) {
            return false;
        }
        try {
            String debugKey = props.getChecksumKey();
            log.info("PayOS DEBUG key: null={}, length={}, first4={}, last4={}",
                    debugKey == null,
                    debugKey == null ? 0 : debugKey.length(),
                    debugKey == null || debugKey.length() < 4 ? debugKey : debugKey.substring(0, 4),
                    debugKey == null || debugKey.length() < 4 ? debugKey : debugKey.substring(debugKey.length() - 4));
            Map<String, String> sorted = sortedDataForSignature(payload.getData());
            String expected = createSignatureFromMapForVerify(sorted, props.getChecksumKey());
            boolean match = expected.equalsIgnoreCase(payload.getSignature());
            if (!match) {
                log.warn("PayOS webhook signature mismatch: expected={} received={}",
                        expected, payload.getSignature());
            } else {
                log.info("PayOS webhook signature match: signature={}", expected);
            }
            return match;
        } catch (Exception e) {
            log.error("PayOS verifyWebhookSignature error", e);
            return false;
        }
    }

    @Override
    public boolean isSuccessPayload(PayOsWebhookDto payload) {
        if (payload == null || payload.getData() == null) {
            return false;
        }
        boolean topOk = Boolean.TRUE.equals(payload.getSuccess()) && "00".equals(payload.getCode());
        boolean dataOk = "00".equals(payload.getData().getCode());
        return topOk && dataOk;
    }

    private Map<String, String> sortedDataForSignature(PayOsWebhookDto.Data data) {
        Map<String, String> map = new TreeMap<>();
        if (data.getOrderCode() != null) {
            map.put("orderCode", String.valueOf(data.getOrderCode()));
        }
        if (data.getAmount() != null) {
            map.put("amount", String.valueOf(data.getAmount()));
        }
        if (data.getDescription() != null) {
            map.put("description", data.getDescription());
        }
        if (data.getAccountNumber() != null) {
            map.put("accountNumber", data.getAccountNumber());
        }
        if (data.getReference() != null) {
            map.put("reference", data.getReference());
        }
        if (data.getTransactionDateTime() != null) {
            map.put("transactionDateTime", data.getTransactionDateTime());
        }
        if (data.getPaymentLinkId() != null) {
            map.put("paymentLinkId", data.getPaymentLinkId());
        }
        if (data.getCode() != null) {
            map.put("code", data.getCode());
        }
        if (data.getDesc() != null) {
            map.put("desc", data.getDesc());
        }
        if (data.getCounterAccountBankId() != null) {
            map.put("counterAccountBankId", data.getCounterAccountBankId());
        }
        if (data.getCounterAccountBankName() != null) {
            map.put("counterAccountBankName", data.getCounterAccountBankName());
        }
        if (data.getCounterAccountName() != null) {
            map.put("counterAccountName", data.getCounterAccountName());
        }
        if (data.getCounterAccountNumber() != null) {
            map.put("counterAccountNumber", data.getCounterAccountNumber());
        }
        if (data.getVirtualAccountName() != null) {
            map.put("virtualAccountName", data.getVirtualAccountName());
        }
        if (data.getVirtualAccountNumber() != null) {
            map.put("virtualAccountNumber", data.getVirtualAccountNumber());
        }
        if (data.getCurrency() != null) {
            map.put("currency", data.getCurrency());
        }
        return map;
    }

    private String createSignatureFromMap(Map<String, String> data, String key) throws Exception {
        return createSignatureFromMap(data, key, null, null, null, false);
    }

    private String createSignatureFromMapForVerify(Map<String, String> data, String key) throws Exception {
        return createSignatureFromMap(data, key, null, null, null, true);
    }

    private String createSignatureFromMap(Map<String, String> data, String key,
                                          Long debugOrderCode, Long debugAmount, String debugDescription) throws Exception {
        return createSignatureFromMap(data, key, debugOrderCode, debugAmount, debugDescription, false);
    }

    private String createSignatureFromMap(Map<String, String> data, String key,
                                          Long debugOrderCode, Long debugAmount, String debugDescription,
                                          boolean isVerify) throws Exception {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (query.length() > 0) {
                query.append("&");
            }
            query.append(entry.getKey()).append("=").append(entry.getValue());
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] bytes = mac.doFinal(query.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hash.append('0');
            }
            hash.append(hex);
        }
        if (isVerify) {
            log.info("====== PayOS DEBUG VERIFY SIGNATURE START ======");
            log.info("PayOS VERIFY data string (before hash) = {}", query.toString());
            log.info("PayOS VERIFY checksum key length = {}", key == null ? 0 : key.length());
            log.info("PayOS VERIFY signature (after HMAC SHA256, hex lowercase) = {}", hash.toString());
            log.info("====== PayOS DEBUG VERIFY SIGNATURE END ======");
        }
        if (debugOrderCode != null) {
            log.info("====== PayOS DEBUG SIGNATURE START ======");
            log.info("PayOS SIG input orderCode = {}", debugOrderCode);
            log.info("PayOS SIG input amount    = {}", debugAmount);
            log.info("PayOS SIG input description = {}", debugDescription);
            log.info("PayOS SIG checksum key (first 6) = {}", safePrefix(key));
            log.info("PayOS SIG data string (before hash) = {}", query.toString());
            log.info("PayOS SIG signature (after HMAC SHA256, hex lowercase) = {}", hash.toString());
            log.info("====== PayOS DEBUG SIGNATURE END ======");
        }
        return hash.toString();
    }

    private String safePrefix(String value) {
        if (value == null) {
            return "<null>";
        }
        int len = Math.min(6, value.length());
        return value.substring(0, len) + "...";
    }
}