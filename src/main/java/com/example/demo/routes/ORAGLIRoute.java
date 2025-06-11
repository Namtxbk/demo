package com.example.demo.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
@Component
public class ORAGLIRoute extends RouteBuilder {


    @Value("${endpoint.url}")
    private String endpoint;

    @Override
    public void configure() throws Exception {
             from("quartz://myGroup/ORAGLI?cron=0+0+0+*+*+?")
      //  from("timer://ORAGLIRoute?repeatCount=1")
                .setBody(constant(
                        "SELECT " +
                                "    ate.acctg_trans_id as ITFBCH, " +
                                "    at.description AS ITFDSC, " +
                                "    at.acctg_trans_id AS  ITFJRN , " +
                                "    ate.description AS  ITFJRD , " +
                                "    at.transaction_date AS  ITFACD , " +
                                "    at.acctg_trans_type_enum_id AS  ITFCTN , " +
                                "    ga.gl_account_id AS  ITFGLM , " +
                                "    ate.amount AS  ITFAMT , " +
                                "    ate.acctg_trans_entry_seq_id AS  ITFJEN , " +
                                "    ate.description AS  ITFJLD  " +
                                "FROM  " +
                                "    acctg_trans at " +
                                "    INNER JOIN acctg_trans_entry ate ON at.acctg_trans_id = ate.acctg_trans_id " +
                                "    LEFT JOIN gl_journal gj ON at.gl_journal_id = gj.gl_journal_id " +
                                "    LEFT JOIN gl_account ga ON ate.gl_account_id = ga.gl_account_id " +
                                " at.transaction_date := ::date = CURRENT_DATE "

                ))
                .to("jdbc:dataSource")
                .log(" ORAGLI SQL tra ve: ${body}")
                .process(this::mapToOragliFormat)
                .marshal().json()
                .log("Sent payload to ORAGLIRoute endpoint: ${body}")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .toD(endpoint + "/oragli")
                .log(" ORAGLI: Server trả về: ${body}");

    }


    private void mapToOragliFormat(Exchange exchange) {
        List<Map<String, Object>> rawList = exchange.getIn().getBody(List.class);
        List<Map<String, Object>> oragliList = new ArrayList<>();

        for (Map<String, Object> row : rawList) {
            Map<String, Object> oragli = new LinkedHashMap<>();

            oragli.put("ITFFIL", "GL100240724.0000001");   // MMS File
            oragli.put("ITFBCH", row.get("itfbch"));       // Batch name
            oragli.put("ITFDSC", row.get("itfdsc"));       // Batch description
            oragli.put("ITFJRN", row.get("itfjrn"));       // Journal name
            oragli.put("ITFJRD", row.get("itfjrd"));       // Journal description
            oragli.put("ITFACD", formatDate(row.get("itfacd"))); // Accounting date
            oragli.put("ITFCTN", row.get("itfctn"));       // Category name
            oragli.put("ITFGLC", "");                      // Segment: Company
            oragli.put("ITFGLM", "");                      // Segment: Account
            oragli.put("ITFDEP", "");                      // Segment: Department
            oragli.put("ITFSTR", "");                      // Segment: Store
            oragli.put("ITFAMT", row.get("itfamt"));       // Amount
            oragli.put("ITFJEN", row.get("itfjen"));       // Journal Entry Line
            oragli.put("ITFJLD", row.get("itfjld"));       // Journal Line Description
            oragli.put("ITFREF", "");       // Reference Number


            oragliList.add(oragli);
        }

        exchange.getIn().setBody(oragliList);
    }


    private String formatDate(Object dateObj) {
        if (dateObj == null) return "";
        if (dateObj instanceof Date) {
            return new SimpleDateFormat("yyMMdd").format((Date) dateObj);
        }
        return dateObj.toString();
    }


    
}
