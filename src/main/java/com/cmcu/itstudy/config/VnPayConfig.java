package com.cmcu.itstudy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.Calendar;

@Configuration
public class VnPayConfig {

    @Value("${vnp.tmnCode}")
    private String tmnCode;

    @Value("${vnp.hashSecret}")
    private String hashSecret;

    @Value("${vnp.payUrl}")
    private String payUrl;

    @Value("${vnp.returnUrl}")
    private String returnUrl;

    @Value("${vnp.ipnUrl}")
    private String ipnUrl;

    public String getTmnCode() {
        return tmnCode;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getIpnUrl() {
        return ipnUrl;
    }

    public String generateOrderCode() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+7"));

        String timestamp = sdf.format(new Date());
        int random = (int) (Math.random() * 9000) + 1000;

        return "ORDER" + timestamp + random;
    }

    public String buildPaymentUrl(Long amount,
        String orderCode,
        String ipAddress) {
                
long vnpAmount = amount * 100L;

if ("0:0:0:0:0:0:0:1".equals(ipAddress)
|| "::1".equals(ipAddress)) {
ipAddress = "127.0.0.1";
}

Calendar calendar =
Calendar.getInstance(
TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

SimpleDateFormat formatter =
new SimpleDateFormat("yyyyMMddHHmmss");

formatter.setTimeZone(
TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

String createDate =
formatter.format(calendar.getTime());

calendar.add(Calendar.MINUTE, 15);

String expireDate =
formatter.format(calendar.getTime());

Map<String, String> params =
new TreeMap<>();

params.put("vnp_Version", "2.1.1");
params.put("vnp_Command", "pay");
params.put("vnp_TmnCode", tmnCode);
params.put("vnp_Amount", String.valueOf(vnpAmount));
params.put("vnp_CurrCode", "VND");
params.put("vnp_TxnRef", orderCode);
params.put("vnp_OrderInfo", "Thanh toan tai lieu " + orderCode);
params.put("vnp_OrderType", "other");
params.put("vnp_Locale", "vn");
params.put("vnp_ReturnUrl", returnUrl);
params.put("vnp_IpAddr", ipAddress);
params.put("vnp_CreateDate", createDate);
params.put("vnp_ExpireDate", expireDate);
params.put("vnp_BankCode", "NCB");
String secureHash = hashSign(params);

StringBuilder query = new StringBuilder();

for (Map.Entry<String, String> entry : params.entrySet()) {

if (query.length() > 0) {
query.append("&");
}

query.append(entry.getKey())
.append("=")
.append(
  URLEncoder.encode(
          entry.getValue(),
          StandardCharsets.UTF_8
  )
);
}


        query.append("&vnp_SecureHash=");
        query.append(secureHash);

String paymentUrl =
payUrl + "?" + query;

System.out.println("================================");
System.out.println("VNPay Debug");
System.out.println("TMNCODE      = " + tmnCode);
System.out.println("HASH_SECRET  = " + hashSecret);
System.out.println("ORDER_CODE   = " + orderCode);
System.out.println("IP_ADDRESS   = " + ipAddress);
System.out.println("CREATE_DATE  = " + createDate);
System.out.println("EXPIRE_DATE  = " + expireDate);
System.out.println("HASH_DATA    = " + params);
System.out.println("SECURE_HASH  = " + secureHash);
System.out.println("PAYMENT_URL  = " + paymentUrl);
System.out.println("================================");

return paymentUrl;
}

    public String hashSign(Map<String, String> params) {

        TreeMap<String, String> sortedParams =
                new TreeMap<>(params);

        StringBuilder hashData =
                new StringBuilder();

        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {

            if (hashData.length() > 0) {
                hashData.append("&");
            }

            hashData.append(
                URLEncoder.encode(
                        entry.getKey(),
                        StandardCharsets.US_ASCII
                )
        );
        
        hashData.append("=");
        
        hashData.append(
                URLEncoder.encode(
                        entry.getValue(),
                        StandardCharsets.US_ASCII
                )
        );
        }
        System.out.println("HASH_STRING=" + hashData);

        return hmacSHA512(
                hashSecret,
                hashData.toString()
        );
        
    }

    public boolean validateReturnChecksum(
            Map<String, String> params) {

        String receivedHash =
                params.get("vnp_SecureHash");

        if (receivedHash == null ||
                receivedHash.isBlank()) {
            return false;
        }

        TreeMap<String, String> sortedParams =
                new TreeMap<>(params);

        sortedParams.remove("vnp_SecureHash");
        sortedParams.remove("vnp_SecureHashType");

        String calculatedHash =
                hashSign(sortedParams);

        return receivedHash.equalsIgnoreCase(
                calculatedHash
        );
    }

    public boolean validateIpnChecksum(
            Map<String, String> params) {

        return validateReturnChecksum(params);
    }

    private String hmacSHA512(
            String key,
            String data) {

        try {

            Mac mac =
                    Mac.getInstance("HmacSHA512");

            SecretKeySpec secretKeySpec =
                    new SecretKeySpec(
                            key.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA512"
                    );

            mac.init(secretKeySpec);

            byte[] bytes =
        mac.doFinal(
                data.getBytes(StandardCharsets.UTF_8)
        );

            StringBuilder hash =
                    new StringBuilder();

                    for (byte b : bytes) {

                        String hex =
                                Integer.toHexString(0xff & b);
                    
                        if (hex.length() == 1) {
                            hash.append('0');
                        }
                    
                        hash.append(hex);
                    }

            return hash.toString().toUpperCase();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot generate VNPay checksum",
                    e
            );
        }
    }
}