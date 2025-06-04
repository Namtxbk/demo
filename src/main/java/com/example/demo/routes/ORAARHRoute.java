package com.example.demo.routes;


import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ORAARHRoute extends RouteBuilder {

    @Value("${endpoint.url}")
    private String endpoint;

    @Override
    public void configure() throws Exception {
//        from("quartz://myGroup/myTimerName?cron=0+0+0+*+*+?")
        from("timer://invoiceTimer?repeatCount=1")
                .setBody(constant(
                        "SELECT i.* " +
                                "FROM invoice i " +
                                "JOIN party_role pr ON i.from_party_id = pr.party_id " +
                                "WHERE pr.role_type_id = 'OrgInternal' " +
                                "LIMIT 1"
                )) //WHERE i.last_updated_stamp::date = CURRENT_DATE
                .to("jdbc:dataSource")
                .process(this::mapToOraarhFormat)
                .marshal().json()
                .log("Sent payload to ORAARH endpoint: ${body}");
//                .to(endpoint);
    }

    private void mapToOraarhFormat(Exchange exchange) {
        List<Map<String, Object>> invoiceList = exchange.getIn().getBody(List.class);
        List<Map<String, Object>> oraarhList = new ArrayList<>();

        for (Map<String, Object> invoice : invoiceList) {
            Map<String, Object> oraarh = new LinkedHashMap<>();

            // Cột ánh xạ từ Invoice sang ORAARH
            oraarh.put("ITFFIL", "RH" + new SimpleDateFormat("yyMMdd").format(new Date()) + ".0000001"); // Đổi prefix RH cho AR
            oraarh.put("ITFPFN", "ITFARH");                         // Fix cứng
            oraarh.put("ITFMBR", invoice.get("external_id"));       // Tạm thời lấy external_id
            oraarh.put("ITFINV", invoice.get("invoice_id"));        // Số hóa đơn
            oraarh.put("ITFCRD", invoice.get("invoice_type_enum_id")); // Loại hóa đơn
            oraarh.put("ITFDTE", formatDate(invoice.get("invoice_date"))); // Ngày hóa đơn (yyMMdd)
            oraarh.put("ITFVNO", invoice.get("from_party_id"));     // Mã khách hàng (AR)
            oraarh.put("ITFCMP", invoice.get("product_store_id"));  // Store hoặc GL code
            oraarh.put("ITFAMT", invoice.get("invoice_total"));     // Số tiền hóa đơn
            oraarh.put("ITFTRM", "6");                              // Term ID mặc định
            oraarh.put("ITFDSC", invoice.get("description"));       // Mô tả
            oraarh.put("ITFRDT", formatDate(invoice.get("due_date")));  // Ngày nhận hàng
            oraarh.put("ITFIDT", formatDate(invoice.get("paid_date"))); // Ngày so khớp
            oraarh.put("ITFGDT", formatDate(invoice.get("due_date")));  // GL Date
            oraarh.put("ITFREB", "N");                              // Mặc định
            oraarh.put("ITFBCH",null);                               // cai nay khong co trong data
            oraarh.put("ITFSTR", invoice.get("product_store_id"));  // Location
            oraarh.put("ITFCUR", invoice.get("currency_uom_id"));   // Loại tiền
            oraarh.put("ITFXRT", 1);                                // Tỷ giá cố định
            oraarh.put("ITFMDV", 0);                                // Multi-division flag

            oraarhList.add(oraarh);
        }

        exchange.getIn().setBody(oraarhList);
    }


    private String formatDate(Object dateObj) {
        if (dateObj == null) return "";
        if (dateObj instanceof Date) {
            return new SimpleDateFormat("yyMMdd").format((Date) dateObj);
        }
        return dateObj.toString();
    }
}
