package com.example.demo.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ORAAPHRoute extends RouteBuilder {

    @Value("${endpoint.url}")
    private String endpoint;

    @Override
    public void configure() throws Exception {
        from("quartz://myGroup/ORAAPH?cron=0+0+0+*+*+?")
      // from("timer://invoiceTimer?repeatCount=1")
                .setBody(constant(
                        "SELECT i.* " +
                                "FROM invoice i " +
                                "JOIN party_role pr ON i.to_party_id = pr.party_id " +
                                "WHERE pr.role_type_id = 'OrgInternal' " +
                               " AND i.last_updated_stamp::date = CURRENT_DATE"

                )) //
                .to("jdbc:dataSource")
                .process(this::mapToOraaphFormat)
                .marshal().json()
                .log("Sent payload to ORAAPH endpoint: ${body}")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .toD(endpoint + "/oraaph");
    }

    private void mapToOraaphFormat(Exchange exchange) {
        List<Map<String, Object>> invoiceList = exchange.getIn().getBody(List.class);
        List<Map<String, Object>> oraaphList = new ArrayList<>();

        for (Map<String, Object> invoice : invoiceList) {
            Map<String, Object> oraaph = new LinkedHashMap<>();

            // Cột ánh xạ từ Invoice sang ORAAPH
            oraaph.put("ITFFIL", "PH" + new SimpleDateFormat("yyMMdd").format(new Date()) + ".0000001");
            oraaph.put("ITFPFN", "ITFAPH");                              // Fix cứng
            oraaph.put("ITFMBR", invoice.get("external_id"));           // Không rõ, tạm lấy external_id
            oraaph.put("ITFINV", invoice.get("invoice_id"));            // Số hóa đơn
            oraaph.put("ITFCRD", invoice.get("invoice_type_enum_id")); // Loại hóa đơn
            oraaph.put("ITFDTE", formatDate(invoice.get("invoice_date"))); // Ngày hóa đơn (yyMMdd)
            oraaph.put("ITFVNO", invoice.get("from_party_id"));         // Mã nhà cung cấp
            oraaph.put("ITFCMP", invoice.get("product_store_id"));      // Store hoặc GL code
            oraaph.put("ITFAMT", invoice.get("invoice_total"));         // Số tiền hóa đơn
            oraaph.put("ITFTRM", "6");                                   // Term ID (mặc định)
            oraaph.put("ITFDSC", invoice.get("description"));           // Mô tả
            oraaph.put("ITFRDT", formatDate(invoice.get("due_date")));  // Ngày nhận hàng
            oraaph.put("ITFIDT", formatDate(invoice.get("paid_date"))); // Ngày so khớp
            oraaph.put("ITFGDT", formatDate(invoice.get("due_date")));  // GL Date
            oraaph.put("ITFREB", "N");                                   // Mặc định
            oraaph.put("ITFBCH",null);     // cai nay khong co trong data
            oraaph.put("ITFSTR", invoice.get("product_store_id"));      // Location
            oraaph.put("ITFCUR", invoice.get("currency_uom_id"));       // Loại tiền
            oraaph.put("ITFXRT", 1);                                     // Tỷ giá cố định
            oraaph.put("ITFMDV", 0);                                     // Multi-division flag

            oraaphList.add(oraaph);
        }

        exchange.getIn().setBody(oraaphList);
    }


    private String formatDate(Object dateObj) {
        if (dateObj == null) return "";
        if (dateObj instanceof Date) {
            return new SimpleDateFormat("yyMMdd").format((Date) dateObj);
        }
        return dateObj.toString();
    }
}
