package com.example.demo.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ORAAPDRoute extends RouteBuilder {
    @Value("${endpoint.url}")
    private String endpoint;

    @Override
    public void configure() throws Exception {

                //        from("timer://invoiceItemTimer?repeatCount=1")
                from("quartz://myGroup/ORAAPD?cron=0+0+0+*+*+?")
                .setBody(constant(
                        "SELECT ii.*, i.invoice_date, i.from_party_id, i.to_party_id, i.product_store_id, i.currency_uom_id " +
                                "FROM invoice_item ii " +
                                "JOIN invoice i ON ii.invoice_id = i.invoice_id " +
                                "JOIN party_role pr ON i.to_party_id = pr.party_id " +
                                "WHERE pr.role_type_id = 'OrgInternal' "+
                                "AND i.last_updated_stamp::date = CURRENT_DATE"

                ))
                .to("jdbc:dataSource")
                .process(this::mapToOraapdFormat)
                .marshal().json()
                .log("Sent payload to ORAAPD endpoint: ${body}")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .toD(endpoint + "/oraapd");
    }

    private void mapToOraapdFormat(Exchange exchange) {
        List<Map<String, Object>> itemList = exchange.getIn().getBody(List.class);
        List<Map<String, Object>> oraapdList = new ArrayList<>();

        for (Map<String, Object> item : itemList) {
            Map<String, Object> oraapd = new LinkedHashMap<>();

            oraapd.put("ITFFIL", "PH" + new SimpleDateFormat("yyMMdd").format(new Date()) + ".0000001");
            oraapd.put("ITFPFN", "ITFAPD");                              // Fix cứng
            oraapd.put("ITFMBR", item.get("external_id"));              // Chua ro tam lay external_id
            oraapd.put("ITFINV", item.get("invoice_id"));               // Số hóa đơn
            oraapd.put("ITFVNO", item.get("from_party_id"));            // Nhà cung cấp
            oraapd.put("ITFSEQ", Integer.parseInt(((String)item.get("invoice_item_seq_id")).replace("0000", ""))); // sequence
            oraapd.put("ITFDMT", item.get("amount"));                   // Số tiền
            oraapd.put("ITFADT", formatDate(item.get("invoice_date"))); // Ngày hóa đơn
            oraapd.put("ITFDST", null);            // Dist Code
            oraapd.put("ITFRCR", null);                                 // Receiving number
            oraapd.put("ITFPOB", null);                                 // Mã PO
            oraapd.put("ITFBCH", null);                                 // Batch, chưa có
            oraapd.put("ITFSTR", item.get("product_store_id"));         // Location
            oraapd.put("ITFCCD", null);                                 // Tax Code nếu cần
            oraapd.put("ITFCSQ", 0);                                    // Reciprocal seq id (nếu có xử lý đối ứng)

            oraapdList.add(oraapd);
        }

        exchange.getIn().setBody(oraapdList);
    }


    private String formatDate(Object dateObj) {
        if (dateObj == null) return "";
        if (dateObj instanceof Date) {
            return new SimpleDateFormat("yyMMdd").format((Date) dateObj);
        }
        return dateObj.toString();
    }
}
