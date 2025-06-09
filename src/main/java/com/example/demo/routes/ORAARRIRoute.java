package com.example.demo.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ORAARRIRoute extends RouteBuilder {

    @Value("${endpoint.url}")
    private String endpoint;

    @Override
    public void configure() throws Exception {
        from("quartz://myGroup/ORAARRI?cron=0+0+0+*+*+?")

//        from("timer://salesAuditTimer?repeatCount=1")
                .setBody(constant(
                        "SELECT " +
                                "  order_id, " +
                                "  grand_total, " +
                                "  entry_date, " +
                                "  product_store_id, " +
                                "  salesman_party_id " +
                                "FROM order_header oh "
                             +   " WHERE oh.last_updated_stamp::date = CURRENT_DATE"
                ))
                .to("jdbc:dataSource")
                .process(this::mapToOraarriFormat)
                .marshal().json()
                .log("Sent payload to ORAARRI endpoint: ${body}")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .toD(endpoint + "/oraarri");    }



    private void mapToOraarriFormat(Exchange exchange) {
        List<Map<String, Object>> rawList = exchange.getIn().getBody(List.class);
        List<Map<String, Object>> oraarriList = new ArrayList<>();

        for (Map<String, Object> row : rawList) {
            Map<String, Object> oraarri = new LinkedHashMap<>();

            oraarri.put("ITFFIL", "RR100250724.0000001");
            oraarri.put("ITFTRN", generateTransactionId(row.get("product_store_id"), row.get("order_date")));
            oraarri.put("ITFAMT", row.get("grand_total"));              // Amount
            oraarri.put("ITFDTE", formatDate(row.get("entry_date")));   // Transaction date
            oraarri.put("ITFTYP", "CA");                                 // Static type
            oraarri.put("ITFCUS", 0);                                    // Hardcoded customer ID
            oraarri.put("ITFMDT", formatDate(row.get("entry_date")));   // Due date
            oraarri.put("ITFSEQ", 1);                                    // Sequence
            oraarri.put("ITFGCO", 100);                                  // GL code
            oraarri.put("ITFDOC", row.get("RIGHT(order_id, 5)"));        // Document number
            oraarri.put("ITFSTR", row.get("product_store_id"));         // Store
            oraarri.put("ITFTIL", row.get("salesman_party_id"));        // TIL

            oraarriList.add(oraarri);
        }

        exchange.getIn().setBody(oraarriList);
    }

    private String formatDate(Object dateObj) {
        if (dateObj == null) return "";
        if (dateObj instanceof Date) {
            return new SimpleDateFormat("yyMMdd").format((Date) dateObj);
        }
        return dateObj.toString();
    }


    private String generateTransactionId(Object storeIdObj, Object dateObj) {
        String storeIdStr = "00000";
        if (storeIdObj != null) {
            String storeId = storeIdObj.toString();
            if (storeId.length() >= 5) {
                storeIdStr = storeId.substring(storeId.length() - 5);
            } else {
                storeIdStr = String.format("%5s", storeId).replace(' ', '0');
            }
        }

        String dateStr = "";
        if (dateObj instanceof Date) {
            dateStr = new SimpleDateFormat("yyyyMMdd").format((Date) dateObj);
        } else if (dateObj != null) {
            dateStr = dateObj.toString();
        }

        return "CA." + storeIdStr + "." + dateStr;
    }

}
